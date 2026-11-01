package com.nendo.argosy.data.remote.github

import com.nendo.argosy.util.Logger
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GitHubReleaseClient"
private const val API_BASE = "https://api.github.com/"
private const val CACHE_TTL_SECONDS = 300L

sealed class ReleaseLookup {
    data class Found(val release: GitHubRelease) : ReleaseLookup()
    data object NotFound : ReleaseLookup()
    data class RateLimited(val resetAt: Instant?) : ReleaseLookup()
    data class Failed(val code: Int?) : ReleaseLookup()
}

data class GitHubBudget(
    val remaining: Int?,
    val limit: Int?,
    val resetAt: Instant?
)

/**
 * The one place Argosy asks GitHub for a release.
 *
 * Unauthenticated callers share a single 60-per-hour allowance keyed on the public IP, and a
 * conditional request that answers 304 still spends from it, so an ETag buys nothing here. Holding
 * every caller behind one object is what makes the remaining allowance knowable at all: separate
 * clients each see their own responses and none of them can tell how much is left.
 */
@Singleton
class GitHubReleaseClient @Inject constructor() {

    private val api: GitHubApi = Retrofit.Builder()
        .baseUrl(API_BASE)
        .client(
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        )
        .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
        .build()
        .create(GitHubApi::class.java)

    private val mutex = Mutex()
    private val cache = mutableMapOf<String, CachedRelease>()

    @Volatile
    private var budget = GitHubBudget(remaining = null, limit = null, resetAt = null)

    fun budget(): GitHubBudget = budget

    suspend fun latestRelease(owner: String, repo: String): ReleaseLookup =
        withContext(Dispatchers.IO) {
            val key = "$owner/$repo"

            mutex.withLock { cache[key] }
                ?.takeIf { it.isFresh() }
                ?.let { return@withContext ReleaseLookup.Found(it.release) }

            exhaustedUntil()?.let { return@withContext ReleaseLookup.RateLimited(it) }

            val response = try {
                api.getRepoLatestRelease(owner, repo)
            } catch (e: Exception) {
                Logger.warn(TAG, "Release lookup threw for $key: ${e.message}")
                return@withContext ReleaseLookup.Failed(null)
            }

            recordBudget(response)

            when {
                response.isSuccessful -> {
                    val release = response.body()
                        ?: return@withContext ReleaseLookup.Failed(response.code())
                    mutex.withLock { cache[key] = CachedRelease(release, Instant.now()) }
                    ReleaseLookup.Found(release)
                }
                response.code() == 404 -> ReleaseLookup.NotFound
                isRateLimited(response) -> ReleaseLookup.RateLimited(budget.resetAt)
                else -> ReleaseLookup.Failed(response.code())
            }
        }

    suspend fun invalidate(owner: String, repo: String) {
        mutex.withLock { cache.remove("$owner/$repo") }
    }

    private fun exhaustedUntil(): Instant? {
        val remaining = budget.remaining ?: return null
        if (remaining > 0) return null
        val resetAt = budget.resetAt ?: return null
        return resetAt.takeIf { it.isAfter(Instant.now()) }
    }

    private fun isRateLimited(response: Response<*>): Boolean {
        if (response.code() == 429) return true
        return response.code() == 403 && budget.remaining == 0
    }

    private fun recordBudget(response: Response<*>) {
        val headers = response.headers()
        val remaining = headers["x-ratelimit-remaining"]?.toIntOrNull()
        val limit = headers["x-ratelimit-limit"]?.toIntOrNull()
        val resetAt = headers["x-ratelimit-reset"]?.toLongOrNull()?.let(Instant::ofEpochSecond)
        if (remaining == null && limit == null && resetAt == null) return
        budget = GitHubBudget(remaining = remaining, limit = limit, resetAt = resetAt)
        if (remaining != null && remaining <= 5) {
            Logger.warn(TAG, "GitHub allowance low: $remaining left, resets at $resetAt")
        }
    }

    private data class CachedRelease(val release: GitHubRelease, val fetchedAt: Instant) {
        fun isFresh(): Boolean =
            fetchedAt.plusSeconds(CACHE_TTL_SECONDS).isAfter(Instant.now())
    }
}
