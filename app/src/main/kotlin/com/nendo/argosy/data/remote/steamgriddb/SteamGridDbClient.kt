package com.nendo.argosy.data.remote.steamgriddb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class SteamGridDbGrid(
    val url: String,
    val thumb: String?,
    val width: Int?,
    val height: Int?
)

data class SteamGridDbSearch(
    val matchedName: String?,
    val grids: List<SteamGridDbGrid>
) {
    companion object {
        val EMPTY = SteamGridDbSearch(null, emptyList())
    }
}

/** SteamGridDB's art kinds and their API paths. */
enum class SteamGridDbAsset(val path: String) {
    GRID("grids"),
    HERO("heroes"),
    LOGO("logos"),
    ICON("icons")
}

class SteamGridDbRateLimited(val retryAfterMillis: Long) :
    IllegalStateException("SteamGridDB rate limited for ${retryAfterMillis}ms")

class SteamGridDbRequestFailed(val code: Int) :
    IllegalStateException("SteamGridDB HTTP $code")

/**
 * SteamGridDB's v2 API: a title either resolves to a game id or does not, and that game's static
 * grids are the covers. Keyed by the user's own API key.
 */
@Singleton
class SteamGridDbClient @Inject constructor() {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    private val paceMutex = Mutex()
    private var lastRequestAtMillis = 0L

    suspend fun searchGrids(term: String, apiKey: String): Result<SteamGridDbSearch> =
        search(term, apiKey, SteamGridDbAsset.GRID)

    /**
     * Square grids only (512x512 and 1024x1024), asked for by size: an unfiltered grid list can
     * fill its first page with tall covers and leave the square ones out.
     */
    suspend fun searchSquareGrids(term: String, apiKey: String): Result<SteamGridDbSearch> =
        search(term, apiKey, SteamGridDbAsset.GRID, dimensions = SQUARE_DIMENSIONS)

    /** Any kind of art for [term]: grids (covers), heroes (backgrounds), logos or icons. */
    suspend fun search(
        term: String,
        apiKey: String,
        asset: SteamGridDbAsset,
        dimensions: String? = null
    ): Result<SteamGridDbSearch> =
        withContext(Dispatchers.IO) {
            runCatching {
                val trimmed = term.trim()
                if (trimmed.isEmpty() || apiKey.isBlank()) return@runCatching SteamGridDbSearch.EMPTY
                val match = autocomplete(trimmed, apiKey) ?: return@runCatching SteamGridDbSearch.EMPTY
                val sizes = dimensions?.let { "&dimensions=$it" }.orEmpty()
                val grids = parseGrids(
                    get("$BASE_URL/${asset.path}/game/${match.id}?types=static&nsfw=false&humor=false$sizes", apiKey)
                )
                SteamGridDbSearch(match.name, grids)
            }
        }

    private data class AutocompleteMatch(val id: Long, val name: String?)

    private suspend fun autocomplete(term: String, apiKey: String): AutocompleteMatch? {
        val body = get("$BASE_URL/search/autocomplete/${term.replace(" ", "%20")}", apiKey)
        val data = JSONObject(body).optJSONArray("data") ?: return null
        if (data.length() == 0) return null
        val first = data.optJSONObject(0) ?: return null
        val id = first.optLong("id").takeIf { it > 0 } ?: return null
        return AutocompleteMatch(id, first.optString("name").takeIf { it.isNotBlank() })
    }

    private fun parseGrids(body: String): List<SteamGridDbGrid> {
        val data = JSONObject(body).optJSONArray("data") ?: return emptyList()
        return buildList {
            for (index in 0 until data.length()) {
                val grid = data.optJSONObject(index) ?: continue
                val url = grid.optString("url").takeIf { it.isNotBlank() } ?: continue
                add(
                    SteamGridDbGrid(
                        url = url,
                        thumb = grid.optString("thumb").takeIf { it.isNotBlank() },
                        width = grid.optInt("width").takeIf { it > 0 },
                        height = grid.optInt("height").takeIf { it > 0 }
                    )
                )
            }
        }
    }

    private suspend fun get(url: String, apiKey: String): String {
        pace()
        val request = Request.Builder()
            .url(url)
            .header(AUTHORIZATION, "Bearer $apiKey")
            .header(ACCEPT, "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == HTTP_TOO_MANY_REQUESTS) {
                throw SteamGridDbRateLimited(retryAfterMillis(response))
            }
            if (!response.isSuccessful) throw SteamGridDbRequestFailed(response.code)
            return response.body?.string().orEmpty()
        }
    }

    private suspend fun pace() {
        paceMutex.withLock {
            val sinceLast = System.currentTimeMillis() - lastRequestAtMillis
            val wait = MIN_REQUEST_INTERVAL_MS - sinceLast
            if (wait > 0) delay(wait)
            lastRequestAtMillis = System.currentTimeMillis()
        }
    }

    private fun retryAfterMillis(response: Response): Long {
        val seconds = response.header(RETRY_AFTER)?.trim()?.toLongOrNull()
        if (seconds != null && seconds > 0) return seconds * 1000L
        val resetAtMillis = response.header(RATE_LIMIT_RESET)?.trim()?.toLongOrNull()?.times(1000L)
        if (resetAtMillis != null) {
            return (resetAtMillis - System.currentTimeMillis()).coerceAtLeast(MIN_PARK_MS)
        }
        return DEFAULT_PARK_MS
    }

    private companion object {
        const val BASE_URL = "https://www.steamgriddb.com/api/v2"
        const val SQUARE_DIMENSIONS = "512x512,1024x1024"
        const val AUTHORIZATION = "Authorization"
        const val ACCEPT = "Accept"
        const val RETRY_AFTER = "Retry-After"
        const val RATE_LIMIT_RESET = "x-ratelimit-reset"
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val CONNECT_TIMEOUT_SECONDS = 10L
        const val READ_TIMEOUT_SECONDS = 20L
        const val MIN_REQUEST_INTERVAL_MS = 700L
        const val MIN_PARK_MS = 5_000L
        const val DEFAULT_PARK_MS = 60_000L
    }
}
