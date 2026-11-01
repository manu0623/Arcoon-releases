package com.nendo.argosy.data.launcher

import android.content.Context
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.cache.recordArtSource
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameScreenshotDao
import com.nendo.argosy.data.local.dao.forGames
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.model.ArtSlot
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.platform.LocalPlatformIds
import com.nendo.argosy.data.preferences.StoragePreferencesRepository
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.AppsRepository
import com.nendo.argosy.data.remote.steam.SteamStoreApi
import com.nendo.argosy.data.remote.steam.SteamStoreSearchApi
import com.nendo.argosy.util.Logger
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The storefronts whose markers create and delete library rows, and whose slugs drive launch
 * routing through `forSlug`. Steam is deliberately absent: its markers only flip install state on
 * rows that already exist, so adding it here would change how games launch.
 */
enum class GameNativeStore(
    val platformId: Long,
    val folder: GameNativeSyncFolder,
    val launchSource: String
) {
    GOG(LocalPlatformIds.GOG, GameNativeSyncFolder.GOG, "GOG"),
    EPIC(LocalPlatformIds.EPIC, GameNativeSyncFolder.EPIC, "EPIC"),
    AMAZON(LocalPlatformIds.AMAZON, GameNativeSyncFolder.AMAZON, "AMAZON");

    val slug: String get() = folder.slug
    val displayName: String get() = folder.displayName

    companion object {
        fun forSlug(slug: String): GameNativeStore? = entries.find { it.slug == slug }
        fun forFolder(folder: GameNativeSyncFolder): GameNativeStore? = entries.find { it.folder == folder }
    }
}

/**
 * Per-folder outcome of one scan pass. Only folders the user configured get an entry, so an
 * absent key means "not set up" rather than "scanned and found nothing".
 */
sealed interface StoreScanResult {
    val markers: Int

    data class FolderMissing(val path: String) : StoreScanResult {
        override val markers: Int get() = 0
    }

    data class Library(
        override val markers: Int,
        val added: Int,
        val removed: Int
    ) : StoreScanResult

    data class InstallState(
        override val markers: Int,
        val matched: Int,
        val notInLibrary: Int
    ) : StoreScanResult
}

/**
 * Reads the Frontend Sync folders of GameNative and DroidDeck. GOG, Epic and Amazon markers own
 * their library rows; Steam markers only set install state for their launcher on existing Steam
 * rows, leaving hand-pinned rows and rows another launcher already claimed untouched.
 */
@Singleton
class GameNativeStoreSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appsRepository: AppsRepository,
    private val gameDao: GameDao,
    private val platformDao: PlatformDao,
    private val imageCacheManager: ImageCacheManager,
    private val storagePrefs: StoragePreferencesRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val syncPreferencesRepository: com.nendo.argosy.data.preferences.SyncPreferencesRepository,
    private val steamLibraryRepair: com.nendo.argosy.data.steam.SteamLibraryRepair,
    private val gameArtDao: com.nendo.argosy.data.local.dao.GameArtDao,
    private val gameScreenshotDao: GameScreenshotDao
) {
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val steamRetrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://store.steampowered.com/")
            .client(httpClient)
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
            .build()
    }

    private val steamSearchApi: SteamStoreSearchApi by lazy { steamRetrofit.create(SteamStoreSearchApi::class.java) }
    private val steamStoreApi: SteamStoreApi by lazy { steamRetrofit.create(SteamStoreApi::class.java) }

    suspend fun scan(): ScanSummary = withContext(Dispatchers.IO) {
        storagePrefs.migrateLegacyGameNativeSyncDir()
        steamLibraryRepair.restoreSources()
        val dirs = storagePrefs.preferences.first().gameNativeSyncDirs
        val cacheScreenshots = preferencesRepository.userPreferences.first().syncScreenshotsEnabled
        val ownerUserId = syncPreferencesRepository.getRommUserId()
        val results = linkedMapOf<GameNativeSyncFolder, StoreScanResult>()
        val steamReports = linkedMapOf<GameNativeSyncFolder, List<Pair<Int, String>>>()

        for (folder in GameNativeSyncFolder.entries) {
            val path = dirs[folder] ?: continue
            val dir = File(path)
            if (!dir.exists() || !dir.isDirectory) {
                Logger.warn(TAG, "scan: sync dir missing | store=${folder.slug}, path=$path")
                results[folder] = StoreScanResult.FolderMissing(path)
                continue
            }
            val markers = FrontendSyncMarkers.read(dir, folder)
            if (markers == null) {
                Logger.warn(TAG, "scan: sync dir unreadable | store=${folder.slug}, path=$path")
                results[folder] = StoreScanResult.FolderMissing(path)
                continue
            }
            val store = GameNativeStore.forFolder(folder)
            if (store == null) {
                steamReports[folder] = markers
            } else {
                results[folder] = reconcileStore(store, markers, cacheScreenshots, ownerUserId)
            }
        }
        results.putAll(reconcileSteamInstallState(steamReports, ownerUserId))
        results.forEach { (folder, result) -> Logger.info(TAG, "scan: ${folder.slug} | $result") }
        ScanSummary(results)
    }

    suspend fun refreshSteamInstallations() = withContext(Dispatchers.IO) {
        val dirs = storagePrefs.preferences.first().gameNativeSyncDirs
        val reports = GameNativeSyncFolder.STEAM_INSTALL_MARKERS.mapNotNull { folder ->
            val dir = dirs[folder]?.let(::File)?.takeIf { it.isDirectory } ?: return@mapNotNull null
            FrontendSyncMarkers.read(dir, folder)?.let { folder to it }
        }.toMap()
        reconcileSteamInstallState(reports, syncPreferencesRepository.getRommUserId())
        Unit
    }

    private suspend fun reconcileSteamInstallState(
        reports: Map<GameNativeSyncFolder, List<Pair<Int, String>>>,
        ownerUserId: Long?
    ): Map<GameNativeSyncFolder, StoreScanResult.InstallState> {
        val steamGames = gameDao.getBySourcesForOwner(
            sources = listOf(GameSource.STEAM),
            platformId = LocalPlatformIds.STEAM,
            ownerUserId = ownerUserId
        )
        val byAppId = steamGames.mapNotNull { game -> game.steamAppId?.let { it to game } }.toMap()
        val markerIds = reports.mapValues { (_, markers) -> markers.map { it.first.toLong() }.toSet() }
        val results = reports.mapValues { (folder, markers) ->
            val ids = markerIds.getValue(folder)
            StoreScanResult.InstallState(markers.size, ids.count { it in byAppId }, ids.count { it !in byAppId })
        }
        val installed = SteamLaunchers.all.filter { runCatching { it.isInstalled(context) }.getOrDefault(false) }
            .map { it.packageName }.toSet()
        val missingPackages = steamGames.mapNotNull { it.steamLauncher }
            .filter { it != GameEntity.LAUNCHER_UNSPECIFIED && it !in installed }.distinct()
            .filter(appsRepository::isPackageMissing).toSet()
        gameLoop@ for (initial in steamGames) {
            var game = initial
            for (attempt in 0..1) {
                if (syncPreferencesRepository.getRommUserId() != ownerUserId) return results
                val stored = game.steamLauncher?.takeIf { it != GameEntity.LAUNCHER_UNSPECIFIED }
                if (stored != null && appsRepository.isPackageUpdating(stored)) continue@gameLoop
                val missing = stored in missingPackages
                if (missing && game.launcherSetManually) {
                    if (gameDao.compareAndSetSteamLauncher(game.id, stored, true, stored, false, ownerUserId) == 0) {
                        game = gameDao.getById(game.id) ?: continue@gameLoop
                        continue
                    }
                    game = game.copy(launcherSetManually = false)
                }
                if (game.launcherSetManually) continue@gameLoop
                val appId = game.steamAppId ?: continue@gameLoop
                val reporting = markerIds.filter { (folder, ids) -> folder.launcherPackage in installed && appId in ids }
                    .keys.map { it.launcherPackage }
                val ownInventory = markerIds.entries.firstOrNull { it.key.launcherPackage == stored }?.value
                val replacement = when {
                    missing -> reporting.singleOrNull() ?: game.steamLauncher
                    stored == null -> reporting.firstOrNull() ?: game.steamLauncher
                    ownInventory != null && appId !in ownInventory -> reporting.firstOrNull()
                    else -> game.steamLauncher
                }
                if (replacement == game.steamLauncher) continue@gameLoop
                if (gameDao.compareAndSetSteamLauncher(game.id, game.steamLauncher, false, replacement, false, ownerUserId) != 0) {
                    continue@gameLoop
                }
                game = gameDao.getById(game.id) ?: continue@gameLoop
            }
        }
        return results
    }

    private suspend fun reconcileStore(
        store: GameNativeStore,
        markers: List<Pair<Int, String>>,
        cacheScreenshots: Boolean,
        ownerUserId: Long?
    ): StoreScanResult.Library {
        val existing = gameDao.getBySource(GameSource.GAMENATIVE)
            .filter { it.platformId == store.platformId }
        val markerIds = markers.map { it.first.toLong() }.toSet()

        var removed = 0
        val stale = if (markerIds.isEmpty() && existing.isNotEmpty()) {
            Logger.warn(TAG, "reconcile: ${store.slug} folder lists no markers; keeping ${existing.size} games")
            emptyList()
        } else {
            existing.filter { it.steamAppId !in markerIds }
        }
        stale.forEach { game ->
            Logger.debug(TAG, "reconcile: removing uninstalled | store=${store.slug}, title=${game.title}")
            gameDao.delete(game.id)
            removed++
        }

        val known = existing.mapNotNull { it.steamAppId }.toSet()
        val fresh = markers.filter { it.first.toLong() !in known }
        if (fresh.isNotEmpty()) ensurePlatformExists(store)

        var added = 0
        for ((id, name) in fresh) {
            val meta = fetchMetadata(store, id, name)
            val game = GameEntity(
                platformId = store.platformId,
                platformSlug = store.slug,
                title = meta.title,
                sortTitle = createSortTitle(meta.title),
                localPath = null,
                rommId = null,
                igdbId = null,
                steamAppId = id.toLong(),
                steamLauncher = GAMENATIVE_LAUNCHER,
                source = GameSource.GAMENATIVE,
                developer = meta.developer,
                releaseYear = meta.releaseYear,
                genre = meta.genre,
                description = meta.description,
                addedAt = Instant.now()
            )
            val insertedId = gameDao.insert(game)
            writeArtSource(insertedId, ArtSlot.COVER, meta.coverUrl, meta.title)
            writeArtSource(insertedId, ArtSlot.BACKGROUND, meta.backgroundUrl, meta.title)
            recordScreenshots(insertedId, meta.screenshotUrls, cacheScreenshots)
            Logger.debug(TAG, "reconcile: added | store=${store.slug}, title=${meta.title}, id=$id")
            added++
        }
        val detailless = existing.filter { it.steamAppId in markerIds && it.description == null }
        val withScreenshots = gameScreenshotDao.forGames(detailless.map { it.id }).keys
        detailless
            .filter { it.id !in withScreenshots }
            .forEach { game ->
                val id = game.steamAppId?.toInt() ?: return@forEach
                val meta = fetchMetadata(store, id, game.title)
                if (meta.description == null && meta.screenshotUrls.isEmpty()) return@forEach
                gameDao.update(
                    game.copy(
                        description = meta.description ?: game.description,
                        genre = meta.genre ?: game.genre
                    )
                )
                recordScreenshots(game.id, meta.screenshotUrls, cacheScreenshots)
                Logger.debug(TAG, "reconcile: backfilled details | store=${store.slug}, title=${game.title}")
            }

        if (platformDao.getById(store.platformId) != null) {
            platformDao.updateGameCount(
                store.platformId,
                gameDao.countByPlatform(store.platformId, ownerUserId)
            )
        }
        return StoreScanResult.Library(markers = markers.size, added = added, removed = removed)
    }

    private suspend fun writeArtSource(gameId: Long, slot: ArtSlot, url: String?, title: String) {
        url ?: return
        recordArtSource(gameArtDao, imageCacheManager, gameId, slot, listOf(url), title)
    }

    private suspend fun recordScreenshots(gameId: Long, urls: List<String>, cache: Boolean) {
        val rows = gameScreenshotDao.replaceSources(gameId, urls)
        if (cache && rows.isNotEmpty()) imageCacheManager.queueScreenshotCacheByGameId(gameId)
    }

    private suspend fun fetchMetadata(store: GameNativeStore, id: Int, markerName: String): StoreGameMetadata {
        val fallback = StoreGameMetadata(title = markerName)
        return try {
            when (store) {
                GameNativeStore.GOG -> fetchGogMetadata(id) ?: fallback
                GameNativeStore.EPIC, GameNativeStore.AMAZON -> fetchSteamSearchMetadata(markerName) ?: fallback
            }
        } catch (e: Exception) {
            Logger.warn(TAG, "fetchMetadata: failed | store=${store.slug}, name=$markerName, ${e.message}")
            fallback
        }
    }

    private fun fetchGogMetadata(productId: Int): StoreGameMetadata? {
        val request = Request.Builder()
            .url("https://api.gog.com/v2/games/$productId")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            val product = json.optJSONObject("_embedded")?.optJSONObject("product")
            val title = product?.optString("title")?.takeIf { it.isNotBlank() } ?: return null
            val links = json.optJSONObject("_links")
            val cover = links?.optJSONObject("boxArtImage")?.optString("href")?.takeIf { it.isNotBlank() }
            val background = links?.optJSONObject("backgroundImage")?.optString("href")?.takeIf { it.isNotBlank() }
            val developer = json.optJSONObject("_embedded")
                ?.optJSONArray("developers")?.optJSONObject(0)?.optString("name")?.takeIf { it.isNotBlank() }
            val releaseYear = product.optString("globalReleaseDate").takeIf { it.length >= 4 }
                ?.take(4)?.toIntOrNull()
            val details = fetchGogDetails(productId)
            return StoreGameMetadata(
                title = title,
                coverUrl = cover,
                backgroundUrl = background,
                developer = developer,
                releaseYear = releaseYear,
                description = details?.first,
                screenshotUrls = details?.second ?: emptyList()
            )
        }
    }

    private fun fetchGogDetails(productId: Int): Pair<String?, List<String>>? {
        val request = Request.Builder()
            .url("https://api.gog.com/products/$productId?expand=description,screenshots")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            val lead = json.optJSONObject("description")?.optString("lead")
                ?.replace(Regex("<[^>]*>"), " ")
                ?.replace(Regex("\\s+"), " ")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            val screenshots = json.optJSONArray("screenshots")?.let { arr ->
                (0 until minOf(arr.length(), MAX_SCREENSHOTS)).mapNotNull { i ->
                    arr.optJSONObject(i)?.optString("formatter_template_url")
                        ?.takeIf { it.isNotBlank() }
                        ?.replace("{formatter}", "1600")
                }
            } ?: emptyList()
            return lead to screenshots
        }
    }

    private suspend fun fetchSteamSearchMetadata(name: String): StoreGameMetadata? {
        val response = steamSearchApi.search(name)
        if (!response.isSuccessful) return null
        val items = response.body()?.items ?: return null
        val match = items.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: items.firstOrNull() ?: return null
        val details = runCatching {
            steamStoreApi.getAppDetails(match.id).body()?.get(match.id.toString())?.data
        }.getOrNull()
        val screenshots = details?.screenshots
            ?.mapNotNull { it.pathFull }
            ?.take(MAX_SCREENSHOTS)
            ?: emptyList()
        return StoreGameMetadata(
            title = name,
            coverUrl = "https://steamcdn-a.akamaihd.net/steam/apps/${match.id}/library_600x900.jpg",
            backgroundUrl = "https://steamcdn-a.akamaihd.net/steam/apps/${match.id}/library_hero.jpg",
            description = details?.plainShortDescription,
            genre = details?.genres?.mapNotNull { it.description }?.joinToString(", ")?.takeIf { it.isNotBlank() },
            screenshotUrls = screenshots
        )
    }

    private suspend fun ensurePlatformExists(store: GameNativeStore) {
        if (platformDao.getById(store.platformId) != null) return
        Logger.debug(TAG, "ensurePlatformExists: creating ${store.displayName} platform")
        platformDao.insert(
            PlatformEntity(
                id = store.platformId,
                slug = store.slug,
                name = store.displayName,
                shortName = store.displayName,
                sortOrder = 3,
                isVisible = true,
                romExtensions = "",
                gameCount = 0
            )
        )
    }

    private fun createSortTitle(title: String): String {
        val lower = title.lowercase()
        return when {
            lower.startsWith("the ") -> title.drop(4)
            lower.startsWith("a ") -> title.drop(2)
            lower.startsWith("an ") -> title.drop(3)
            else -> title
        }.lowercase()
    }

    data class StoreGameMetadata(
        val title: String,
        val coverUrl: String? = null,
        val backgroundUrl: String? = null,
        val developer: String? = null,
        val releaseYear: Int? = null,
        val genre: String? = null,
        val description: String? = null,
        val screenshotUrls: List<String> = emptyList()
    )

    data class ScanSummary(val results: Map<GameNativeSyncFolder, StoreScanResult>) {
        val configured: Boolean get() = results.isNotEmpty()
    }

    private companion object {
        private const val TAG = "GameNativeStoreSync"
        private const val MAX_SCREENSHOTS = 10
        private const val GAMENATIVE_LAUNCHER = "gamenative"
    }
}
