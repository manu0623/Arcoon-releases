package com.nendo.argosy.ui.quickpanel

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.LocalActivityResultRegistryOwner
import android.app.Activity
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.core.input.ConnectedControllerTracker
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.preferences.ControlsPreferencesRepository
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.social.SocialRepository
import com.nendo.argosy.domain.usecase.music.GetMusicPlaylistsUseCase
import com.nendo.argosy.domain.usecase.music.ResolveMusicQueueUseCase
import com.nendo.argosy.domain.usecase.music.SearchSoundtrackGamesUseCase
import com.nendo.argosy.hardware.AmbientLedManager
import com.nendo.argosy.hardware.BrightnessController
import com.nendo.argosy.hardware.DevicePerformanceResolver
import com.nendo.argosy.hardware.DisplayRefreshController
import com.nendo.argosy.hardware.RadioController
import com.nendo.argosy.hardware.VolumeController
import com.nendo.argosy.ui.audio.AmbientAudioManager
import com.nendo.argosy.ui.components.FooterHost
import com.nendo.argosy.ui.components.FooterHostController
import com.nendo.argosy.ui.components.LocalFooterHost
import com.nendo.argosy.ui.components.QuickSettingsController
import com.nendo.argosy.ui.components.QuickSettingsInputRouter
import com.nendo.argosy.ui.components.QuickSettingsPage
import com.nendo.argosy.ui.components.QuickSettingsPanel
import com.nendo.argosy.ui.components.connections.QuickConnectionsController
import com.nendo.argosy.ui.components.connections.QuickConnectionsEffects
import com.nendo.argosy.ui.components.connections.QuickConnectionsInputHandler
import com.nendo.argosy.ui.components.connections.QuickConnectionsModals
import com.nendo.argosy.ui.components.connections.QuickSettingsConnectionsPage
import com.nendo.argosy.ui.components.friends.QuickFriendsController
import com.nendo.argosy.ui.components.friends.QuickFriendsInputHandler
import com.nendo.argosy.ui.components.friends.QuickFriendsModals
import com.nendo.argosy.ui.components.friends.QuickSettingsFriendsPage
import com.nendo.argosy.ui.components.musicplayer.MusicPlayerInputHandler
import com.nendo.argosy.ui.components.musicplayer.MusicPlayerViewModel
import com.nendo.argosy.ui.components.musicplayer.QuickSettingsMusicPage
import com.nendo.argosy.ui.input.GamepadInput
import com.nendo.argosy.ui.input.HapticFeedbackManager
import com.nendo.argosy.ui.input.InputDispatcher
import com.nendo.argosy.ui.input.LocalABIconsSwapped
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.input.LocalModalPresence
import com.nendo.argosy.ui.input.LocalSwapStartSelect
import com.nendo.argosy.ui.input.LocalXYIconsSwapped
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.input.buttonGlyphSwaps
import com.nendo.argosy.ui.input.mapKeycodeToGamepadEvent
import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.theme.ALauncherTheme
import com.nendo.argosy.ui.theme.ThemeViewModel
import com.nendo.argosy.util.hideWindowSystemBars
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.launch
import androidx.compose.runtime.withFrameNanos
import com.nendo.argosy.ui.theme.Motion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "QuickPanelOverlay"
private val EXIT_MS = Motion.durationSlide * 2L
private const val THEME_WAIT_MS = 750L
/**
 * True inside the panel's own window. The panel draws its ring from the same primitive the
 * launcher does, and the launcher's ring is muted while the panel is up, so the panel has to
 * say who it is or it loses the highlight to its own presence.
 */
val LocalQuickPanelSurface = androidx.compose.runtime.staticCompositionLocalOf { false }

private const val WARM_FRAMES = 2
/** How long the invisible warm-up panel stays up: long enough to compose and draw it a few times. */
private const val WARM_UP_MS = 1500L

/**
 * The quick settings panel drawn in a window of its own above whatever is on a display, so it can
 * open over a running game without bringing Argosy forward. Actions that would open an Argosy
 * screen are shown disabled here.
 */
@Singleton
class QuickPanelOverlay @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: UserPreferencesRepository,
    private val brightnessController: BrightnessController,
    private val volumeController: VolumeController,
    private val performanceResolver: DevicePerformanceResolver,
    private val refreshController: DisplayRefreshController,
    private val socialRepository: SocialRepository,
    private val radioController: RadioController,
    private val hapticManager: HapticFeedbackManager,
    private val soundManager: SoundFeedbackManager,
    private val ambientLedManager: AmbientLedManager,
    private val controllerTracker: ConnectedControllerTracker,
    private val ambientAudioManager: AmbientAudioManager,
    private val controlsPreferences: ControlsPreferencesRepository,
    private val getMusicPlaylists: GetMusicPlaylistsUseCase,
    private val searchSoundtrackGames: SearchSoundtrackGamesUseCase,
    private val resolveMusicQueue: ResolveMusicQueueUseCase
) {
    private val _isOpen get() = QuickPanelPresence.open
    val isOpen: StateFlow<Boolean> = QuickPanelPresence.open.asStateFlow()

    private var session: Session? = null
    private val exitScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun canShow(): Boolean = Settings.canDrawOverlays(context)

    /**
     * Opens the panel on [displayId]. False when overlay permission is missing or the window
     * cannot be added, so the caller can fall back to its own menu. [resultOwner] runs the
     * panel's permission requests, such as Bluetooth access on the Connections page; without one,
     * each request answers as declined.
     */
    fun show(
        displayId: Int,
        resultOwner: ActivityResultRegistryOwner?,
        page: QuickSettingsPage = QuickSettingsPage.QUICK
    ): Boolean {
        if (session != null) return true
        if (!canShow()) return false
        endWarmUp()
        val windowContext = windowContextFor(displayId) ?: return false
        val next = Session(windowContext, resultOwner ?: DeclinedResultRegistryOwner())
        next.startOnPage(page)
        _isOpen.value = true
        if (!next.attach()) {
            _isOpen.value = false
            next.detach()
            return false
        }
        session = next
        soundManager.play(SoundType.OPEN_MODAL)
        return true
    }

    /**
     * Opens the panel on the device's upper screen: the built-in panel that is not the
     * presentation-flagged one. On the Thor that is display 0 (1080x1920); display 4 (1080x1240)
     * carries FLAG_PRESENTATION and sits below it. Input focus moves over with it, because keys
     * follow the focused display rather than the focused window - without that the pad kept
     * driving the launcher underneath while the panel sat on the other screen.
     */
    fun showOnUpper(
        resultOwner: ActivityResultRegistryOwner?,
        page: QuickSettingsPage = QuickSettingsPage.QUICK
    ): Boolean {
        val all = context.getSystemService(DisplayManager::class.java)?.displays.orEmpty()
        val upper = upperScreenId(all) ?: return false
        val shown = show(upper, resultOwner, page)
        com.nendo.argosy.util.ArcoonTrace.log(
            "quick panel: screens=${all.map { it.displayId }} upper=$upper shown=$shown"
        )
        if (shown) DualScreenManagerHolder.instance?.focusDisplay(upper)
        return shown
    }

    private var warmSession: Session? = null

    /**
     * Builds the panel once, invisibly, on the upper screen and takes it down again: the first
     * real open then finds its code loaded and its drawing prepared, instead of stalling the
     * screens while it builds (about a tenth of a second). It takes no input and no focus, plays
     * no sound and does not count as open. Main thread only; does nothing while a panel is up.
     */
    fun prewarm() {
        if (session != null || warmSession != null || !canShow()) return
        val all = context.getSystemService(DisplayManager::class.java)?.displays.orEmpty()
        val upper = upperScreenId(all) ?: all.firstOrNull()?.displayId ?: return
        val windowContext = windowContextFor(upper) ?: return
        val warm = runCatching { Session(windowContext, DeclinedResultRegistryOwner(), warmUp = true) }.getOrNull() ?: return
        if (!warm.attach()) {
            warm.detach()
            return
        }
        warmSession = warm
        exitScope.launch {
            delay(WARM_UP_MS)
            if (warmSession === warm) endWarmUp()
        }
    }

    private fun endWarmUp() {
        val warm = warmSession ?: return
        warmSession = null
        warm.detach()
    }

    /** The upper screen's display id, or null on a single-screen device. */
    private fun upperScreenId(all: Array<out android.view.Display>): Int? {
        if (all.size < 2) return null
        all.firstOrNull {
            it.displayId == android.view.Display.DEFAULT_DISPLAY &&
                it.flags and android.view.Display.FLAG_PRESENTATION == 0
        }?.let { return it.displayId }
        // No default display to trust: the biggest panel that is not the companion one.
        return all
            .filter { it.flags and android.view.Display.FLAG_PRESENTATION == 0 }
            .maxByOrNull { it.mode.physicalWidth.toLong() * it.mode.physicalHeight }
            ?.displayId
    }

    fun hide() {
        val current = session ?: return
        if (!current.beginExit()) return
        exitScope.launch {
            delay(EXIT_MS)
            finish(current)
        }
    }

    fun hideNow() {
        session?.let(::finish)
    }

    private fun finish(target: Session) {
        if (session !== target) return
        session = null
        target.detach()
        _isOpen.value = false
    }

    private fun windowContextFor(displayId: Int): Context? {
        val displayManager = context.getSystemService(DisplayManager::class.java) ?: return null
        val display = displayManager.getDisplay(displayId) ?: return null
        val displayContext = context.createDisplayContext(display)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            displayContext.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        } else {
            displayContext
        }
    }

    private inner class Session(
        private val windowContext: Context,
        private val resultOwner: ActivityResultRegistryOwner,
        /** An invisible build of the panel ([prewarm]): never shown, never takes input. */
        private val warmUp: Boolean = false,
    ) {
        private val owner = OverlayWindowOwner()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val prefs: StateFlow<UserPreferences> =
            preferencesRepository.userPreferences.stateIn(scope, SharingStarted.Eagerly, UserPreferences())
        private val dispatcher = InputDispatcher(hapticManager, soundManager)
        private val footerHost = FooterHostController()
        private val friends = QuickFriendsController(socialRepository, preferencesRepository, scope)
        private val connections = QuickConnectionsController(radioController, scope)
        private val settings = QuickSettingsController(
            preferencesRepository = preferencesRepository,
            brightnessController = brightnessController,
            volumeController = volumeController,
            performanceResolver = performanceResolver,
            refreshController = refreshController,
            socialRepository = socialRepository,
            hapticManager = hapticManager,
            soundManager = soundManager,
            quickFriends = friends,
            wrapMode = { prefs.value.menuWrapMode },
            scope = scope
        )
        private val viewModels = ViewModelProvider(owner.viewModelStore, OverlayViewModelFactory())
        private val music = viewModels[MusicPlayerViewModel::class.java]
        private val theme = viewModels[ThemeViewModel::class.java]
        private val friendsHandler = QuickFriendsInputHandler(
            controller = friends,
            showQuayPass = { settings.state.value.isSocialLinked },
            quayPassEnabled = { settings.state.value.quayPassEnabled },
            wrapMode = { prefs.value.menuWrapMode },
            onToggleQuayPass = { settings.toggleQuayPass() },
            onOpenProfile = null,
            onEditAvatar = null
        )
        private val connectionsHandler = QuickConnectionsInputHandler(
            controller = connections,
            wrapMode = { prefs.value.menuWrapMode }
        )
        private val router = QuickSettingsInputRouter(
            panelHandler = settings.createInputHandler(onDismiss = ::hide, onOpenDeviceAccess = null),
            pageHandlers = mapOf(
                QuickSettingsPage.FRIENDS to friendsHandler,
                QuickSettingsPage.CONNECTIONS to connectionsHandler,
                QuickSettingsPage.MUSIC to MusicPlayerInputHandler(music, onOpenRommSignIn = null)
            ),
            activePage = { settings.activePage() }
        )
        private val shown = MutableStateFlow(false)

        /** Opens onto [page] instead of the device page (L1 lands on friends). */
        fun startOnPage(page: QuickSettingsPage) {
            settings.selectPage(page)
        }
        // A warm-up panel counts as leaving from the start: it is built but never shown.
        private var exiting = warmUp
        private val root = KeyRoot(windowContext)

        fun beginExit(): Boolean {
            if (exiting) return false
            exiting = true
            shown.value = false
            return true
        }
        private val windowManager = windowContext.getSystemService(WindowManager::class.java)

        fun attach(): Boolean {
            owner.start()
            DualScreenManagerHolder.instance?.let { dsm ->
                settings.setDualScreen(
                    active = dsm.isDualScreenDevice.value && dsm.isCompanionActive.value,
                    rolesSwapped = dsm.isRolesSwapped.value
                )
            }
            if (!warmUp) {
                settings.setOpen(true)
                dispatcher.subscribeDrawer(router)
            }
            val view = ComposeView(windowContext).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setViewTreeViewModelStoreOwner(owner)
                setContent {
                    CompositionLocalProvider(LocalQuickPanelSurface provides true) { PanelContent() }
                }
            }
            root.setViewTreeLifecycleOwner(owner)
            root.setViewTreeSavedStateRegistryOwner(owner)
            root.setViewTreeViewModelStoreOwner(owner)
            root.addView(view)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or (
                    // the warm-up window is see-through, untouchable and never takes the pad
                    if (warmUp) WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0
                    ),
                PixelFormat.TRANSLUCENT
            ).apply { if (warmUp) alpha = 0f }
            return runCatching {
                windowManager?.addView(root, params) ?: return false
                if (!warmUp) {
                    root.requestFocus()
                    root.hideWindowSystemBars()
                }
                true
            }.onFailure { Log.w(TAG, "addView failed", it) }.getOrDefault(false)
        }

        fun detach() {
            if (!warmUp) {
                settings.setOpen(false)
                dispatcher.unsubscribeDrawer()
            }
            music.onPageHidden()
            runCatching { if (root.isAttachedToWindow) windowManager?.removeView(root) }
                .onFailure { Log.w(TAG, "removeView failed", it) }
            owner.stop()
            scope.cancel()
        }

        @Composable
        private fun PanelContent() {
            val preferences by prefs.collectAsState()
            val activeLayout by controllerTracker.activeLayout.collectAsState()
            val swaps = remember(preferences, activeLayout) { preferences.buttonGlyphSwaps(activeLayout) }
            val state by settings.state.collectAsState()
            val page by settings.page.collectAsState()
            val focusIndex by settings.focusIndex.collectAsState()
            val friendsState by friends.state.collectAsState()
            val connectionsState by connections.state.collectAsState()
            val isShown by shown.collectAsState()
            val musicVisible = page == QuickSettingsPage.MUSIC
            LaunchedEffect(musicVisible) { if (!musicVisible) music.onPageHidden() }
            var warmed by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                withTimeoutOrNull(THEME_WAIT_MS) { theme.themeState.drop(1).first() }
                repeat(WARM_FRAMES) { withFrameNanos { } }
                warmed = true
                withFrameNanos { }
                if (!exiting) shown.value = true
            }

            ALauncherTheme(viewModel = theme) {
                CompositionLocalProvider(
                    LocalInputDispatcher provides dispatcher,
                    LocalModalPresence provides dispatcher,
                    LocalABIconsSwapped provides swaps.ab,
                    LocalXYIconsSwapped provides swaps.xy,
                    LocalSwapStartSelect provides swaps.startSelect,
                    LocalFooterHost provides footerHost,
                    LocalActivityResultRegistryOwner provides resultOwner
                ) {
                    QuickConnectionsEffects(
                        controller = connections,
                        pageVisible = page == QuickSettingsPage.CONNECTIONS
                    )
                    Box(modifier = Modifier.fillMaxSize()) {
                        key(warmed) {
                            QuickSettingsPanel(
                                modifier = if (warmed) Modifier else Modifier.alpha(0f),
                                isVisible = if (warmed) isShown else true,
                                state = state,
                                page = page,
                                focusedIndex = focusIndex,
                                controller = settings,
                                onOpenDeviceAccess = null,
                                onDismiss = ::hide,
                                friendsPage = {
                                    QuickSettingsFriendsPage(
                                        state = friendsState,
                                        showQuayPass = state.isSocialLinked,
                                        quayPassEnabled = state.quayPassEnabled,
                                        onRowClick = { friendsHandler.tapRow(it) },
                                        onRowLongClick = { friendsHandler.longPressRow(it) },
                                        onActionClick = { index, action -> friendsHandler.tapAction(index, action) }
                                    )
                                },
                                connectionsPage = {
                                    QuickSettingsConnectionsPage(
                                        state = connectionsState,
                                        onRowClick = connectionsHandler::tapRow,
                                        onShowList = connectionsHandler::tapList,
                                        onForget = connectionsHandler::tapForget
                                    )
                                },
                                musicPage = { QuickSettingsMusicPage(viewModel = music, onOpenRommSignIn = null) }
                            )
                        }
                        QuickFriendsModals(
                            state = friendsState,
                            controller = friends,
                            onOpenProfile = null,
                            onEditAvatar = null
                        )
                        QuickConnectionsModals(state = connectionsState, controller = connections)
                        if (isShown) {
                            FooterHost(controller = footerHost, modifier = Modifier.align(Alignment.BottomCenter))
                        }
                    }
                }
            }
        }

        private inner class KeyRoot(context: Context) : FrameLayout(context) {
            init {
                isFocusable = true
                isFocusableInTouchMode = true
            }

            override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
                super.onWindowFocusChanged(hasWindowFocus)
                if (hasWindowFocus) hideWindowSystemBars()
            }

            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.action != KeyEvent.ACTION_DOWN || exiting || !shown.value) return true
                val swaps = prefs.value
                val gamepadEvent = mapKeycodeToGamepadEvent(
                    event.keyCode,
                    swaps.swapAB,
                    swaps.swapXY,
                    swaps.swapStartSelect
                ) ?: return super.dispatchKeyEvent(event)
                if (gamepadEvent == GamepadEvent.Back && event.repeatCount > 0) return true
                dispatcher.dispatch(GamepadInput(gamepadEvent, isRepeat = event.repeatCount > 0))
                return true
            }
        }
    }

    private inner class OverlayViewModelFactory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
            MusicPlayerViewModel::class.java -> MusicPlayerViewModel(
                ambientAudioManager = ambientAudioManager,
                controlsPreferences = controlsPreferences,
                getMusicPlaylists = getMusicPlaylists,
                searchSoundtrackGames = searchSoundtrackGames,
                resolveMusicQueue = resolveMusicQueue
            )
            ThemeViewModel::class.java -> ThemeViewModel(
                preferencesRepository = preferencesRepository,
                ambientLedManager = ambientLedManager,
                connectedControllerTracker = controllerTracker
            )
            else -> throw IllegalArgumentException("No overlay factory for ${modelClass.name}")
        } as T
    }
}

private class DeclinedResultRegistryOwner : ActivityResultRegistryOwner {
    override val activityResultRegistry: ActivityResultRegistry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?
        ) {
            dispatchResult(requestCode, Activity.RESULT_CANCELED, null)
        }
    }
}

private class OverlayWindowOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()

    fun start() {
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}

/**
 * Whether a quick panel window is up anywhere in the process. The launcher reads it to drop its
 * own focus ring: while the panel is open the pad is driving that window, so a highlight left on
 * the other screen points at nothing.
 */
object QuickPanelPresence {
    val open = MutableStateFlow(false)
}
