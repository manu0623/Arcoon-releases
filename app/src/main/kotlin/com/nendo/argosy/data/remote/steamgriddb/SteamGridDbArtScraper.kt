package com.nendo.argosy.data.remote.steamgriddb

import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.local.dao.ArtScrapeCandidate
import com.nendo.argosy.data.local.dao.GameArtDao
import com.nendo.argosy.data.model.ArtSlot
import com.nendo.argosy.data.preferences.AppPreferencesRepository
import com.nendo.argosy.data.preferences.BoxArtShape
import com.nendo.argosy.data.preferences.DisplayPreferencesRepository
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

enum class SteamGridDbScrapeStage {
    IDLE,
    RUNNING,
    PARKED,
    FINISHED,
    NEEDS_KEY,
    RESTORING,
    RESTORED
}

data class SteamGridDbScrapeState(
    val stage: SteamGridDbScrapeStage = SteamGridDbScrapeStage.IDLE,
    val scanned: Int = 0,
    val total: Int = 0,
    val filled: Int = 0,
    val parkedUntilMillis: Long? = null
)

/**
 * Walks the library in id order and gives every game a SteamGridDB cover source. The table is the
 * queue: a game leaves it once its cover comes from SteamGridDB, and a stored cursor resumes an
 * interrupted run without a second lookup. A rate limit parks the run until the window reopens.
 * Grids are chosen to fit the box art shape in Settings, and changing that shape re-picks every
 * cover from grids with the new tile proportions.
 */
@Singleton
class SteamGridDbArtScraper @Inject constructor(
    private val client: SteamGridDbClient,
    private val gameArtDao: GameArtDao,
    private val appPreferences: AppPreferencesRepository,
    private val displayPreferences: DisplayPreferencesRepository,
    private val imageCacheManager: ImageCacheManager,
    private val romMSync: dagger.Lazy<com.nendo.argosy.data.remote.romm.RomMLibrarySyncService>
) {
    companion object {
        private const val TAG = "SteamGridDbScrape"
        private const val UNAUTHORIZED = 401
        private const val FORBIDDEN = 403
        private const val LEGACY_COVER_RATIO = 2f / 3f
        // 3: square tiles take square grids only (asked for by size), and the Cocoon home counts
        // as square whatever the box art setting says.
        private const val SHAPE_SELECTION_VERSION = 3
    }

    private enum class Outcome {
        FILLED,
        SKIPPED,
        RATE_LIMITED,
        UNAUTHORIZED,
        TRANSIENT
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(SteamGridDbScrapeState())
    val state: StateFlow<SteamGridDbScrapeState> = _state.asStateFlow()

    private var job: Job? = null
    private var restoreJob: Job? = null

    init {
        scope.launch {
            displayPreferences.preferences
                .map { it.effectiveCoverShape() }
                .distinctUntilChanged()
                .collect { shape -> applyCoverShape(shape) }
        }
        scope.launch {
            appPreferences.preferences
                .map { it.steamGridDbEnabled && !it.steamGridDbApiKey.isNullOrBlank() }
                .distinctUntilChanged()
                .collect { active -> if (!active) haltRun() }
        }
    }

    fun start(platformSlug: String? = null, rescanAll: Boolean = false) {
        if (job?.isActive == true) return
        restoreJob?.cancel()
        restoreJob = null
        job = scope.launch { runScrape(platformSlug, rescanAll) }
    }

    /**
     * Replaces every cover that did not come from SteamGridDB with a fresh lookup. Hand-picked
     * overrides keep their art.
     */
    suspend fun scrapeLibrary() {
        val prefs = appPreferences.preferences.first()
        if (!prefs.steamGridDbEnabled || prefs.steamGridDbApiKey.isNullOrBlank()) return
        appPreferences.setSteamGridDbScrapePlatform(null)
        appPreferences.setSteamGridDbScrapeCursor(0L)
        appPreferences.setSteamGridDbScrapeRescanAll(false)
        appPreferences.setSteamGridDbScrapeAutoStart(true)
        start()
    }

    /**
     * Walks the whole library again from the first game, replacing covers that already come from
     * SteamGridDB as well.
     */
    suspend fun restartFromScratch() {
        val prefs = appPreferences.preferences.first()
        if (!prefs.steamGridDbEnabled || prefs.steamGridDbApiKey.isNullOrBlank()) return
        appPreferences.setSteamGridDbScrapePlatform(null)
        appPreferences.setSteamGridDbScrapeCursor(0L)
        appPreferences.setSteamGridDbScrapeRescanAll(true)
        appPreferences.setSteamGridDbScrapeAutoStart(true)
        start(rescanAll = true)
    }

    /**
     * Cancels a running scrape and leaves it stopped for the next launch.
     */
    fun stop() {
        job?.cancel()
        job = null
        _state.value = SteamGridDbScrapeState(stage = SteamGridDbScrapeStage.IDLE)
        scope.launch { clearRunMarkers() }
    }

    /**
     * Starts a run left unfinished, or one for games that still have no cover.
     */
    fun autoStart() {
        scope.launch {
            val prefs = appPreferences.preferences.first()
            if (!prefs.steamGridDbEnabled || prefs.steamGridDbApiKey.isNullOrBlank()) return@launch
            if (!appPreferences.getSteamGridDbScrapeAutoStart()) return@launch
            val rescanAll = appPreferences.getSteamGridDbScrapeRescanAll()
            val interrupted = appPreferences.getSteamGridDbScrapeCursor() > 0L ||
                appPreferences.getSteamGridDbScrapeParkedUntil() > System.currentTimeMillis()
            if (!interrupted && gameArtDao.countArtScrapeCandidates(rescanAll = rescanAll) == 0) {
                return@launch
            }
            start(appPreferences.getSteamGridDbScrapePlatform(), rescanAll)
        }
    }

    /**
     * A platform's cached artwork was just deleted: with SteamGridDB active its covers come back
     * from there, not from the server urls the cleared rows still carried.
     */
    suspend fun refreshPlatformFromSteamGridDb(platformSlug: String) {
        val prefs = appPreferences.preferences.first()
        if (!prefs.steamGridDbEnabled || prefs.steamGridDbApiKey.isNullOrBlank()) return
        gameArtDao.clearCoverRowsForPlatform(platformSlug)
        start(platformSlug)
    }

    /**
     * Restarts a run that the process ended in the middle of: a stored cursor, or a park that has
     * not expired yet. A finished run leaves neither behind.
     */
    fun resumeIfInterrupted() {
        scope.launch {
            val cursor = appPreferences.getSteamGridDbScrapeCursor()
            val parkedUntil = appPreferences.getSteamGridDbScrapeParkedUntil()
            val parked = parkedUntil > System.currentTimeMillis()
            if (parkedUntil > 0L && !parked) appPreferences.setSteamGridDbScrapeParkedUntil(0L)
            if (cursor == 0L && !parked) return@launch
            start(
                appPreferences.getSteamGridDbScrapePlatform(),
                appPreferences.getSteamGridDbScrapeRescanAll()
            )
        }
    }

    /**
     * SteamGridDB was switched off: every cover it supplied is asked from RomM again, game by
     * game, through the same path a sync takes, so the RomM art replaces it on screen. Games RomM
     * does not know keep their SteamGridDB cover.
     */
    fun restoreRomMCovers() {
        if (restoreJob?.isActive == true) return
        restoreJob = scope.launch {
            val romIds = gameArtDao.getRommIdsWithSteamGridDbCover()
            if (romIds.isEmpty()) return@launch
            Logger.info(TAG, "Restoring RomM covers for ${romIds.size} games")
            _state.value = SteamGridDbScrapeState(stage = SteamGridDbScrapeStage.RESTORING, total = romIds.size)
            var restored = 0
            for (romId in romIds) {
                if (!currentCoroutineContext().isActive) return@launch
                when (val result = romMSync.get().syncSingleRom(romId)) {
                    is com.nendo.argosy.data.remote.romm.RomMResult.Success -> restored++
                    is com.nendo.argosy.data.remote.romm.RomMResult.Error -> {
                        if (result.message == "Not connected") {
                            Logger.warn(TAG, "RomM is not connected; $restored of ${romIds.size} covers restored")
                            break
                        }
                    }
                }
                _state.update { current -> current.copy(scanned = current.scanned + 1, filled = restored) }
            }
            _state.update { current -> current.copy(stage = SteamGridDbScrapeStage.RESTORED, filled = restored) }
            Logger.info(TAG, "RomM covers restored for $restored of ${romIds.size} games")
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.update { current -> current.copy(stage = SteamGridDbScrapeStage.IDLE, parkedUntilMillis = null) }
    }

    private suspend fun runScrape(platformSlug: String?, rescanAll: Boolean) {
        val prefs = appPreferences.preferences.first()
        val apiKey = prefs.steamGridDbApiKey
        if (!prefs.steamGridDbEnabled || apiKey.isNullOrBlank()) {
            _state.value = SteamGridDbScrapeState(stage = SteamGridDbScrapeStage.NEEDS_KEY)
            return
        }
        appPreferences.setSteamGridDbScrapeAutoStart(true)
        appPreferences.setSteamGridDbScrapeRescanAll(rescanAll)
        val coverShape = displayPreferences.preferences.first().effectiveCoverShape()
        val total = gameArtDao.countArtScrapeCandidates(platformSlug, rescanAll)
        if (total == 0) {
            _state.value = SteamGridDbScrapeState(stage = SteamGridDbScrapeStage.FINISHED)
            return
        }
        _state.value = SteamGridDbScrapeState(
            stage = SteamGridDbScrapeStage.RUNNING,
            total = total
        )
        if (platformSlug != null) {
            appPreferences.setSteamGridDbScrapePlatform(platformSlug)
            appPreferences.setSteamGridDbScrapeCursor(0L)
        }
        var cursor = if (platformSlug != null) 0L else appPreferences.getSteamGridDbScrapeCursor()
        awaitParkedWindow()
        while (currentCoroutineContext().isActive) {
            val candidate = gameArtDao.getNextArtScrapeCandidate(cursor, platformSlug, rescanAll) ?: break
            when (val outcome = scrapeOne(candidate, apiKey, coverShape)) {
                Outcome.RATE_LIMITED -> {
                    awaitParkedWindow()
                    continue
                }
                Outcome.UNAUTHORIZED -> {
                    _state.update { current -> current.copy(stage = SteamGridDbScrapeStage.NEEDS_KEY) }
                    Logger.warn(TAG, "SteamGridDB refused the API key; parking the queue at game ${candidate.gameId}")
                    return
                }
                else -> {
                    cursor = candidate.gameId
                    appPreferences.setSteamGridDbScrapeCursor(cursor)
                    _state.update { current ->
                        current.copy(
                            scanned = current.scanned + 1,
                            filled = current.filled + if (outcome == Outcome.FILLED) 1 else 0
                        )
                    }
                }
            }
        }
        if (currentCoroutineContext().isActive) {
            appPreferences.setSteamGridDbScrapeCursor(0L)
            appPreferences.setSteamGridDbScrapeParkedUntil(0L)
            appPreferences.setSteamGridDbScrapePlatform(null)
            appPreferences.setSteamGridDbScrapeRescanAll(false)
            val filled = _state.value.filled
            _state.update { current -> current.copy(stage = SteamGridDbScrapeStage.FINISHED) }
            Logger.info(TAG, "SteamGridDB scrape finished, $filled covers filled")
        }
    }

    private suspend fun scrapeOne(
        candidate: ArtScrapeCandidate,
        apiKey: String,
        coverShape: BoxArtShape
    ): Outcome {
        // Square tiles ask SteamGridDB for square grids by size; an unfiltered list can be all
        // tall covers on its first page even when square ones exist.
        val result = if (coverShape == BoxArtShape.SQUARE) {
            client.searchSquareGrids(candidate.title, apiKey)
        } else {
            client.searchGrids(candidate.title, apiKey)
        }
        val error = result.exceptionOrNull()
        if (error is SteamGridDbRateLimited) {
            val until = System.currentTimeMillis() + error.retryAfterMillis
            appPreferences.setSteamGridDbScrapeParkedUntil(until)
            _state.update { current -> current.copy(stage = SteamGridDbScrapeStage.PARKED, parkedUntilMillis = until) }
            return Outcome.RATE_LIMITED
        }
        if (error is SteamGridDbRequestFailed) {
            if (error.code == UNAUTHORIZED || error.code == FORBIDDEN) return Outcome.UNAUTHORIZED
            if (error.code >= 500) return Outcome.TRANSIENT
        }
        if (error != null) return Outcome.TRANSIENT
        val search = result.getOrNull() ?: return Outcome.TRANSIENT
        val grid = search.grids.gridForShape(coverShape)
        if (grid == null) {
            gameArtDao.insertSlotIfAbsent(candidate.gameId, ArtSlot.COVER.name)
            return Outcome.SKIPPED
        }
        val aliases = gameArtDao.getTitleAliases(candidate.gameId)
        val alternatives = parseAlternativeNames(aliases?.alternativeNames) +
            listOfNotNull(aliases?.searchTitle?.takeIf { it.isNotBlank() })
        val matchedTitle = search.matchedName?.takeIf { it.isNotBlank() }
        if (matchedTitle == null || !isConfidentTitleMatch(candidate.title, alternatives, matchedTitle)) {
            gameArtDao.insertSlotIfAbsent(candidate.gameId, ArtSlot.COVER.name)
            return Outcome.SKIPPED
        }
        gameArtDao.setSourceUrl(candidate.gameId, ArtSlot.COVER, grid.url)
        imageCacheManager.queueArtIfStale(
            candidate.gameId,
            ArtSlot.COVER,
            listOf(grid.url),
            candidate.rommId,
            candidate.steamAppId,
            candidate.title
        )
        return Outcome.FILLED
    }

    private fun List<SteamGridDbGrid>.gridForShape(shape: BoxArtShape): SteamGridDbGrid? {
        val sized = mapNotNull { grid ->
            val width = grid.width ?: return@mapNotNull null
            val height = grid.height ?: return@mapNotNull null
            grid to (width.toFloat() / height)
        }
        if (sized.isEmpty()) return firstOrNull()
        val target = shape.coverRatioTarget
        val fitting = sized.filter { (_, ratio) -> ratio in shape.coverRatioRange }
        // A square tile never takes a tall or wide grid: cropped into the square it showed a
        // sliver of a cover. With no square art the game keeps the cover it already has.
        if (fitting.isEmpty() && shape == BoxArtShape.SQUARE) return null
        val pool = if (fitting.isEmpty()) sized else fitting
        return pool.minByOrNull { (_, ratio) -> abs(ratio - target) }?.first
    }

    private suspend fun applyCoverShape(shape: BoxArtShape) {
        val recorded = appPreferences.getSteamGridDbCoverShape()
        val token = "${shape.name}@$SHAPE_SELECTION_VERSION"
        if (recorded == token) return
        appPreferences.setSteamGridDbCoverShape(token)
        val coversFitTile = LEGACY_COVER_RATIO in shape.coverRatioRange &&
            (recorded == null || recorded == shape.name)
        if (coversFitTile) return
        val prefs = appPreferences.preferences.first()
        if (!prefs.steamGridDbEnabled || prefs.steamGridDbApiKey.isNullOrBlank()) return
        job?.cancel()
        job = null
        restartFromScratch()
    }

    private suspend fun haltRun() {
        val wasRunning = job?.isActive == true
        job?.cancel()
        job = null
        _state.update { current ->
            current.copy(stage = SteamGridDbScrapeStage.IDLE, parkedUntilMillis = null)
        }
        clearRunMarkers()
        if (wasRunning) Logger.info(TAG, "SteamGridDB disabled; the running scrape was stopped")
    }

    private suspend fun clearRunMarkers() {
        appPreferences.setSteamGridDbScrapeCursor(0L)
        appPreferences.setSteamGridDbScrapeParkedUntil(0L)
        appPreferences.setSteamGridDbScrapePlatform(null)
        appPreferences.setSteamGridDbScrapeRescanAll(false)
        appPreferences.setSteamGridDbScrapeAutoStart(false)
    }

    private suspend fun awaitParkedWindow() {
        val until = appPreferences.getSteamGridDbScrapeParkedUntil()
        val waitMillis = until - System.currentTimeMillis()
        if (waitMillis <= 0L) {
            if (until > 0L) appPreferences.setSteamGridDbScrapeParkedUntil(0L)
            _state.update { current -> current.copy(stage = SteamGridDbScrapeStage.RUNNING, parkedUntilMillis = null) }
            return
        }
        _state.update { current -> current.copy(stage = SteamGridDbScrapeStage.PARKED, parkedUntilMillis = until) }
        Logger.info(TAG, "SteamGridDB parked for ${waitMillis / 1000}s")
        delay(waitMillis)
        appPreferences.setSteamGridDbScrapeParkedUntil(0L)
        _state.update { current -> current.copy(stage = SteamGridDbScrapeStage.RUNNING, parkedUntilMillis = null) }
    }
}

/** The tile shape covers are drawn at: always square in the Cocoon home, else the setting. */
private fun com.nendo.argosy.data.preferences.DisplayPreferences.effectiveCoverShape(): BoxArtShape =
    if (launcherUiMode == com.nendo.argosy.data.preferences.LauncherUiMode.COCOON) BoxArtShape.SQUARE else boxArtShape
