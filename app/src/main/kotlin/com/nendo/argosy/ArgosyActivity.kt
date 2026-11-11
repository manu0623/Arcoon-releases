package com.nendo.argosy

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nendo.argosy.ui.dualscreen.onDisplay
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nendo.argosy.core.input.ControllerPointerGuard
import com.nendo.argosy.core.input.controllerTouchpadGuarding
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.local.dao.DownloadQueueDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.wallpaper.LockScreenScenes
import com.nendo.argosy.data.repository.CollectionRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.hardware.AmbientLedContext
import com.nendo.argosy.hardware.AmbientLedManager
import com.nendo.argosy.hardware.ScreenCaptureManager
import com.nendo.argosy.ui.ArgosyApp
import com.nendo.argosy.ui.audio.AmbientAudioManager
import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.input.GamepadInputHandler
import com.nendo.argosy.ui.input.gamepadEventToKeyCode
import com.nendo.argosy.ui.screens.common.GameActionsDelegate
import com.nendo.argosy.ui.theme.ALauncherTheme
import android.view.Display
import com.nendo.argosy.hardware.SecondaryHomeActivity
import com.nendo.argosy.util.DesktopMode
import com.nendo.argosy.util.DisplayAffinityHelper
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.LauncherSystemBars
import com.nendo.argosy.util.installImmersiveMode
import dagger.hilt.android.AndroidEntryPoint
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "ArgosyActivity"
private const val AUDIO_FOCUS_WINDOW = "main"
private val LAUNCH_EXTRA_KEYS = listOf("path", "romm_id", "game_id", "channel")
internal fun shouldInitializeScreenCapture(prefs: UserPreferences): Boolean =
    prefs.ambientLedEnabled && prefs.ambientLedScreenEnabled

internal enum class NonDefaultDisplayLaunch { RUN_HERE, YIELD, HAND_TO_COMPANION }

internal fun nonDefaultDisplayLaunch(
    desktopMode: Boolean,
    dualScreenEnabled: Boolean,
    docked: Boolean
): NonDefaultDisplayLaunch = when {
    desktopMode -> NonDefaultDisplayLaunch.RUN_HERE
    !docked && !dualScreenEnabled -> NonDefaultDisplayLaunch.YIELD
    else -> NonDefaultDisplayLaunch.HAND_TO_COMPANION
}

@AndroidEntryPoint
class ArgosyActivity : ComponentActivity() {

    private var languageTag: String = com.nendo.argosy.core.locale.LocaleHelper.SYSTEM_LANGUAGE_TAG

    override fun attachBaseContext(newBase: android.content.Context) {
        languageTag = com.nendo.argosy.data.preferences.SessionStateStore(newBase).getAppLanguage()
        super.attachBaseContext(
            com.nendo.argosy.core.locale.LocaleHelper.wrap(newBase, languageTag)
        )
    }

    override fun applyOverrideConfiguration(overrideConfiguration: android.content.res.Configuration?) {
        super.applyOverrideConfiguration(
            com.nendo.argosy.core.locale.LocaleHelper.overrideConfiguration(
                overrideConfiguration,
                languageTag
            )
        )
    }

    @Inject lateinit var gameDao: GameDao
    @Inject lateinit var gameRepository: com.nendo.argosy.data.repository.GameRepository
    @Inject lateinit var activeSaveRepository: com.nendo.argosy.data.repository.ActiveSaveRepository
    @Inject lateinit var configureEmulatorUseCase: com.nendo.argosy.domain.usecase.game.ConfigureEmulatorUseCase
    @Inject lateinit var platformRepository: PlatformRepository
    @Inject lateinit var collectionRepository: CollectionRepository
    @Inject lateinit var socialRepositoryForDual: com.nendo.argosy.data.social.SocialRepository
    @Inject lateinit var downloadQueueDao: DownloadQueueDao
    @Inject lateinit var downloadQueueRepository: com.nendo.argosy.data.repository.DownloadQueueRepository
    @Inject lateinit var gamepadInputHandler: GamepadInputHandler
    @Inject lateinit var quickPanelOverlay: com.nendo.argosy.ui.quickpanel.QuickPanelOverlay
    @Inject lateinit var connectedControllerTracker: com.nendo.argosy.core.input.ConnectedControllerTracker
    @Inject lateinit var triggerAxisKeyEmitter: com.nendo.argosy.ui.input.TriggerAxisKeyEmitter
    @Inject lateinit var imageCacheManager: ImageCacheManager
    @Inject lateinit var resolveGameEmulatorContext:
        com.nendo.argosy.domain.usecase.emulator.ResolveGameEmulatorContextUseCase
    @Inject lateinit var hapticFeedbackManager: com.nendo.argosy.ui.input.HapticFeedbackManager
    @Inject lateinit var soundFeedbackManager: com.nendo.argosy.ui.input.SoundFeedbackManager
    @Inject lateinit var androidGameScanner: com.nendo.argosy.data.scanner.AndroidGameScanner
    @Inject lateinit var gameNativeStoreSync: com.nendo.argosy.data.launcher.GameNativeStoreSync
    @Inject lateinit var romMRepository: RomMRepository
    @Inject lateinit var gameDocumentLoader: com.nendo.argosy.data.repository.GameDocumentLoader
    @Inject lateinit var documentHighlightStore: com.nendo.argosy.data.repository.DocumentHighlightStore
    @Inject lateinit var steamLibraryRepair: com.nendo.argosy.data.steam.SteamLibraryRepair
    @Inject lateinit var jellyfinConnectionManager: com.nendo.argosy.data.remote.jellyfin.JellyfinConnectionManager
    @Inject lateinit var preferencesRepository: UserPreferencesRepository
    @Inject lateinit var syncPreferencesRepository: com.nendo.argosy.data.preferences.SyncPreferencesRepository
    @Inject lateinit var displayPreferencesRepository: com.nendo.argosy.data.preferences.DisplayPreferencesRepository
    @Inject lateinit var homeTileRepository: com.nendo.argosy.data.repository.HomeTileRepository
    @Inject lateinit var homeTilePromptQueue: com.nendo.argosy.data.repository.HomeTilePromptQueue
    @Inject lateinit var appsRepository: com.nendo.argosy.data.repository.AppsRepository
    @Inject lateinit var appShortcutActions: com.nendo.argosy.ui.screens.common.AppShortcutActions
    @Inject lateinit var ambientAudioManager: AmbientAudioManager
    @Inject lateinit var bgmPlaylistCoordinator: com.nendo.argosy.ui.audio.BgmPlaylistCoordinator
    @Inject lateinit var ambientLedManager: AmbientLedManager
    @Inject lateinit var screenCaptureManager: ScreenCaptureManager
    @Inject lateinit var displayAffinityHelper: DisplayAffinityHelper
    @Inject lateinit var permissionHelper: com.nendo.argosy.util.PermissionHelper
    @Inject lateinit var gameActionsDelegate: GameActionsDelegate
    @Inject lateinit var platformSyncQueue: com.nendo.argosy.data.sync.PlatformSyncQueue
    @Inject lateinit var gameThemeAudioCoordinator: com.nendo.argosy.ui.audio.GameThemeAudioCoordinator
    @Inject lateinit var getPinnedCollectionsUseCase: com.nendo.argosy.domain.usecase.collection.GetPinnedCollectionsUseCase
    @Inject lateinit var advanceCollectionFocusUseCase: com.nendo.argosy.domain.usecase.collection.AdvanceCollectionFocusUseCase
    @Inject lateinit var prepareCollectionQueueUseCase: com.nendo.argosy.domain.usecase.collection.PrepareCollectionQueueUseCase
    @Inject lateinit var getGamesForPinnedCollectionUseCase: com.nendo.argosy.domain.usecase.collection.GetGamesForPinnedCollectionUseCase
    @Inject lateinit var sessionEndCoordinator: com.nendo.argosy.ui.screens.common.SessionEndCoordinator
    @Inject lateinit var saveCacheManager: SaveCacheManager
    @Inject lateinit var prefetchGameSaveDataUseCase:
        com.nendo.argosy.domain.usecase.sync.PrefetchGameSaveDataUseCase
    @Inject lateinit var raRepository: com.nendo.argosy.data.repository.RetroAchievementsRepository
    @Inject lateinit var raTileContentRepository: com.nendo.argosy.data.repository.RaTileContentRepository
    @Inject lateinit var achievementUpdateBus: com.nendo.argosy.core.event.AchievementUpdateBus
    @Inject lateinit var gameFileDao: com.nendo.argosy.data.local.dao.GameFileDao
    @Inject lateinit var downloadManagerInstance: com.nendo.argosy.data.download.DownloadManager
    @Inject lateinit var notificationManager: com.nendo.argosy.core.notification.NotificationManager
    @Inject lateinit var titleIdDownloadObserver: com.nendo.argosy.data.emulator.TitleIdDownloadObserver
    @Inject lateinit var homeGridPageRepository: com.nendo.argosy.data.repository.HomeGridPageRepository
    @Inject lateinit var pageChooserEntrySource: com.nendo.argosy.ui.home.grid.PageChooserEntrySource
    @Inject lateinit var emulatorConfigDao: com.nendo.argosy.data.local.dao.EmulatorConfigDao
    @Inject lateinit var builtinCoreResolver: com.nendo.argosy.data.emulator.BuiltinCoreResolver
    @Inject lateinit var saveHandlerRegistry: com.nendo.argosy.data.sync.platform.PlatformSaveHandlerRegistry
    @Inject lateinit var steamRepository: com.nendo.argosy.data.repository.SteamRepository
    @Inject lateinit var playSessionTracker: com.nendo.argosy.data.emulator.PlaySessionTracker
    @Inject lateinit var repairImageCacheUseCase: com.nendo.argosy.domain.usecase.cache.RepairImageCacheUseCase
    @Inject lateinit var downloadFileStatusRepository: com.nendo.argosy.data.repository.DownloadFileStatusRepository
    @Inject lateinit var presenceManager: com.nendo.argosy.data.social.PresenceManager
    @Inject lateinit var discordPresenceManager: com.nendo.argosy.data.social.discord.DiscordPresenceManager
    @Inject lateinit var gradientExtractionDelegate: com.nendo.argosy.ui.screens.common.GradientExtractionDelegate
    @Inject lateinit var filePickerFlowUseCase: com.nendo.argosy.domain.usecase.download.FilePickerFlowUseCase
    @Inject lateinit var mediaRepository: com.nendo.argosy.data.repository.MediaRepository
    @Inject lateinit var getRelatedMediaUseCase:
        com.nendo.argosy.domain.usecase.media.GetRelatedMediaUseCase
    @Inject lateinit var resolveMediaPlayTargetUseCase:
        com.nendo.argosy.domain.usecase.media.ResolveMediaPlayTargetUseCase
    @Inject lateinit var mediaPlaybackTracker: com.nendo.argosy.data.media.MediaPlaybackTracker
    @Inject lateinit var launchProgressTracker: com.nendo.argosy.data.emulator.LaunchProgressTracker
    @Inject lateinit var mediaAvailabilityVerifier: com.nendo.argosy.data.media.MediaAvailabilityVerifier
    @Inject lateinit var mediaDownloadDelegate:
        com.nendo.argosy.ui.screens.media.delegates.MediaDownloadDelegate
    @Inject lateinit var mediaSeriesDelegate:
        com.nendo.argosy.ui.screens.media.delegates.MediaSeriesDelegate
    @Inject lateinit var mediaSiblingsDelegate:
        com.nendo.argosy.ui.screens.media.delegates.MediaSiblingsDelegate
    @Inject lateinit var gameSessionManager: com.nendo.argosy.data.emulator.GameSessionManager

    private val sessionStateStore by lazy {
        com.nendo.argosy.data.preferences.SessionStateStore(this)
    }
    private val activityScope = SafeCoroutineScope(Dispatchers.Main, "ArgosyActivity")
    private val systemBars by lazy { LauncherSystemBars(window) }

    private lateinit var preferencesObserver: MainActivityPreferencesObserver


    lateinit var dualScreenManager: DualScreenManager
        private set

    var isOnHomeScreen = false

    var isOverlayFocused: Boolean
        get() = dualScreenManager.isOverlayFocused
        set(value) {
            dualScreenManager.isOverlayFocused = value
        }

    private var screenCapturePromptedThisSession = false
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        screenCaptureManager.onPermissionResult(result.resultCode, result.data)
        if (screenCaptureManager.hasPermission.value) {
            screenCaptureManager.startCapture()
        }
    }

    fun requestScreenCapturePermission() {
        screenCaptureManager.requestPermission(this, screenCaptureLauncher)
    }

    private var hasResumedBefore = false
    private var yieldedFocusToGame = false
    private var attachedToDsmHolder = false
    private val pointerGuard = ControllerPointerGuard(
        windowName = "launcher",
        scope = lifecycleScope,
        setWindowTouchable = ::setWindowTouchable
    )

    @SuppressLint("NewApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        val createStarted = android.os.SystemClock.uptimeMillis()
        super.onCreate(savedInstanceState)
        // No task screenshot: coming back, Android would show the last one (an earlier suspend's
        // picture at full size) before the launcher draws the new one.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(false)
        }
        com.nendo.argosy.ui.cocoon.theme.CocoonThemeEngine.init(this)
        com.nendo.argosy.ui.cocoon.CocoonAppStore.init(this)
        com.nendo.argosy.ui.cocoon.CocoonHomeStore.load(this)

        val desktopMode = DesktopMode.isActive(resources.configuration)
        val ownDisplayId = display?.displayId ?: Display.DEFAULT_DISPLAY
        if (ownDisplayId != Display.DEFAULT_DISPLAY) {
            val launch = nonDefaultDisplayLaunch(
                desktopMode = desktopMode,
                dualScreenEnabled = sessionStateStore.isDualScreenEnabled(),
                docked = !desktopMode && DisplayAffinityHelper.dockedExternalDisplayId(this) != null
            )
            if (launch != NonDefaultDisplayLaunch.RUN_HERE) forwardDeepLinkToDefaultDisplay()
            if (launch == NonDefaultDisplayLaunch.YIELD) {
                Log.d(TAG, "ArgosyActivity on non-default display with dual-screen disabled, finishing")
                finish()
                return
            }
            if (launch == NonDefaultDisplayLaunch.HAND_TO_COMPANION) {
                handDisplayToCompanion(ownDisplayId)
                return
            }
        }
        val desktopModeChanged = recordDesktopMode(desktopMode, ownDisplayId)

        installImmersiveMode { systemBars.visibility }
        systemBars.follow(activityScope, preferencesRepository.userPreferences)
        keepAwakeWhileUserActive()
        dimWhileMediaIdle()

        discordPresenceManager.init(this)

        displayAffinityHelper.dualScreenEnabled = sessionStateStore.isDualScreenEnabled()
        displayAffinityHelper.secondaryDisplayUsable = sessionStateStore.isSecondaryDisplayUsable()

        val existingDsm = DualScreenManagerHolder.instance
        if (existingDsm != null) {
            dualScreenManager = existingDsm
            dualScreenManager.rebind(this, activityScope)
            if (desktopModeChanged) dualScreenManager.onDesktopModeChanged()
        } else {
            dualScreenManager = DualScreenManager(
                context = this,
                scope = activityScope,
                gameDao = gameDao,
                gameRepository = gameRepository,
                activeSaveRepository = activeSaveRepository,
                platformRepository = platformRepository,
                collectionRepository = collectionRepository,
                socialRepository = socialRepositoryForDual,
                downloadQueueDao = downloadQueueDao,
                downloadQueueRepository = downloadQueueRepository,
                gameFileDao = gameFileDao,
                downloadManager = downloadManagerInstance,
                gameActionsDelegate = gameActionsDelegate,
                platformSyncQueue = platformSyncQueue,
                sessionEndCoordinator = sessionEndCoordinator,
                saveCacheManager = saveCacheManager,
                prefetchGameSaveDataUseCase = prefetchGameSaveDataUseCase,
                raRepository = raRepository,
                raTileContentRepository = raTileContentRepository,
                achievementUpdateBus = achievementUpdateBus,
                displayAffinityHelper = displayAffinityHelper,
                sessionStateStore = sessionStateStore,
                preferencesRepository = preferencesRepository,
                syncPreferencesRepository = syncPreferencesRepository,
                homeTileRepository = homeTileRepository,
                homeTilePromptQueue = homeTilePromptQueue,
                appsRepository = appsRepository,
                appShortcutActions = appShortcutActions,
                notificationManager = notificationManager,
                titleIdDownloadObserver = titleIdDownloadObserver,
                homeGridPageRepository = homeGridPageRepository,
                pageChooserEntrySource = pageChooserEntrySource,
                ambientAudioManager = ambientAudioManager,
                emulatorConfigDao = emulatorConfigDao,
                configureEmulatorUseCase = configureEmulatorUseCase,
                builtinCoreResolver = builtinCoreResolver,
                saveHandlerRegistry = saveHandlerRegistry,
                steamRepository = steamRepository,
                playSessionTracker = playSessionTracker,
                gameSessionManager = gameSessionManager,
                permissionHelper = permissionHelper,
                repairImageCacheUseCase = repairImageCacheUseCase,
                downloadFileStatusRepository = downloadFileStatusRepository,
                gradientExtractionDelegate = gradientExtractionDelegate,
                filePickerFlow = filePickerFlowUseCase,
                gameThemeAudioCoordinator = gameThemeAudioCoordinator,
                getPinnedCollectionsUseCase = getPinnedCollectionsUseCase,
                getGamesForPinnedCollectionUseCase = getGamesForPinnedCollectionUseCase,
                advanceCollectionFocusUseCase = advanceCollectionFocusUseCase,
                prepareCollectionQueueUseCase = prepareCollectionQueueUseCase,
                mediaRepository = mediaRepository,
                getRelatedMediaUseCase = getRelatedMediaUseCase,
                resolveMediaPlayTargetUseCase = resolveMediaPlayTargetUseCase,
                mediaPlaybackTracker = mediaPlaybackTracker,
                launchProgressTracker = launchProgressTracker,
                mediaAvailabilityVerifier = mediaAvailabilityVerifier,
                mediaDownloadDelegate = mediaDownloadDelegate,
                mediaSeriesDelegate = mediaSeriesDelegate,
                mediaSiblingsDelegate = mediaSiblingsDelegate,
                imageCacheManager = imageCacheManager,
                romMRepository = romMRepository,
                gameDocumentLoader = gameDocumentLoader,
                documentHighlightStore = documentHighlightStore,
                resolveGameEmulatorContext = resolveGameEmulatorContext,
                hapticManager = hapticFeedbackManager,
                soundManager = soundFeedbackManager,
                initialRolesSwapped = sessionStateStore.isRolesSwapped()
            )
            DualScreenManagerHolder.instance = dualScreenManager
        }
        DualScreenManagerHolder.attach()
        attachedToDsmHolder = true

        dualScreenManager.onRoleSwapped = { swapped ->
            if (swapped && !dualScreenManager.isMovingGame) refocusSelf()
        }
        dualScreenManager.registerReceivers()
        if (savedInstanceState == null) dualScreenManager.releaseForeignAppHoldOnLauncherStart()
        dualScreenManager.ensureCompanionLaunched()
        dualScreenManager.startStartupGuard()
        dualScreenManager.applyStoredScreenLayout()
        initCacheAndPreferences()
        activityScope.launch { jellyfinConnectionManager.initialize() }


        com.nendo.argosy.data.sync.AchievementSubmissionWorker.schedule(this)

        preferencesObserver = MainActivityPreferencesObserver(
            preferencesRepository = preferencesRepository,
            ambientAudioManager = ambientAudioManager,
            bgmPlaylistCoordinator = bgmPlaylistCoordinator,
            sessionStateStore = sessionStateStore,
            dualScreenManager = dualScreenManager,
            displayAffinityHelper = displayAffinityHelper,
            screenCaptureManager = screenCaptureManager,
            onDualScreenChanged = { dualScreenManager.setDualScreenDevice(it) },
            hasWindowFocus = ::hasWindowFocus,
            appContext = applicationContext
        )
        preferencesObserver.collectIn(activityScope)
        revealCallerWhenExternalSessionCloses()
        pointerGuard.follow(controllerTouchpadGuarding(playSessionTracker, preferencesRepository))

        com.nendo.argosy.util.ArcoonTrace.log(
            "startup: main screen set up in ${android.os.SystemClock.uptimeMillis() - createStarted} ms before its first draw"
        )
        com.nendo.argosy.util.FrameWatch.attach(this)
        window.decorView.post {
            com.nendo.argosy.util.ArcoonTrace.log(
                "startup: main screen first drawn ${android.os.SystemClock.uptimeMillis() - createStarted} ms after it was created"
            )
            // the screen is up: now the app's background services can be built and started
            (application as? ArgosyApp)?.onFirstScreenDrawn()
        }
        setContent {
            ALauncherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = com.nendo.argosy.ui.theme.CocoonSurface.Background
                ) {
                    // The lower screen is pure black, as Cocoon draws it for OLED panels.
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        dualScreenManager.localeChangeToken.drop(1).collect { recreate() }
                    }
                    val companionHoldsPrimary =
                        dualScreenManager.companionHoldsPrimary.collectAsState()
                    val rolesSwapped = dualScreenManager.isRolesSwapped.collectAsState()
                    val gameActive = dualScreenManager.gameOnScreen.collectAsState()
                    val gameDisplay = dualScreenManager.emulatorDisplay.collectAsState()
                    // Lifted Settings floats over the showcase picture instead of replacing it.
                    val settingsLifted = dualScreenManager.settingsLift.collectAsState().value != null
                    val companionHostsLauncher = dualScreenManager.companionHostsLauncher.collectAsState()
                    val companionScreenUp = dualScreenManager.companionScreenUp.collectAsState()
                    val showsPresentation = rendersPresentation(
                        companionHoldsPrimary.value || companionHostsLauncher.value,
                        gameActive.value,
                        gameDisplay.value
                    )
                    androidx.compose.runtime.CompositionLocalProvider(
                        com.nendo.argosy.ui.theme.LocalMotionTier provides com.nendo.argosy.ui.cocoon.cocoonMotionTier()
                    ) {
                    androidx.compose.runtime.LaunchedEffect(showsPresentation) {
                        com.nendo.argosy.util.ArcoonTrace.log(
                            "main window (display ${window.decorView.display?.displayId}) shows ${if (showsPresentation) "presentation" else "launcher"}"
                        )
                    }
                    // Settings coming up here (or leaving) crossfades with the showcase picture; a
                    // screen swap keeps its own slide with no fade under it.
                    val swapping = com.nendo.argosy.ui.cocoon.cocoonSwapRecent()
                    com.nendo.argosy.ui.cocoon.CocoonSwapSlide(key = rolesSwapped.value) { frameKey ->
                    /*
                     * The copy sliding away keeps the branch it was showing. Both copies render the
                     * live branch otherwise, which composed the whole launcher twice in one frame
                     * (the lag and the stutter) and stripped the leaving picture before the slide
                     * covered it (the dock breaking). Inverting is only right for the
                     * launcher/presentation pair, and a running game picks its own branch.
                     */
                    val swappedHere = frameKey as? Boolean ?: rolesSwapped.value
                    /*
                     * The branch for this copy's own arrangement, not for the live one:
                     * companionHoldsPrimary and companionHostsLauncher both reduce to `!swapped &&
                     * the companion is up`, so asking rendersPresentation with this copy's key
                     * gives the picture that arrangement shows. A live flag lands a few frames
                     * after the role does, and reading it let a running slide change picture
                     * 293 ms in: a second board inside one swap (the flash), and both panels
                     * settling on the same role (the replication).
                     */
                    val presentingHere = rendersPresentation(
                        companionScreenUp.value && !swappedHere,
                        gameActive.value,
                        gameDisplay.value
                    )
                    androidx.compose.animation.Crossfade(
                        targetState = presentingHere,
                        animationSpec = if (swapping) androidx.compose.animation.core.snap()
                        else androidx.compose.animation.core.tween(SETTINGS_LIFT_FADE_MS),
                        label = "main-window-role"
                    ) { presenting ->
                    androidx.compose.runtime.LaunchedEffect(presenting) {
                        com.nendo.argosy.util.ArcoonTrace.log(
                            "main window branch composed presenting=$presenting swappedHere=$swappedHere " +
                                "live=${rolesSwapped.value} swapping=$swapping " +
                                "display=${window.decorView.display?.displayId}"
                        )
                    }
                    if (presenting) {
                        val slot by dualScreenManager.presentationSlot.collectAsState()
                        androidx.compose.runtime.LaunchedEffect(slot::class) {
                            com.nendo.argosy.util.ArcoonTrace.log("main window presentation slot: ${slot::class.simpleName}")
                        }
                        val presentationSink = androidx.compose.runtime.remember {
                            FocusRequester()
                        }
                        androidx.compose.runtime.LaunchedEffect(Unit) {
                            // Rebuilt by a role swap: the node can still be unattached here.
                            runCatching { presentationSink.requestFocus() }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.systemBars)
                                .focusRequester(presentationSink)
                                .focusable()
                        ) {
                            val preferences = dualScreenManager.preferencesRepository.userPreferences
                            com.nendo.argosy.ui.input.ProvideButtonGlyphs(
                                preferences,
                                connectedControllerTracker.activeLayout
                            ) {
                                com.nendo.argosy.ui.components.ProvideStatusBarItems(preferences) {
                                    val ownDisplay = androidx.compose.ui.platform.LocalView.current.display?.displayId
                                    com.nendo.argosy.ui.dualscreen.PresentationSlotContent(
                                        slot.onDisplay(ownDisplay, gameDisplay.value),
                                        showsNotifications = true
                                    )
                                }
                            }
                        }
                    } else {
                        // A theme's bottom-screen wallpaper sits behind the whole app.
                        com.nendo.argosy.ui.cocoon.theme.CocoonThemeEngine.active.value?.wallpaperMain?.let {
                            com.nendo.argosy.ui.cocoon.theme.CocoonThemeWallpaper(it)
                        }
                        // One sliding Cocoon cursor for every menu, list and settings page in the app
                        // (screens that blur, like the board, keep their own).
                        com.nendo.argosy.ui.primitives.CocoonCursorHost(modifier = Modifier.fillMaxSize()) {
                            ArgosyApp(
                                onStartupComplete = { dualScreenManager.stopStartupGuard() },
                                // The right-hand panel belongs on the upper screen: it is the
                                // surface with room, and the board (with its left drawer) keeps
                                // the lower one. Falls back to drawing it here when there is no
                                // second screen or no overlay permission.
                                openQuickPanelOnTop = { owner, page -> quickPanelOverlay.showOnUpper(owner, page) },
                                hideQuickPanel = {
                                    com.nendo.argosy.util.ArcoonTrace.log("quick panel: hide")
                                    quickPanelOverlay.hideNow()
                                },
                                isQuickPanelOpen = { quickPanelOverlay.isOpen.value }
                            )
                        }
                    }
                    }
                    }
                    }
                    // The panel stays up after Settings closes until the lower screen's Home has
                    // put its own picture back here; leaving at once showed the Settings picture
                    // for a moment before Home's replaced it.
                    var settingsPanelUp by androidx.compose.runtime.remember {
                        androidx.compose.runtime.mutableStateOf(false)
                    }
                    androidx.compose.runtime.LaunchedEffect(settingsLifted) {
                        if (settingsLifted) {
                            settingsPanelUp = true
                        } else if (settingsPanelUp) {
                            kotlinx.coroutines.withTimeoutOrNull(SETTINGS_PANEL_HOLD_MS) {
                                dualScreenManager.presentationSlot.drop(1).first()
                            }
                            settingsPanelUp = false
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = settingsPanelUp && showsPresentation,
                        // At once: a fade let the Settings showcase meant for the lower screen
                        // show through up here for a moment.
                        enter = androidx.compose.animation.EnterTransition.None,
                        exit = androidx.compose.animation.fadeOut(
                            androidx.compose.animation.core.tween(SETTINGS_PANEL_EXIT_MS)
                        )
                    ) {
                        FloatingSettingsPanel()
                    }
                    // The optimization intro's top-screen half (logo and folders popping in).
                    if (showsPresentation) com.nendo.argosy.ui.cocoon.CocoonOptimizeTop()
                    // Black over whatever this window shows for a moment after Home leaves a game.
                    com.nendo.argosy.ui.cocoon.CocoonLeaveVeil()
                }
            }
        }

        // Debug hook: lets the upper-screen panel be checked without a controller in hand.
        if (intent?.getBooleanExtra("argosy.swap_roles", false) == true) {
            android.os.Handler(mainLooper).postDelayed({ dualScreenManager.swapRoles() }, 1200)
        }
        intent?.getStringExtra("argosy.ui_mode")?.let { requested ->
            android.os.Handler(mainLooper).postDelayed({
                com.nendo.argosy.ui.cocoon.CocoonMode.requestClassicHome(requested == "classic")
            }, 1500)
        }
        if (intent?.getBooleanExtra("argosy.quick_panel", false) == true) {
            android.os.Handler(mainLooper).postDelayed({ quickPanelOverlay.showOnUpper(null) }, 1500)
        }

        if (savedInstanceState == null) {
            handleDeepLink(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!handleDeepLink(intent)) {
            handleHomeIntent(intent)
        }
    }

    @SuppressLint("NewApi")
    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) pointerGuard.onInFrontChanged(true)
        systemBars.apply()
        if (::dualScreenManager.isInitialized) {
            dualScreenManager.notifyUserActivity("mainResume")
        }
        Log.d(TAG, "onResume: swapped=${dualScreenManager.isRolesSwapped.value} gameActive=${if (::dualScreenManager.isInitialized) dualScreenManager.swappedIsGameActive.value else "N/A"} hasResumedBefore=$hasResumedBefore")

        dualScreenManager.broadcastForegroundState(true)

        revalidateDownloadedFiles()

        // Suspending happens in the game itself when Home leaves it (see LibretroActivity); the
        // launcher resuming is not a signal to suspend anything, and must never stop a process.

        if (hasResumedBefore) {
            romMRepository.onAppResumed()
            activityScope.launch { romMRepository.initialize(reprobe = true) }
            activityScope.launch { jellyfinConnectionManager.initialize() }
            ambientAudioManager.fadeIn()
        } else {
            if (displayAffinityHelper.hasSecondaryDisplay && !dualScreenManager.isRolesSwapped.value) {
                window.decorView.postDelayed({
                    if (com.nendo.argosy.ui.dualscreen.companionTakesFocusOnFirstResume(
                            sessionActive = sessionStateStore.hasActiveSession(),
                            gameActive = dualScreenManager.swappedIsGameActive.value,
                            launchLive = launchProgressTracker.launchLive.value
                        )
                    ) {
                        dualScreenManager.controlCompanion?.refocusSelf()
                    }
                }, 500)
            }
        }
        hasResumedBefore = true
    }

    override fun onPause() {
        super.onPause()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) pointerGuard.onInFrontChanged(false)
        if (!ambientAudioManager.keepsPlayingAsleep()) ambientAudioManager.suspend()
        if (!dualScreenManager.isCompanionActive.value) {
            dualScreenManager.broadcastForegroundState(false)
        }
    }

    override fun onStop() {
        super.onStop()
        gamepadInputHandler.resetStickMotion()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::screenCaptureManager.isInitialized) screenCaptureManager.stopCapture()
        activityScope.cancel()
        val othersAttached = attachedToDsmHolder && DualScreenManagerHolder.detachAndCountRemaining() > 0
        if (isFinishing && ::dualScreenManager.isInitialized && !othersAttached) {
            dualScreenManager.unregisterReceivers()
            if (DualScreenManagerHolder.instance === dualScreenManager) {
                DualScreenManagerHolder.instance = null
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!::dualScreenManager.isInitialized) return
        val ownDisplayId = window.decorView.display?.displayId ?: Display.DEFAULT_DISPLAY
        if (recordDesktopMode(DesktopMode.isActive(newConfig), ownDisplayId)) {
            dualScreenManager.onDesktopModeChanged()
        }
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!dualScreenManager.claimInput(event)) return true
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            Logger.verbose(TAG) { "dispatchKeyEvent: key=${event.keyCode} isHome=$isOnHomeScreen swapped=${dualScreenManager.isRolesSwapped.value} gameOnSecondary=${dualScreenManager.swappedIsGameActive.value} companion=${dualScreenManager.isCompanionActive.value} overlay=$isOverlayFocused" }
        }

        if (dualScreenManager.swappedIsGameActive.value && !isOverlayFocused && isGameOnOtherDisplay()) {
            val emulatorDispatcher = dualScreenManager.emulatorKeyDispatcher
            if (emulatorDispatcher != null) {
                Logger.verbose(TAG) { "dispatchKeyEvent: FORWARDING key=${event.keyCode} to emulator" }
                return emulatorDispatcher(event)
            }
            return true
        }

        if (dualScreenManager.companionHoldsPrimaryNow() && !isOverlayFocused) {
            gamepadInputHandler.observeInput(event)
            if (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    Logger.verbose(TAG) { "dispatchKeyEvent: FORWARDING key=${event.keyCode} to companion" }
                }
                dualScreenManager.controlCompanion?.onForwardKey(
                    event.keyCode,
                    event.action,
                    event.repeatCount,
                    sessionStateStore.getSwapAB(),
                    sessionStateStore.getSwapXY(),
                    sessionStateStore.getSwapStartSelect()
                )
            }
            return true
        }

        if (!dualScreenManager.isRolesSwapped.value &&
            !isOverlayFocused &&
            !dualScreenManager.isCompanionActive.value &&
            !dualScreenManager.swappedIsGameActive.value &&
            !sessionStateStore.isForeignAppOnSecondary() &&
            displayAffinityHelper.hasSecondaryDisplay
        ) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                Log.d(TAG, "dispatchKeyEvent: companion link stale, relinking key=${event.keyCode}")
                reassertCompanionForwarding()
            }
        }

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            Logger.verbose(TAG) { "dispatchKeyEvent: LOCAL handling key=${event.keyCode}" }
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            ambientAudioManager.resumeFromSuspend()
        }
        if (gamepadInputHandler.handleKeyEvent(event)) {
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_HOME || event.keyCode == KeyEvent.KEYCODE_BUTTON_MODE) {
            if (event.action == KeyEvent.ACTION_DOWN) gamepadInputHandler.emitHomeEvent()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private var mediaDimSwallowsTouch = false

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (pointerGuard.consumes(event)) return true
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val dimmed = isMediaDimmed()
            ambientAudioManager.resumeFromSuspend()
            if (::dualScreenManager.isInitialized) {
                dualScreenManager.notifyUserActivity("mainTouchDown")
            }
            if (dimmed) {
                mediaDimSwallowsTouch = true
                Logger.debug(
                    DualScreenManager.MEDIA_DIM_LOG_TAG,
                    "wakeTouch swallowed window=main brightness=${window.attributes.screenBrightness}"
                )
            }
        }
        if (mediaDimSwallowsTouch) {
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                mediaDimSwallowsTouch = false
            }
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    private fun isMediaDimmed(): Boolean =
        window.attributes.screenBrightness !=
            android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (pointerGuard.consumes(event)) return true
        triggerAxisKeyEmitter.emit(event).forEach { dispatchKeyEvent(it) }

        if (!dualScreenManager.claimInput(event)) return true
        if (dualScreenManager.swappedIsGameActive.value && !isOverlayFocused && isGameOnOtherDisplay()) {
            gamepadInputHandler.resetStickMotion()
            val emulatorDispatcher = dualScreenManager.emulatorMotionDispatcher
            if (emulatorDispatcher != null) {
                return emulatorDispatcher(event)
            }
            return true
        }

        val steerRightStick = !dualScreenManager.companionHoldsPrimaryNow() || isOverlayFocused
        if (gamepadInputHandler.processStickMotion(event, steerRightStick, ::deliverStickEvent)) return true
        if (gamepadInputHandler.handleMotionEvent(event)) return true
        return super.dispatchGenericMotionEvent(event)
    }

    private fun setWindowTouchable(touchable: Boolean) {
        val flag = android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (touchable) window.clearFlags(flag) else window.addFlags(flag)
    }

    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        pointerGuard.onInFrontChanged(isTopResumedActivity)
    }

    private fun deliverStickEvent(stickEvent: GamepadEvent, isRepeat: Boolean) {
        if (dualScreenManager.companionHoldsPrimaryNow() && !isOverlayFocused) {
            val keyCode = gamepadEventToKeyCode(stickEvent) ?: return
            dualScreenManager.controlCompanion?.onForwardKey(
                keyCode,
                KeyEvent.ACTION_DOWN,
                if (isRepeat) 1 else 0,
                sessionStateStore.getSwapAB(),
                sessionStateStore.getSwapXY(),
                sessionStateStore.getSwapStartSelect()
            )
            return
        }

        if (!dualScreenManager.isRolesSwapped.value &&
            isOnHomeScreen &&
            !isOverlayFocused &&
            !dualScreenManager.isCompanionActive.value &&
            !dualScreenManager.swappedIsGameActive.value &&
            !sessionStateStore.isForeignAppOnSecondary() &&
            displayAffinityHelper.hasSecondaryDisplay
        ) {
            if (!isRepeat) reassertCompanionForwarding()
            return
        }

        gamepadInputHandler.injectEvent(stickEvent, isRepeat)
    }

    @SuppressLint("NewApi")
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Log.d(TAG, "onWindowFocusChanged: hasFocus=$hasFocus swapped=${if (::dualScreenManager.isInitialized) dualScreenManager.isRolesSwapped.value else "N/A"} gameActive=${if (::dualScreenManager.isInitialized) dualScreenManager.swappedIsGameActive.value else "N/A"}")
        if (hasFocus) {
            if (yieldedFocusToGame) {
                yieldedFocusToGame = false
                reassertCompanionForwarding()
            }
            systemBars.apply()
            window.decorView.requestFocus()
            if (::dualScreenManager.isInitialized) dualScreenManager.onLauncherWindowFocused(this)
            ambientAudioManager.onLauncherWindowFocused(AUDIO_FOCUS_WINDOW)
            ambientAudioManager.fadeIn()
            ambientLedManager.setContext(AmbientLedContext.ARGOSY_UI)
            ambientLedManager.clearInGameColors()
            if (!isOverlayFocused) {
                gamepadInputHandler.blockInputFor(200)
            }
        } else {
            gamepadInputHandler.resetStickMotion()
            if (::dualScreenManager.isInitialized &&
                displayAffinityHelper.hasSecondaryDisplay &&
                !dualScreenManager.isRolesSwapped.value
            ) {
                yieldedFocusToGame = true
            }
            ambientAudioManager.onLauncherWindowUnfocused(AUDIO_FOCUS_WINDOW)
            ambientLedManager.setContext(AmbientLedContext.IN_GAME)
            if (::dualScreenManager.isInitialized) {
                val emulatorDisplay = dualScreenManager.emulatorDisplayId
                if (emulatorDisplay != null && emulatorDisplay != display?.displayId) {
                    dualScreenManager.restoreEmulatorFocus()
                }
            }
        }
    }

    private fun forwardDeepLinkToDefaultDisplay() {
        if (intent?.data?.scheme != "argosy") return
        val forwarded = Intent(intent).setClass(this, ArgosyActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = android.app.ActivityOptions.makeBasic()
            .setLaunchDisplayId(Display.DEFAULT_DISPLAY)
            .toBundle()
        startActivity(forwarded, options)
    }

    private fun handDisplayToCompanion(displayId: Int) {
        val companionIntent = Intent(this, SecondaryHomeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        val options = android.app.ActivityOptions.makeBasic()
            .setLaunchDisplayId(displayId)
            .toBundle()
        try {
            startActivity(companionIntent, options)
        } catch (e: android.content.ActivityNotFoundException) {
            Log.w(TAG, "Companion activity unavailable on this install, yielding display", e)
        }
        finish()
    }

    private fun recordDesktopMode(active: Boolean, ownDisplayId: Int): Boolean {
        val desktopDisplayId = if (active) ownDisplayId else null
        if (displayAffinityHelper.desktopModeDisplayId == desktopDisplayId) return false
        displayAffinityHelper.desktopModeDisplayId = desktopDisplayId
        return true
    }

    /**
     * Settings drawn as a floating panel over the upper screen's showcase picture, like Quick
     * Settings: the picture stays put underneath and the lower screen keeps its launcher.
     */
    @androidx.compose.runtime.Composable
    private fun FloatingSettingsPanel() {
        // Opening: the screen goes dark at once (so the showcase meant for the lower screen never
        // shows up here), then Settings enters as a folder's page does: it fades in while
        // settling a short way into place, and its rows arrive in a wave (CocoonPopulate).
        val opening = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
        androidx.compose.runtime.LaunchedEffect(Unit) {
            opening.animateTo(
                1f,
                androidx.compose.animation.core.tween(
                    SETTINGS_PANEL_OPEN_MS,
                    easing = com.nendo.argosy.ui.theme.Motion.argosyEase
                )
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black)
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = opening.value
                    alpha = p
                    translationY = -(1f - p) * SETTINGS_PANEL_RISE_DP * density
                }
                .background(com.nendo.argosy.ui.theme.CocoonSurface.Background)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Final)
                        }
                    }
                }
        ) {
            com.nendo.argosy.ui.cocoon.theme.CocoonThemeEngine.active.value?.wallpaperMain?.let {
                com.nendo.argosy.ui.cocoon.theme.CocoonThemeWallpaper(it)
            }
            com.nendo.argosy.ui.primitives.CocoonCursorHost(modifier = Modifier.fillMaxSize()) {
                ArgosyApp(
                    onStartupComplete = { dualScreenManager.stopStartupGuard() },
                    openQuickPanelOnTop = { owner, page -> quickPanelOverlay.showOnUpper(owner, page) },
                    hideQuickPanel = { quickPanelOverlay.hideNow() },
                    isQuickPanelOpen = { quickPanelOverlay.isOpen.value },
                    asLiftedPanel = true
                )
            }
        }
        }
    }

    private fun isGameOnOtherDisplay(): Boolean {
        val emulatorDisplay = dualScreenManager.emulatorDisplayId ?: return false
        val ownDisplay = window.decorView.display?.displayId ?: return false
        if (emulatorDisplay == ownDisplay) return false
        return rendersPresentation(
            dualScreenManager.companionHoldsPrimaryNow(),
            dualScreenManager.gameOnScreen.value,
            emulatorDisplay
        )
    }

    private fun rendersPresentation(
        companionHoldsPrimary: Boolean,
        gameActive: Boolean,
        gameDisplayId: Int?
    ): Boolean {
        if (companionHoldsPrimary) return true
        if (!gameActive) return false
        val ownDisplayId = window.decorView.display?.displayId
        val holdsPrimaryRole = dualScreenManager.interactiveDisplayId()?.let { it == ownDisplayId } ?: true
        return !holdsPrimaryRole || dualScreenManager.primaryShowsDashboard(ownDisplayId, gameDisplayId)
    }

    private fun reassertCompanionForwarding() {
        if (!::dualScreenManager.isInitialized) return
        if (!displayAffinityHelper.hasSecondaryDisplay) return
        if (dualScreenManager.isRolesSwapped.value) return
        if (dualScreenManager.swappedIsGameActive.value) return
        isOverlayFocused = false
        if (!dualScreenManager.isCompanionActive.value) {
            dualScreenManager.ensureCompanionLaunched()
            return
        }
        if (isOnHomeScreen) {
            dualScreenManager.controlCompanion?.refocusSelf()
        } else {
            dualScreenManager.onRefocusUpper()
        }
    }

    private fun revalidateDownloadedFiles() {
        activityScope.launch(Dispatchers.IO) {
            runCatching { gameRepository.validateLocalFiles() }
                .onFailure { Log.w(TAG, "revalidateDownloadedFiles failed", it) }
        }
    }

    private fun revealCallerWhenExternalSessionCloses() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                playSessionTracker.externalSessionClosed.collect {
                    playSessionTracker.pendingSessionConflict.first { it == null }
                    if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@collect
                    Log.d(TAG, "External session closed, moving task to back")
                    moveTaskToBack(true)
                }
            }
        }
    }

    private fun initCacheAndPreferences() {
        activityScope.launch {
            val prefs = preferencesRepository.preferences.first()
            displayAffinityHelper.dualScreenEnabled = prefs.dualScreenEnabled
            sessionStateStore.setDualScreenEnabled(prefs.dualScreenEnabled)
            sessionStateStore.setSaveSyncEnabled(prefs.saveSyncEnabled)
            dualScreenManager.setDualScreenDevice(displayAffinityHelper.hasSecondaryDisplay)
            imageCacheManager.setCustomCachePath(prefs.imageCachePath)

            if (imageCacheManager.needsLegacyCacheDirsMigration()) {
                Log.i(TAG, "Migrating cover cache out of purgeable cacheDir to persistent storage")
                imageCacheManager.migrateLegacyCacheDirs()
            }

            if (imageCacheManager.needsFlatToShardedMigration()) {
                Log.i(TAG, "Migrating flat image cache to sharded directories")
                imageCacheManager.migrateFlatToSharded()
            }

            imageCacheManager.resumePendingLogoCache()
            imageCacheManager.resumePendingBadgeCache()

            androidGameScanner.ensureAndroidPlatformExists()

            val relinked = androidGameScanner.relinkInstalledRommAndroidApps()
            if (relinked > 0) {
                Log.i(TAG, "Relinked $relinked installed RomM Android games to their packages")
            }
            androidGameScanner.refreshGameCount()

            val storeSync = gameNativeStoreSync.scan()
            if (storeSync.configured) {
                Log.i(TAG, "GameNative store sync: ${storeSync.results}")
            }
            lifecycleScope.launch { steamLibraryRepair.repairCovers() }

            launch(Dispatchers.IO) {
                val validationResult = imageCacheManager.validateAndCleanCache()
                if (validationResult.deletedFiles > 0 || validationResult.clearedPaths > 0) {
                    Log.i(TAG, "Cache validation: ${validationResult.deletedFiles} files deleted, ${validationResult.clearedPaths} paths cleared")
                }
                imageCacheManager.resumePendingArt(includeBoxFaces = prefs.boxArtCacheEnabled)
            }

            if (shouldInitializeScreenCapture(prefs)) {
                if (screenCaptureManager.hasPermission.value && !screenCaptureManager.isCapturing.value) {
                    screenCaptureManager.startCapture()
                } else if (!screenCaptureManager.hasPermission.value && !screenCapturePromptedThisSession) {
                    screenCapturePromptedThisSession = true
                    screenCaptureManager.requestPermission(
                        this@ArgosyActivity, screenCaptureLauncher
                    )
                }
            }

            if (shouldOfferLiveWallpaper(prefs)) {
                displayPreferencesRepository.setLiveWallpaperOffered()
                runCatching { startActivity(LockScreenScenes.pickerIntent(this@ArgosyActivity)) }
                    .onFailure { Log.w(TAG, "Live wallpaper picker unavailable: ${it.message}") }
            }
        }
    }

    private suspend fun shouldOfferLiveWallpaper(prefs: UserPreferences): Boolean =
        LockScreenScenes.canOffer(this, prefs.lockScreenArt) &&
            !LockScreenScenes.isLiveActive(this) &&
            !displayPreferencesRepository.isLiveWallpaperOffered()



    private fun handleDeepLink(intent: Intent): Boolean {
        val uri = intent.data ?: return false
        if (uri.scheme == "argosy") {
            val resolved = mergeLaunchExtras(uri, intent)
            Log.d(TAG, "Received deep link: $resolved")
            dualScreenManager.publishDeepLink(resolved)
            return true
        }
        return false
    }

    private fun mergeLaunchExtras(uri: android.net.Uri, intent: Intent): android.net.Uri {
        var builder: android.net.Uri.Builder? = null
        for (key in LAUNCH_EXTRA_KEYS) {
            if (uri.getQueryParameter(key) != null) continue
            val value = intent.getStringExtra(key)?.takeIf { it.isNotBlank() } ?: continue
            builder = (builder ?: uri.buildUpon()).appendQueryParameter(key, value)
        }
        return builder?.build() ?: uri
    }

    private fun handleHomeIntent(intent: Intent): Boolean {
        if (intent.hasCategory(Intent.CATEGORY_HOME) && hasResumedBefore) {
            gamepadInputHandler.emitHomeEvent()
            return true
        }
        return false
    }

    private fun keepAwakeWhileUserActive() {
        activityScope.launch {
            kotlinx.coroutines.flow.combine(
                dualScreenManager.userActive,
                dualScreenManager.mediaPlayback
            ) { active, playback -> active || playback != null }
                .collect { keepOn ->
                    if (keepOn) {
                        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
        }
    }

    private fun dimWhileMediaIdle() {
        activityScope.launch {
            kotlinx.coroutines.flow.combine(
                dualScreenManager.mediaPlayerDisplay,
                dualScreenManager.mediaDimBrightness,
                dualScreenManager.mediaDimCoverAlpha
            ) { playerDisplayId, dim, coverAlpha -> Triple(playerDisplayId, dim, coverAlpha) }
                .collect { (playerDisplayId, dim, coverAlpha) ->
                    applyMediaDim(playerDisplayId, dim, coverAlpha)
                }
        }
    }

    private fun applyMediaDim(playerDisplayId: Int?, dim: Float?, coverAlpha: Float) {
        val ownDisplayId = window.decorView.display?.displayId
        val resolvedPlayerId = playerDisplayId ?: dualScreenManager.mediaPlayerRelocationDisplayId()
        val isOtherDisplay = resolvedPlayerId != null &&
            ownDisplayId != null && ownDisplayId != resolvedPlayerId
        val dimsThisWindow = dim != null && isOtherDisplay
        logMediaDimDecline(dimsThisWindow, ownDisplayId, playerDisplayId, resolvedPlayerId, dim)
        val target = if (dimsThisWindow && dim != null) {
            dim
        } else {
            android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        applyMediaDimCover(if (isOtherDisplay) coverAlpha else 0f)
        val attributes = window.attributes
        if (attributes.screenBrightness == target) return
        Logger.debug(
            DualScreenManager.MEDIA_DIM_LOG_TAG,
            "apply window=main display=$ownDisplayId player=$resolvedPlayerId brightness=$target"
        )
        attributes.screenBrightness = target
        window.attributes = attributes
    }

    private var mediaDimDeclineKey: String? = null

    private fun logMediaDimDecline(
        dimsThisWindow: Boolean,
        ownDisplayId: Int?,
        reportedPlayerId: Int?,
        resolvedPlayerId: Int?,
        dim: Float?
    ) {
        val reason = when {
            dim == null || dimsThisWindow -> null
            ownDisplayId == null -> "ownDisplayUnknown"
            resolvedPlayerId == null -> "playerDisplayUnresolved"
            else -> "ownDisplayShowsPlayback"
        }
        val key = reason?.let { "$it dim=$dim" }
        if (key != null && key != mediaDimDeclineKey) {
            Logger.debug(
                DualScreenManager.MEDIA_DIM_LOG_TAG,
                "declined window=main display=$ownDisplayId playerReported=$reportedPlayerId " +
                    "playerResolved=$resolvedPlayerId brightness=$dim reason=$reason"
            )
        }
        mediaDimDeclineKey = key
    }

    private var mediaDimCover: android.graphics.drawable.ColorDrawable? = null

    private fun applyMediaDimCover(alpha: Float) {
        val alphaInt = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        if (alphaInt <= 0) {
            if (mediaDimCover != null) {
                mediaDimCover = null
                window.decorView.foreground = null
            }
            return
        }
        val cover = mediaDimCover
            ?: android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK).also {
                it.alpha = 0
                mediaDimCover = it
                window.decorView.foreground = it
            }
        cover.alpha = alphaInt
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (::dualScreenManager.isInitialized) {
            dualScreenManager.notifyUserActivity("mainUserInteraction")
        }
    }

    private fun refocusSelf() = startActivity(
        Intent(this, ArgosyActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
        }
    )
}

/** Settings moving onto this screen (or off it) crossfades with the showcase picture. */
private const val SETTINGS_LIFT_FADE_MS = 200
private const val SETTINGS_PANEL_EXIT_MS = 160
// A folder's page entrance: 240 ms, settling 26 dp into place.
private const val SETTINGS_PANEL_OPEN_MS = 240
private const val SETTINGS_PANEL_RISE_DP = 26f
private const val SETTINGS_PANEL_HOLD_MS = 600L
