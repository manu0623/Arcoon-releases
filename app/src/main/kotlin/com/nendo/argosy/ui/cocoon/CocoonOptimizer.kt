package com.nendo.argosy.ui.cocoon

import android.content.Context
import androidx.annotation.StringRes
import com.nendo.argosy.R
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CocoonOptimizeEntryPoint {
    fun gameDao(): com.nendo.argosy.data.local.dao.GameDao
    fun imageCacheManager(): com.nendo.argosy.data.cache.ImageCacheManager
    fun database(): com.nendo.argosy.data.local.ALauncherDatabase
    fun quickPanelOverlay(): com.nendo.argosy.ui.quickpanel.QuickPanelOverlay
}

enum class CocoonOptimizeStep(@StringRes val labelRes: Int, val weight: Float) {
    /** One time: covers saved at the old smaller size are downloaded again, sharper. */
    SHARPEN(R.string.cocoon_opt_sharpen, 1f),
    COVERS(R.string.cocoon_opt_covers, 6f),
    ICONS(R.string.cocoon_opt_icons, 1f),
    APPS(R.string.cocoon_opt_apps, 1f),
    DATABASE(R.string.cocoon_opt_database, 0.5f),
    LIBRARY(R.string.cocoon_opt_library, 1f),
    WARMUP(R.string.cocoon_opt_warmup, 1f),
    COMPILE(R.string.cocoon_opt_compile, 3f),
    TIDY(R.string.cocoon_opt_tidy, 0.5f),
}

data class CocoonOptimizeState(
    val introVisible: Boolean = false,
    val running: Boolean = false,
    val finished: Boolean = false,
    val steps: List<CocoonOptimizeStep> = emptyList(),
    val done: Set<CocoonOptimizeStep> = emptySet(),
    val step: CocoonOptimizeStep? = null,
    /** Items done / total in the current step (0 total: no count to show). */
    val current: Int = 0,
    val total: Int = 0,
    val progress: Float = 0f,
    /** Platforms shown as folders on the top screen, and which of them are ready. */
    val platforms: List<String> = emptyList(),
    val readyPlatforms: Set<String> = emptySet(),
    /** While true the intro quietly draws the app's effects once, behind its black backdrop. */
    val warmingUp: Boolean = false,
)

/**
 * Cocoon-style device optimization. It prepares cover thumbnails, warms the folder art and the
 * library, asks Android to fully compile the app (release builds on the Thor) and tidies old
 * files. Every finished piece is saved as it goes, so closing the app loses nothing; the next start
 * only does what is left. The intro shows only when there is real work; skipping hides it and the
 * work carries on quietly in the background.
 */
object CocoonOptimizer {
    private const val PREFS = "cocoon_optimize"
    /** Fewer covers than this left to prepare finish quietly, without the intro. */
    private const val INTRO_THRESHOLD = 25
    private const val KEY_COVERS_SHARPENED = "covers_sharpened_512"
    /** How many of the most recently played games get their background and logo warmed. */
    private const val RECENT_ART_WARMUP = 8
    /** The size the board decodes theme folder icons at (see CocoonFolderIcon). */
    private const val FOLDER_ICON_PX = 320
    /** The size the top screen asks for a folder's logo at (see CocoonHeroScene). */
    private const val FOLDER_LOGO_PX = 1024
    /** The size the top screen's full-screen folder pictures are decoded at (as the board's prefetch). */
    private const val FOLDER_HERO_PX = 2048
    /** How many folders get their full-screen top-screen picture decoded ahead (they are large). */
    private const val FOLDER_HERO_WARMUP = 4
    /** On an ordinary start, the quick menu is warmed this long after the optimizer check. */
    private const val QUICK_PANEL_WARM_DELAY_MS = 2500L

    /** Builds the R1 / L1 panel once, invisibly, so its first opening does not stall (main thread). */
    private suspend fun prewarmQuickPanel(entry: CocoonOptimizeEntryPoint) {
        runCatching {
            kotlinx.coroutines.withContext(Dispatchers.Main) { entry.quickPanelOverlay().prewarm() }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * The main screens' code, loaded ahead so opening them the first time doesn't stall. Release
     * builds keep these names (proguard-rules.pro, "Arcoon optimizer warm-up"), or the shrunk app
     * would not find them and this step would silently do nothing.
     */
    private val WARM_CLASSES = listOf(
        "com.nendo.argosy.ui.cocoon.CocoonHomeScreenKt",
        "com.nendo.argosy.ui.cocoon.CocoonHomeViewModel",
        "com.nendo.argosy.ui.cocoon.CocoonFolderCoversKt",
        "com.nendo.argosy.ui.cocoon.CocoonWidgetsKt",
        "com.nendo.argosy.ui.screens.library.LibraryScreenKt",
        "com.nendo.argosy.ui.screens.library.LibraryViewModel",
        "com.nendo.argosy.ui.screens.settings.SettingsScreenKt",
        "com.nendo.argosy.ui.screens.settings.SettingsViewModel",
        "com.nendo.argosy.ui.screens.settings.SettingsInitRouterKt",
        "com.nendo.argosy.ui.screens.gamedetail.GameDetailScreenKt",
        "com.nendo.argosy.ui.screens.gamedetail.GameDetailViewModel",
        "com.nendo.argosy.ui.screens.apps.AppsScreenKt",
        "com.nendo.argosy.ui.screens.search.SearchScreenKt",
        "com.nendo.argosy.ui.components.QuickSettingsPanelKt",
        "com.nendo.argosy.ui.quickpanel.QuickPanelOverlay",
        "com.nendo.argosy.ui.components.GameCardKt",
        "com.nendo.argosy.ui.cocoon.theme.CocoonPersonalizationKt",
        "com.nendo.argosy.ui.cocoon.theme.CocoonThemePickerKt",
        // the top screen, the game page, menus, the cursor and the shared drawing pieces
        "com.nendo.argosy.ui.dualscreen.GameShowcaseKt",
        "com.nendo.argosy.ui.dualscreen.PresentationSlotContentKt",
        "com.nendo.argosy.ui.cocoon.CocoonGameHeroKt",
        "com.nendo.argosy.ui.cocoon.CocoonHeroKt",
        "com.nendo.argosy.ui.quickmenu.QuickMenuOverlayKt",
        "com.nendo.argosy.ui.primitives.CocoonBracketsKt",
        "com.nendo.argosy.ui.components.BoxArtFrameKt",
        "com.nendo.argosy.ui.cocoon.CocoonFolderKt",
        "com.nendo.argosy.ui.cocoon.CocoonFolderMenuKt",
        "com.nendo.argosy.ui.cocoon.CocoonFoldersKt",
        "com.nendo.argosy.ui.screens.gamedetail.components.GameDetailTabsKt",
        "com.nendo.argosy.ui.screens.gamedetail.components.GameDetailSectionsKt",
        "com.nendo.argosy.ui.common.DeferredBlurKt",
        "com.nendo.argosy.ui.navigation.NavGraphKt",
        "com.nendo.argosy.ui.ArgosyAppKt",
        "com.nendo.argosy.ui.components.SystemStatusBarKt",
        "coil.compose.AsyncImageKt",
        "coil.compose.AsyncImagePainter",
    )
    private var job: Job? = null
    private val _state = MutableStateFlow(CocoonOptimizeState())
    val state: StateFlow<CocoonOptimizeState> = _state.asStateFlow()

    @Volatile private var background = false

    /** Starts (or re-checks) the optimization. [force] always shows the intro (Settings ▸ Optimize now). */
    /** The automatic check runs once per app start, not each time Home comes back. */
    @Volatile private var checkedThisRun = false

    fun start(context: Context, force: Boolean = false) {
        if (job?.isActive == true) {
            if (force) { background = false; _state.update { it.copy(introVisible = true) } }
            return
        }
        if (!force && checkedThisRun) return
        checkedThisRun = true
        val app = context.applicationContext
        background = false
        job = scope.launch { runCatching { optimize(app, force) } }
    }

    /** Settings ▸ Free picture memory: lets go of every cached picture and app icon. */
    fun freePictureMemory(context: Context) {
        runCatching { coil.Coil.imageLoader(context.applicationContext).memoryCache?.clear() }
        CocoonAppIconCache.clear()
    }

    /** Fills the app-icon cache (dock, All Apps) in the background; cheap, runs on every start. */
    private fun warmAppIcons(app: Context, report: ((Int, Int) -> Unit)? = null) {
        val pm = app.packageManager
        val launchable = runCatching {
            pm.queryIntentActivities(
                android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER), 0
            ).map { it.activityInfo.packageName }.distinct()
        }.getOrDefault(emptyList())
        // dock apps first: they are on screen everywhere
        val dock = runCatching { com.nendo.argosy.DualScreenManagerHolder.instance?.homeAppsFlow?.value.orEmpty() }.getOrDefault(emptyList())
        val order = (dock + launchable).distinct()
        order.forEachIndexed { i, pkg ->
            CocoonAppIconCache.get(app, pkg)
            report?.invoke(i + 1, order.size)
        }
    }

    /** B / Skip: straight into the app; the rest keeps going in the background. */
    fun skip() {
        background = true
        _state.update { it.copy(introVisible = false) }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private suspend fun optimize(app: Context, force: Boolean) {
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val installStamp = runCatching {
            app.packageManager.getPackageInfo(app.packageName, 0).lastUpdateTime
        }.getOrDefault(0L)
        val entry = EntryPointAccessors.fromApplication(app, CocoonOptimizeEntryPoint::class.java)
        val games = runCatching { entry.gameDao().cocoonOptimizeGames() }.getOrDefault(emptyList())
        val icm = entry.imageCacheManager()

        // Every game's cover, and which still need a thumbnail.
        val covers = ArrayList<Pair<String, String>>() // platform to path
        games.forEach { game ->
            val path = (icm.artFor(game.id) ?: runCatching { icm.loadArt(game.id) }.getOrNull())?.coverPath
            if (path != null && path.startsWith("/")) covers += game.platformSlug to path
        }
        // Covers that couldn't be made into thumbnails are remembered (by identity) and skipped,
        // so a few odd files never bring the intro back.
        val failed = prefs.getStringSet("failed", emptySet()).orEmpty()
        val pending = covers.filter { (_, path) ->
            CocoonThumbs.ready(app, path) == null && File(path).isFile && CocoonThumbs.keyFor(path) !in failed
        }

        // Android only fully compiles a release (non-debug) app, and on the Thor it needs the
        // device's system access. Marked as tried before it starts, so it never loops.
        val needCompile = !com.nendo.argosy.BuildConfig.DEBUG &&
            prefs.getLong("compiled_for", 0L) != installStamp &&
            com.nendo.argosy.util.RootShell.isAvailable
        val newInstall = prefs.getLong("install", 0L) != installStamp
        // One time after covers started downloading at 512 px: the older, smaller ones are fetched again.
        val needSharpen = !prefs.getBoolean(KEY_COVERS_SHARPENED, false)
        // 3DS banners are not part of this: drawing them takes minutes per game, so they are made
        // in the background queue instead (see ImageCacheManager.render3dsBanners).
        if (!force && pending.isEmpty() && !needCompile && !newInstall && !needSharpen) {
            // nothing to optimize: just have the dock and app icons ready, quietly, and the quick
            // menu built once out of sight a moment after start-up
            warmAppIcons(app)
            delay(QUICK_PANEL_WARM_DELAY_MS)
            prewarmQuickPanel(entry)
            return
        }

        val steps = buildList {
            if (needSharpen) add(CocoonOptimizeStep.SHARPEN)
            if (pending.isNotEmpty() || force) add(CocoonOptimizeStep.COVERS)
            add(CocoonOptimizeStep.ICONS)
            add(CocoonOptimizeStep.APPS)
            add(CocoonOptimizeStep.DATABASE)
            add(CocoonOptimizeStep.LIBRARY)
            add(CocoonOptimizeStep.WARMUP)
            if (needCompile) add(CocoonOptimizeStep.COMPILE)
            add(CocoonOptimizeStep.TIDY)
        }
        val platforms = covers.groupingBy { it.first }.eachCount().entries
            .sortedByDescending { it.value }.take(12).map { it.key }
        // The intro shows by itself at most once per installed version, and never while a game is
        // running or suspended; anything after that finishes quietly in the background.
        val gameOpen = runCatching {
            val dsm = com.nendo.argosy.DualScreenManagerHolder.instance
            dsm?.swappedIsGameActive?.value == true || dsm?.gameSessionManager?.sessionState?.value != null
        }.getOrDefault(false)
        val introAlreadyShown = prefs.getLong("intro_shown_for", 0L) == installStamp
        val showIntro = force || (!gameOpen && !introAlreadyShown && (pending.size >= INTRO_THRESHOLD || needCompile))
        if (showIntro && !force) prefs.edit().putLong("intro_shown_for", installStamp).apply()
        if (!showIntro) background = true
        _state.value = CocoonOptimizeState(
            introVisible = showIntro, running = true, steps = steps, platforms = platforms
        )
        val totalWeight = steps.sumOf { it.weight.toDouble() }.toFloat()
        fun report(step: CocoonOptimizeStep, current: Int, total: Int) {
            val before = _state.value.done.sumOf { it.weight.toDouble() }.toFloat()
            val fraction = if (total > 0) current.toFloat() / total else 0f
            _state.update {
                it.copy(step = step, current = current, total = total,
                    progress = ((before + step.weight * fraction) / totalWeight).coerceIn(0f, 1f))
            }
        }
        fun finish(step: CocoonOptimizeStep) {
            _state.update { it.copy(done = it.done + step) }
            report(step, 1, 1)
        }

        // 0. Sharper covers (once): covers cut down to the old 400 px are queued to download again
        // at full size. The downloads themselves run in the image cache's own background queue.
        if (CocoonOptimizeStep.SHARPEN in steps) {
            runCatching {
                icm.refreshUndersizedCovers { done, total -> report(CocoonOptimizeStep.SHARPEN, done, total) }
            }.onSuccess { prefs.edit().putBoolean(KEY_COVERS_SHARPENED, true).apply() }
            finish(CocoonOptimizeStep.SHARPEN)
        }

        // 1. Covers, platform by platform; a platform's folder pops in on the top screen when its
        // covers are ready.
        if (CocoonOptimizeStep.COVERS in steps) {
            val byPlatform = pending.groupBy({ it.first }, { it.second })
            // platforms with nothing to do pop in first, one after another
            platforms.filter { it !in byPlatform }.forEach { slug ->
                _state.update { it.copy(readyPlatforms = it.readyPlatforms + slug) }
                if (!background) delay(110)
            }
            val doneCount = AtomicInteger(0)
            val failedNow = java.util.Collections.synchronizedSet(HashSet<String>())
            val total = pending.size
            report(CocoonOptimizeStep.COVERS, 0, total)
            val workers = Dispatchers.IO.limitedParallelism(3)
            val gate = Semaphore(1)
            val order = platforms.filter { it in byPlatform } + byPlatform.keys.filter { it !in platforms }
            order.forEach { slug ->
                val paths = byPlatform[slug].orEmpty()
                coroutineScope {
                    paths.map { path ->
                        async(workers) {
                            val made = if (background) {
                                // skipped or quiet: one at a time, with breathing room for the app
                                gate.withPermit { CocoonThumbs.make(app, path).also { delay(30) } }
                            } else {
                                CocoonThumbs.make(app, path)
                            }
                            if (!made) failedNow += CocoonThumbs.keyFor(path)
                            report(CocoonOptimizeStep.COVERS, doneCount.incrementAndGet(), total)
                        }
                    }.awaitAll()
                }
                _state.update { it.copy(readyPlatforms = it.readyPlatforms + slug) }
            }
            if (failedNow.isNotEmpty()) prefs.edit().putStringSet("failed", failed + failedNow).apply()
            finish(CocoonOptimizeStep.COVERS)
        }

        // 2. Folder art from the theme and icon packs, and the wallpaper. Every folder the board
        // can show (each platform in the library, the smart folders, Apps and Emulators): its
        // board icon at the size the board draws it, and the logo the top screen shows, so the
        // first highlight of each folder neither waits nor pops its logo in.
        run {
            val loader = coil.Coil.imageLoader(app)
            val engine = com.nendo.argosy.ui.cocoon.theme.CocoonThemeEngine
            val folders: List<Pair<CocoonGlyph, String?>> =
                covers.map { it.first }.distinct().map { CocoonGlyph.PLATFORM to it } +
                    CocoonSmartKind.entries.map { CocoonGlyph.SMART to it.name } +
                    listOf(CocoonGlyph.APPS to null, CocoonGlyph.EMULATORS to null)
            // (picture, size it is decoded at)
            val arts = ArrayList<Pair<Any, Int>>()
            // The folders with the most games (the first ones on the board) also get their
            // full-screen top-screen picture decoded; the rest are only looked up, as they are large.
            val heroWarm = platforms.take(FOLDER_HERO_WARMUP).toSet()
            folders.forEach { (glyph, slug) ->
                runCatching { engine.folderArt(glyph, slug, "icon") }.getOrNull()?.let { arts += it to FOLDER_ICON_PX }
                runCatching { engine.folderArt(glyph, slug, "logo") }.getOrNull()?.let { arts += it to FOLDER_LOGO_PX }
                val hero = runCatching { engine.folderArt(glyph, slug, "hero") }.getOrNull()
                if (hero != null && glyph == CocoonGlyph.PLATFORM && slug in heroWarm) arts += hero to FOLDER_HERO_PX
            }
            val wallpaper = engine.active.value?.wallpaperMain
                ?.takeIf { it.extension.lowercase() in setOf("png", "jpg", "jpeg", "webp", "gif") }
            if (wallpaper != null) arts += wallpaper to FOLDER_ICON_PX
            arts.forEachIndexed { i, (data, px) ->
                runCatching {
                    loader.execute(coil.request.ImageRequest.Builder(app).data(data).size(px).build())
                }
                report(CocoonOptimizeStep.ICONS, i + 1, arts.size)
            }
            finish(CocoonOptimizeStep.ICONS)
        }

        // 2b. App icons for the dock and All Apps.
        warmAppIcons(app) { done, total -> report(CocoonOptimizeStep.APPS, done, total) }
        finish(CocoonOptimizeStep.APPS)

        // 2c. Database tune-up: SQLite re-learns the library's shape so folder and sort lookups
        // pick the fastest route (saved in the database, so it lasts).
        runCatching {
            val db = entry.database().openHelper.writableDatabase
            db.execSQL("ANALYZE")
            db.query("PRAGMA optimize").use { it.moveToFirst() }
        }
        finish(CocoonOptimizeStep.DATABASE)

        // 3. The library: recent games and their covers, so the first screens open instantly.
        run {
            val recent = runCatching { entry.gameDao().cocoonRecentlyPlayedIds(24) }.getOrDefault(emptyList())
            val loader = coil.Coil.imageLoader(app)
            recent.forEachIndexed { i, id ->
                val path = runCatching { icm.loadArt(id) }.getOrNull()?.coverPath
                CocoonThumbs.model(app, path)?.let { data ->
                    runCatching {
                        // the same request a folder tile makes (size and trim), so the folder reuses it
                        loader.execute(
                            coil.request.ImageRequest.Builder(app)
                                .data(data)
                                .size(COCOON_COVER_DECODE_PX)
                                .transformations(CocoonCoverTrim)
                                .build()
                        )
                    }
                }
                report(CocoonOptimizeStep.LIBRARY, i + 1, recent.size)
            }
            // The most recent games' backgrounds and logos too, decoded at full size, so the top
            // screen and their game pages show them the first time without loading.
            recent.take(RECENT_ART_WARMUP).forEach { id ->
                val art = runCatching { icm.loadArt(id) }.getOrNull() ?: return@forEach
                listOfNotNull(art.backgroundPath, art.logoPath)
                    .filter { it.startsWith("/") && !it.endsWith(".webp") && !it.endsWith(".gif") }
                    .forEach { path ->
                        runCatching {
                            loader.execute(
                                coil.request.ImageRequest.Builder(app)
                                    .data(File(path))
                                    .size(coil.size.Size.ORIGINAL)
                                    .build()
                            )
                        }
                    }
            }
            finish(CocoonOptimizeStep.LIBRARY)
        }

        // 3b. Warm-up: load the code of the main screens, and (while the intro is up) draw the
        // app's effects once behind it, so the first real visit doesn't hitch. Android keeps the
        // prepared graphics between launches.
        run {
            val loader = app.classLoader
            val names = WARM_CLASSES
            names.forEachIndexed { i, name ->
                runCatching { Class.forName(name, false, loader) }
                report(CocoonOptimizeStep.WARMUP, i + 1, names.size + 1)
            }
            // the R1 / L1 quick menu, built once out of sight
            prewarmQuickPanel(entry)
            if (_state.value.introVisible) {
                _state.update { it.copy(warmingUp = true) }
                delay(1400)
                _state.update { it.copy(warmingUp = false) }
            }
            finish(CocoonOptimizeStep.WARMUP)
        }

        // 4. Ahead-of-time compile (release builds only).
        if (needCompile) {
            prefs.edit().putLong("compiled_for", installStamp).commit()
            report(CocoonOptimizeStep.COMPILE, 0, 0)
            runCatching {
                com.nendo.argosy.util.RootShell.execute("cmd package compile -m speed -f ${app.packageName}")
            }
            finish(CocoonOptimizeStep.COMPILE)
        }

        // 5. Tidy: thumbnails of covers that no longer exist.
        runCatching { CocoonThumbs.tidy(app, covers.map { it.second }) }
        finish(CocoonOptimizeStep.TIDY)

        prefs.edit().putLong("install", installStamp).apply()
        _state.update { it.copy(running = false, finished = true, progress = 1f, readyPlatforms = it.platforms.toSet()) }
        if (_state.value.introVisible) {
            delay(1100) // "All set" for a moment
            _state.update { it.copy(introVisible = false) }
        }
    }
}
