package com.nendo.argosy.data.cache

import com.nendo.argosy.data.local.dao.ArtScrapeCandidate
import com.nendo.argosy.data.local.dao.GameArtDao
import com.nendo.argosy.data.model.ArtSlot
import com.nendo.argosy.data.preferences.AppPreferencesRepository
import com.nendo.argosy.data.preferences.BoxArtShape
import com.nendo.argosy.data.preferences.DisplayPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMCoverArtType
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** What a scrape run is allowed to touch. Stored as a token, never as a label. */
enum class CoverScrapeScope(val token: String) {
    /** Games with no cover row, no source, or no cached file. */
    MISSING("MISSING"),

    /** Covers never checked against the current Box Art Shape, or known to miss it. */
    SHAPE("SHAPE"),

    /** Every cover without a hand-picked override, whether or not it already fits. */
    ALL("ALL");

    companion object {
        fun fromToken(token: String?): CoverScrapeScope =
            entries.firstOrNull { it.token == token } ?: MISSING
    }
}

enum class CoverScrapeStage {
    IDLE,
    RUNNING,
    FINISHED,
    /** RomM is not connected or its server cannot search covers. */
    UNAVAILABLE
}

data class CoverScrapeState(
    val stage: CoverScrapeStage = CoverScrapeStage.IDLE,
    val scope: CoverScrapeScope = CoverScrapeScope.MISSING,
    val scanned: Int = 0,
    val total: Int = 0,
    val filled: Int = 0
)

/**
 * Gives games covers from the art their RomM server can already search, as a library-wide run or
 * for one game. RomM's cover search asks the server's own configured providers, so the launcher
 * holds no art-provider credentials of its own.
 *
 * A finished cover carries the Box Art Shape it was chosen for. That marker is what keeps a run
 * cheap: [CoverScrapeScope.MISSING] only ever visits games with nothing on screen, and
 * [CoverScrapeScope.SHAPE] only ones whose cover was never checked against the current shape or
 * does not measure up, so a second run of the same scope asks RomM nothing.
 */
@Singleton
class CoverArtScraper @Inject constructor(
    private val gameArtDao: GameArtDao,
    private val appPreferences: AppPreferencesRepository,
    private val displayPreferences: DisplayPreferencesRepository,
    private val imageCacheManager: ImageCacheManager,
    private val romMRepository: dagger.Lazy<RomMRepository>
) {
    companion object {
        private const val TAG = "CoverScrape"
    }

    private enum class Outcome { FILLED, SKIPPED, TRANSIENT }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(CoverScrapeState())
    val state: StateFlow<CoverScrapeState> = _state.asStateFlow()

    private var job: Job? = null

    /** Whether RomM can answer a cover search at all. Drives the settings row's enabled state. */
    fun canScrape(): Boolean =
        romMRepository.get().isConnected() &&
            romMRepository.get().getCapabilities().supportsCoverSearch

    fun scrapeMissing() = start(CoverScrapeScope.MISSING)

    fun repairShapes() = start(CoverScrapeScope.SHAPE)

    fun replaceAll() = start(CoverScrapeScope.ALL)

    /** Refills one game's cover, for the scrape action in the art picker. */
    fun scrapeGame(gameId: Long) {
        scope.launch {
            if (!canScrape()) {
                _state.value = CoverScrapeState(stage = CoverScrapeStage.UNAVAILABLE)
                return@launch
            }
            val candidate = gameArtDao.getArtScrapeCandidate(gameId) ?: return@launch
            val shape = displayPreferences.preferences.first().boxArtShape
            _state.value = CoverScrapeState(stage = CoverScrapeStage.RUNNING, total = 1)
            val outcome = scrapeOne(candidate, shape)
            _state.value = CoverScrapeState(
                stage = CoverScrapeStage.FINISHED,
                scanned = 1,
                total = 1,
                filled = if (outcome == Outcome.FILLED) 1 else 0
            )
        }
    }

    /** Fills covers a sync left empty. It never repairs shapes; that is a deliberate run. */
    fun fillMissingIfAny() {
        scope.launch {
            if (!canScrape() || job?.isActive == true) return@launch
            val shape = displayPreferences.preferences.first().boxArtShape
            val bounds = shape.coverRatioRange
            val total = gameArtDao.countArtScrapeCandidates(
                platformSlug = null,
                scope = CoverScrapeScope.MISSING.token,
                shape = shape.name,
                minRatio = bounds.start,
                maxRatio = bounds.endInclusive
            )
            if (total == 0) return@launch
            start(CoverScrapeScope.MISSING)
        }
    }

    /** A platform's cached artwork was deleted: refill that platform only. */
    fun refillPlatform(platformSlug: String) {
        start(CoverScrapeScope.MISSING, platformSlug)
    }

    /**
     * Restarts a run the process ended in the middle of. A finished run clears its cursor, so
     * this starts nothing on a clean launch.
     */
    fun resumeIfInterrupted() {
        scope.launch {
            if (appPreferences.getArtScrapeCursor() == 0L) return@launch
            if (!canScrape()) return@launch
            start(
                CoverScrapeScope.fromToken(appPreferences.getArtScrapeScope()),
                appPreferences.getArtScrapePlatform()
            )
        }
    }

    /** Cancels a running scrape and leaves it stopped; the next run starts from the beginning. */
    fun stop() {
        job?.cancel()
        job = null
        _state.value = CoverScrapeState(stage = CoverScrapeStage.IDLE)
        scope.launch { clearCursor() }
    }

    fun start(scope: CoverScrapeScope, platformSlug: String? = null) {
        if (job?.isActive == true) return
        job = this.scope.launch { run(scope, platformSlug) }
    }

    private suspend fun run(scope: CoverScrapeScope, platformSlug: String?) {
        if (!canScrape()) {
            _state.value = CoverScrapeState(stage = CoverScrapeStage.UNAVAILABLE, scope = scope)
            return
        }
        val shape = displayPreferences.preferences.first().boxArtShape
        val bounds = shape.coverRatioRange
        val total = gameArtDao.countArtScrapeCandidates(
            platformSlug = platformSlug,
            scope = scope.token,
            shape = shape.name,
            minRatio = bounds.start,
            maxRatio = bounds.endInclusive
        )
        if (total == 0) {
            _state.value = CoverScrapeState(stage = CoverScrapeStage.FINISHED, scope = scope)
            return
        }
        _state.value = CoverScrapeState(stage = CoverScrapeStage.RUNNING, scope = scope, total = total)
        appPreferences.setArtScrapeScope(scope.token)
        appPreferences.setArtScrapePlatform(platformSlug)
        var cursor = if (platformSlug != null) 0L else appPreferences.getArtScrapeCursor()
        while (currentCoroutineContext().isActive) {
            val candidate = gameArtDao.getNextArtScrapeCandidate(
                afterGameId = cursor,
                platformSlug = platformSlug,
                scope = scope.token,
                shape = shape.name,
                minRatio = bounds.start,
                maxRatio = bounds.endInclusive
            ) ?: break
            val filled = scrapeOne(candidate, shape) == Outcome.FILLED
            cursor = candidate.gameId
            appPreferences.setArtScrapeCursor(cursor)
            _state.update { current ->
                current.copy(
                    scanned = current.scanned + 1,
                    filled = current.filled + if (filled) 1 else 0
                )
            }
        }
        if (currentCoroutineContext().isActive) {
            clearCursor()
            val filled = _state.value.filled
            _state.update { current -> current.copy(stage = CoverScrapeStage.FINISHED) }
            Logger.info(TAG, "${scope.token} cover scrape finished, $filled covers filled")
        }
    }

    private suspend fun clearCursor() {
        appPreferences.setArtScrapeCursor(0L)
        appPreferences.setArtScrapePlatform(null)
        appPreferences.setArtScrapeScope(null)
    }

    /**
     * Asks RomM for this game's grids and takes the one that fits the current shape. RomM's
     * search resolves the title on the server and answers with images only, so the result is a
     * server-side match; SteamGridDB-backed grids are preferred because that is the provider the
     * server's own scrape uses.
     */
    private suspend fun scrapeOne(candidate: ArtScrapeCandidate, shape: BoxArtShape): Outcome {
        val resources = when (
            val result = romMRepository.get().searchCovers(candidate.title, RomMCoverArtType.GRID)
        ) {
            is RomMResult.Success -> result.data
            is RomMResult.Error -> return Outcome.TRANSIENT
        }
        val preferred = resources.sortedBy { resource ->
            if (resource.provider.equals("sgdb", ignoreCase = true)) 0 else 1
        }
        val chosen = preferred.chooseForShape(shape) { resource ->
            artRatio(resource.width, resource.height)
        } ?: return Outcome.SKIPPED
        val url = chosen.url ?: return Outcome.SKIPPED
        gameArtDao.insertSlotIfAbsent(candidate.gameId, ArtSlot.COVER.name)
        gameArtDao.setSourceUrl(candidate.gameId, ArtSlot.COVER, url)
        imageCacheManager.queueArtIfStale(
            candidate.gameId,
            ArtSlot.COVER,
            listOf(url),
            candidate.rommId,
            candidate.steamAppId,
            candidate.title
        )
        gameArtDao.markCoverScraped(
            candidate.gameId,
            shape.name,
            System.currentTimeMillis()
        )
        return Outcome.FILLED
    }

    private fun <T> List<T>.chooseForShape(shape: BoxArtShape, ratioOf: (T) -> Float?): T? {
        val sized = mapNotNull { item -> ratioOf(item)?.let { item to it } }
        if (sized.isEmpty()) return firstOrNull()
        val target = shape.coverRatioTarget
        val fitting = sized.filter { (_, ratio) -> ratio in shape.coverRatioRange }
        // A square tile never takes a tall or wide grid: cropped into the square it showed a
        // sliver of a cover. With no square art the game keeps the cover it already has.
        if (fitting.isEmpty() && shape == BoxArtShape.SQUARE) return null
        val pool = if (fitting.isEmpty()) sized else fitting
        return pool.minByOrNull { (_, ratio) -> abs(ratio - target) }?.first
    }

    private fun artRatio(width: Int?, height: Int?): Float? {
        val w = width ?: return null
        val h = height ?: return null
        if (w <= 0 || h <= 0) return null
        return w.toFloat() / h
    }
}
