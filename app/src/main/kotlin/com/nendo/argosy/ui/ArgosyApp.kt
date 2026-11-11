package com.nendo.argosy.ui

import com.nendo.argosy.ui.common.deferredBlur
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.focusable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import android.content.Intent
import com.nendo.argosy.ui.util.doubleTapNoFocus
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.nendo.argosy.libretro.LibretroActivity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nendo.argosy.ui.components.BackgroundSyncConflictDialog
import com.nendo.argosy.ui.components.SyncOverlay
import com.nendo.argosy.ui.components.FloatingNavBar
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.revealOnBottomEdgeTouch
import com.nendo.argosy.ui.components.FooterHost
import com.nendo.argosy.ui.components.FooterHostController
import com.nendo.argosy.ui.components.LocalFooterHost
import com.nendo.argosy.ui.components.LocalFooterHostVisible
import com.nendo.argosy.ui.components.isBarVisible
import com.nendo.argosy.ui.components.LocalNavBarClearance
import com.nendo.argosy.ui.components.LocalFrostedBackdrop
import com.nendo.argosy.ui.components.frostedBackdropSource
import com.nendo.argosy.ui.components.rememberFrostedBackdrop
import com.nendo.argosy.ui.components.LocalTransientHomeStatusVisible
import com.nendo.argosy.data.sync.ConflictResolution
import com.nendo.argosy.ui.components.MainDrawer
import com.nendo.argosy.ui.components.QuickSettingsInputRouter
import com.nendo.argosy.ui.components.QuickSettingsPage
import com.nendo.argosy.ui.components.QuickSettingsPanel
import com.nendo.argosy.ui.components.connections.QuickConnectionsEffects
import com.nendo.argosy.ui.components.connections.QuickConnectionsInputHandler
import com.nendo.argosy.ui.components.connections.QuickConnectionsModals
import com.nendo.argosy.ui.components.connections.QuickSettingsConnectionsPage
import com.nendo.argosy.ui.components.friends.QuickFriendsInputHandler
import com.nendo.argosy.ui.components.friends.QuickFriendsModals
import com.nendo.argosy.ui.components.friends.QuickSettingsFriendsPage
import com.nendo.argosy.ui.components.musicplayer.MusicPlayerInputHandler
import com.nendo.argosy.ui.components.musicplayer.MusicPlayerViewModel
import com.nendo.argosy.ui.components.musicplayer.QuickSettingsMusicPage
import com.nendo.argosy.ui.components.NetplayInviteModal
import com.nendo.argosy.ui.components.NetplayJoinModal
import com.nendo.argosy.ui.input.NetplayJoinInputHandler
import com.nendo.argosy.data.netplay.NetplayJoinState
import com.nendo.argosy.data.netplay.VerifySubState
import com.nendo.argosy.ui.components.CoreCrashModal
import com.nendo.argosy.ui.components.SaveConflictModal
import com.nendo.argosy.ui.components.ScreenDimmerOverlay
import com.nendo.argosy.ui.input.BackgroundConflictInputHandler
import com.nendo.argosy.ui.input.CapturingInputHandler
import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.input.HapticPattern
import com.nendo.argosy.ui.input.InputDispatcher
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.input.LocalGamepadInputHandler
import com.nendo.argosy.ui.input.LocalABIconsSwapped
import com.nendo.argosy.ui.input.LocalXYIconsSwapped
import com.nendo.argosy.ui.input.LocalSwapStartSelect
import com.nendo.argosy.ui.input.UiShortcut
import com.nendo.argosy.ui.input.UiShortcutGate
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.navigation.NavBarVisibility
import com.nendo.argosy.ui.navigation.NavGraph
import com.nendo.argosy.ui.navigation.NavRing
import com.nendo.argosy.ui.navigation.RouteRestore
import com.nendo.argosy.ui.navigation.concreteRoute
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.screens.player.PlayerActivity
import com.nendo.argosy.ui.screens.player.PlayerArgs
import com.nendo.argosy.core.notification.NotificationHost
import com.nendo.argosy.ui.quickmenu.QuickMenuInputHandler
import com.nendo.argosy.ui.quickmenu.QuickMenuOverlay
import com.nendo.argosy.ui.quickmenu.QuickMenuViewModel
import com.nendo.argosy.DualScreenManager
import com.nendo.argosy.ui.screens.settings.SettingsSection
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.gripReserveBottomInset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val KEY_SINK_RECLAIM_GRACE_MS = 250L
private const val NAV_READY_TIMEOUT_MS = 45_000L

@Composable
fun ArgosyApp(
    viewModel: ArgosyViewModel = launcherViewModel(),
    quickMenuViewModel: QuickMenuViewModel = launcherViewModel(),
    musicPlayerViewModel: MusicPlayerViewModel = launcherViewModel(),
    onStartupComplete: () -> Unit = {},
    /**
     * Opens the quick settings panel in a window of its own on another display (the upper screen
     * on a dual-screen device). True when it opened there, so the launcher keeps its own copy shut.
     */
    openQuickPanelOnTop: ((androidx.activity.result.ActivityResultRegistryOwner?, QuickSettingsPage) -> Boolean)? = null,
    /** Closes that panel again; a no-op when it is not open. */
    hideQuickPanel: () -> Unit = {},
    /** Whether that panel is up, so the same button can close it again. */
    isQuickPanelOpen: (() -> Boolean)? = null,
    /** True for the copy drawn inside the upper screen's floating Settings panel. */
    asLiftedPanel: Boolean = false
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val routeDisplay = androidx.compose.ui.platform.LocalView.current.display?.displayId
    val launcherInstance = androidx.compose.runtime.remember {
        com.nendo.argosy.util.RebuildTrace.id("launcher")
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.nendo.argosy.util.ArcoonTrace.log(
            "launcher composed id=$launcherInstance display=$routeDisplay lifted=$asLiftedPanel"
        )
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            com.nendo.argosy.util.ArcoonTrace.log("launcher disposed id=$launcherInstance")
        }
    }
    LaunchedEffect(currentRoute) {
        com.nendo.argosy.util.ArcoonTrace.log("launcher on display $routeDisplay now on route: $currentRoute")
    }

    val uiState by viewModel.uiState.collectAsState()
    val drawerUiState by viewModel.drawerUiState.collectAsState()
    val isDrawerOpen by viewModel.isDrawerOpen.collectAsState()
    val isQuickSettingsOpen by viewModel.quickSettings.isOpen.collectAsState()
    val quickSettingsFocusIndex by viewModel.quickSettings.focusIndex.collectAsState()
    val quickSettingsPage by viewModel.quickSettings.page.collectAsState()
    val quickSettingsState by viewModel.quickSettings.state.collectAsState()
    val quickFriendsState by viewModel.quickFriends.state.collectAsState()
    val quickConnectionsState by viewModel.quickConnections.state.collectAsState()
    // Only the one field read here: the full music state changes twice a second while the music
    // page is open (playback position), and reading all of it made this whole screen redraw.
    val musicBrowseNotice by androidx.compose.runtime.remember(musicPlayerViewModel) {
        musicPlayerViewModel.uiState
            .map { it.browse?.notice }
            .distinctUntilChanged()
    }.collectAsState(initial = null)
    val screenDimmerPrefs by viewModel.screenDimmerPreferences.collectAsState()
    val isEmulatorRunning by viewModel.isEmulatorRunning.collectAsState()
    val quickMenuState by quickMenuViewModel.uiState.collectAsState()
    val saveConflictInfo by viewModel.saveConflicts.saveConflictInfo.collectAsState()
    val saveConflictButtonIndex by viewModel.saveConflicts.saveConflictButtonIndex.collectAsState()
    val backgroundConflictInfo by viewModel.saveConflicts.backgroundConflictInfo.collectAsState()
    val backgroundConflictButtonIndex by viewModel.saveConflicts.backgroundConflictButtonIndex.collectAsState()
    val backgroundConflictSnapshot by viewModel.saveConflicts.backgroundConflictSnapshot.collectAsState()
    val coreCrashPrompt by viewModel.coreCrashController.prompt.collectAsState()
    val pushedApkPrompt by viewModel.apkInstallManager.pushedPrompt.collectAsState()
    val coreCrashFocusIndex by viewModel.coreCrashController.focusIndex.collectAsState()
    val coreCrashDownloading by viewModel.coreCrashController.downloading.collectAsState()
    val netplayInvitePrompt by viewModel.netplayInvitePrompt.collectAsState()
    val netplayInviteFocusIndex by viewModel.netplayInviteFocusIndex.collectAsState()
    val netplayJoinState by viewModel.netplayJoinState.collectAsState()
    val navRingState by viewModel.navRingState.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as? com.nendo.argosy.ArgosyActivity
    val dsm = remember { com.nendo.argosy.DualScreenManagerHolder.instance }
    val suspendedGameSession by dsm?.gameSessionManager?.sessionState?.collectAsState()
        ?: remember {
            mutableStateOf<com.nendo.argosy.domain.model.multitasking.SuspendedGameSession?>(null)
        }
    val hasBuiltInSuspendedGame = suspendedGameSession?.builtIn == true
    val baseDrawerItems = viewModel.drawerItems
    val drawerItems = remember(baseDrawerItems, hasBuiltInSuspendedGame) {
        if (hasBuiltInSuspendedGame) {
            listOf(DrawerItem(DRAWER_RESUME_GAME_ROUTE, R.string.ui_drawer_resume_game)) + baseDrawerItems
        } else {
            baseDrawerItems
        }
    }
    var quitSuspendedGameId by remember { mutableStateOf<Long?>(null) }
    val requestQuitSuspendedGame = rememberUpdatedState {
        suspendedGameSession?.takeIf { it.builtIn }?.gameId?.let { quitSuspendedGameId = it }
    }
    LaunchedEffect(suspendedGameSession?.gameId) {
        if (quitSuspendedGameId != suspendedGameSession?.gameId) {
            quitSuspendedGameId = null
        }
    }

    val companionActive by dsm?.isCompanionActive?.collectAsState()
        ?: remember { mutableStateOf(false) }
    val isDualScreenDevice by dsm?.isDualScreenDevice?.collectAsState()
        ?: remember { mutableStateOf(false) }
    val isRolesSwapped by dsm?.isRolesSwapped?.collectAsState()
        ?: remember { mutableStateOf(false) }
    val isOnHomeScreen = currentRoute == Screen.Home.route

    val isDualActive = isDualScreenDevice && companionActive
    LaunchedEffect(isDualActive, isRolesSwapped) {
        viewModel.quickSettings.setDualScreen(active = isDualActive, rolesSwapped = isRolesSwapped)
    }

    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading) onStartupComplete()
    }

    val isOnWizard = currentRoute == Screen.FirstRun.route
    var wasOnWizard by remember { mutableStateOf(isOnWizard) }
    // Cocoon-style optimization starts once setup is finished (the wizard always comes first).
    val optimizeContext = LocalContext.current
    LaunchedEffect(uiState.isFirstRun, isOnWizard) {
        if (!uiState.isFirstRun && !isOnWizard) com.nendo.argosy.ui.cocoon.CocoonOptimizer.start(optimizeContext)
    }
    LaunchedEffect(uiState.isFirstRun, isOnWizard) {
        dsm?.recordWizardState(uiState.isFirstRun || isOnWizard)
        if (wasOnWizard && !isOnWizard && !uiState.isFirstRun) {
            viewModel.triggerPostWizardSync()
        }
        wasOnWizard = isOnWizard
    }

    LaunchedEffect(isOnHomeScreen) {
        activity?.isOnHomeScreen = isOnHomeScreen
        dsm?.setPrimaryOnHome(isOnHomeScreen)
    }

    /*
     * Settings opens on the showcase screen: that surface has the room, and the board's
     * presentation moves down to the one the settings list just left. The lift lasts no longer
     * than the settings screen that raised it; the arrangement itself is left alone.
     */
    val settingsLiftOwner by dsm?.settingsLift?.collectAsState()
        ?: remember { mutableStateOf<Any?>(null) }
    val launcherSurfaceToken = remember { Any() }
    var settingsLiftMine by remember { mutableStateOf(false) }
    // The lifted screen ends the lift only once it has shown Settings and Settings has gone again.
    // Starting up it has no Settings yet, and ending the lift then bounced input between screens.
    var liftedSettingsShown by remember { mutableStateOf(false) }
    val settingsRouteOpen = currentRoute?.startsWith(Screen.Settings.route) == true
    // Every launcher copy hears the same controller. While Settings floats on the upper screen,
    // the hidden lower copy stops listening: it has no Settings page of its own, so Left there
    // opened the drawer (whose state the copies share) over the panel.
    val inputYieldsToPanel = !asLiftedPanel && settingsLiftOwner != null
    val hidesSettingsHere = !asLiftedPanel && settingsRouteOpen &&
        (settingsLiftOwner != null || dsm?.companionHoldsPrimaryNow() == true)
    LaunchedEffect(asLiftedPanel) {
        com.nendo.argosy.util.ArcoonTrace.log(
            "launcher composed on display $routeDisplay panel=$asLiftedPanel lifted=${settingsLiftOwner != null}"
        )
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            com.nendo.argosy.util.ArcoonTrace.log("launcher disposed on display $routeDisplay panel=$asLiftedPanel")
        }
    }
    // Leaves Settings on this screen without ever emptying its history: Settings can be its first
    // entry (restored from a carried route), and an empty history reopened Settings by itself.
    val leaveSettingsHere: () -> Unit = {
        if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
        } else {
            navController.navigate(Screen.Home.route) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
    LaunchedEffect(settingsRouteOpen, isDualActive, settingsLiftOwner) {
        val owner = settingsLiftOwner
        if (asLiftedPanel) {
            // The floating panel: it ends the lift once its Settings has shown and gone again.
            // getBackStackEntry is the public read of a stack that NavController keeps closed:
            // it throws when no destination matching the route is on this screen's stack.
            val settingsStillStacked = try {
                navController.getBackStackEntry(Screen.Settings.route)
                true
            } catch (_: IllegalArgumentException) {
                false
            }
            if (owner == null) liftedSettingsShown = false
            if (owner != null && settingsStillStacked) liftedSettingsShown = true
            if (owner != null && !settingsStillStacked && liftedSettingsShown) {
                liftedSettingsShown = false
                dsm?.dropSettingsLift(owner)
            }
            return@LaunchedEffect
        }
        when {
            // Settings stays in this screen's history, hidden behind the showcase picture, and
            // leaves once the panel closes, so Home comes back (and re-publishes its highlight).
            // Any copy of this screen that saw the lift does this, including one rebuilt while
            // the panel was up, which otherwise sat on an empty Settings page (black screen).
            owner != null -> settingsLiftMine = true
            settingsLiftMine && owner == null -> {
                settingsLiftMine = false
                if (settingsRouteOpen) leaveSettingsHere()
            }
            settingsRouteOpen && isDualActive && owner == null -> {
                val route = navController.currentBackStackEntry?.concreteRoute()
                // What this screen goes back to, recorded first so a rebuild opens there.
                dsm?.setPrimaryRoute(
                    navController.previousBackStackEntry?.concreteRoute() ?: Screen.Home.route
                )
                if (dsm?.liftSettingsToShowcase(launcherSurfaceToken, route) == true) {
                    settingsLiftMine = true
                } else {
                    route?.let { dsm?.setPrimaryRoute(it) }
                }
            }
        }
    }

    val carriedRoute = remember { if (asLiftedPanel) dsm?.liftedRoute else dsm?.primaryRoute?.value }
    val carriedStack = remember(carriedRoute) {
        carriedRoute?.let(RouteRestore::restoreStack).orEmpty()
    }
    LaunchedEffect(Unit) {
        val stack = carriedStack
        if (stack.isEmpty()) return@LaunchedEffect
        val entry = withTimeoutOrNull(NAV_READY_TIMEOUT_MS) {
            navController.currentBackStackEntryFlow.first()
        } ?: return@LaunchedEffect
        if (entry.destination.route != stack.first() || navController.previousBackStackEntry != null) {
            return@LaunchedEffect
        }
        stack.drop(1).forEach { navController.navigate(it) }
    }

    LaunchedEffect(navBackStackEntry) {
        // Settings opened on the lower screen lives on the upper one, so it is never this
        // screen's route to carry: a rebuilt lower screen restored it and reopened Settings.
        val floatsUp = navBackStackEntry?.destination?.route?.startsWith(Screen.Settings.route) == true &&
            (settingsLiftOwner != null || dsm?.companionHoldsPrimaryNow() == true)
        if (!asLiftedPanel && !floatsUp) navBackStackEntry?.let { dsm?.setPrimaryRoute(it.concreteRoute()) }
    }

    val handleDeepLink: suspend (android.net.Uri) -> Unit = { uri ->
        android.util.Log.d("ArgosyApp", "Handling deep link: $uri")
        val showDeepLinkNotice: (Int) -> Unit = { messageRes ->
            android.widget.Toast.makeText(context, messageRes, android.widget.Toast.LENGTH_LONG).show()
        }
        val awaitNavGraph: suspend () -> Boolean = {
            val ready = withTimeoutOrNull(NAV_READY_TIMEOUT_MS) {
                navController.currentBackStackEntryFlow.first()
            } != null
            if (!ready) {
                android.util.Log.w("ArgosyApp", "Nav graph not ready, dropping deep link $uri")
                showDeepLinkNotice(R.string.ui_deep_link_startup_timeout)
            }
            ready
        }
        if (uri.scheme == "argosy") {
            when (uri.host) {
                "game" -> {
                    val gameId = uri.lastPathSegment?.toLongOrNull()
                    if (gameId != null && awaitNavGraph()) {
                        navController.navigate(Screen.GameDetail.createRoute(gameId)) {
                            launchSingleTop = true
                        }
                    }
                }
                "play" -> {
                    val gameId = uri.lastPathSegment?.toLongOrNull()
                    if (gameId != null && awaitNavGraph()) {
                        viewModel.initiateGameLaunch(gameId)
                        navController.navigate(Screen.GameDetail.createRoute(gameId)) {
                            launchSingleTop = true
                        }
                    }
                }
                "apps" -> {
                    if (awaitNavGraph()) {
                        navController.navigate(Screen.Apps.route) {
                            launchSingleTop = true
                        }
                    }
                }
                "launch" -> {
                    val request = com.nendo.argosy.ui.deeplink.DeepLinkParser.parse(uri)
                    if (request == null) {
                        android.util.Log.w("ArgosyApp", "Deep link carried no target: $uri")
                    } else {
                        when (val outcome = viewModel.resolveDeepLinkLaunch(request)) {
                            is com.nendo.argosy.ui.deeplink.DeepLinkLaunch.Ready -> {
                                if (awaitNavGraph()) {
                                    navController.navigate(
                                        Screen.GameDetail.createRoute(outcome.gameId)
                                    ) {
                                        launchSingleTop = true
                                    }
                                    viewModel.awaitDeepLinkSyncReady()
                                    viewModel.initiateGameLaunch(
                                        outcome.gameId,
                                        outcome.channelName,
                                        origin = com.nendo.argosy.data.emulator.LaunchOrigin.EXTERNAL
                                    )
                                }
                            }
                            is com.nendo.argosy.ui.deeplink.DeepLinkLaunch.Failed -> {
                                showDeepLinkNotice(outcome.messageRes)
                            }
                        }
                    }
                }
            }
        }
    }
    val currentHandleDeepLink by rememberUpdatedState(handleDeepLink)
    val inputLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val launcherReturnGameId by
        com.nendo.argosy.ui.cocoon.CocoonSuspendTest.launcherReturn.collectAsState()
    LaunchedEffect(launcherReturnGameId, navController) {
        if (launcherReturnGameId < 0L) return@LaunchedEffect
        // A suspend brings the launcher back where the game was started. If that is the game's own
        // details page, the pad belongs to the other screen and nothing here can leave it, so step
        // back to the page under it (or Home when there is none).
        val route = navController.currentDestination?.route
        val detailRoute = com.nendo.argosy.ui.navigation.Screen.GameDetail.route
        if (route == detailRoute || route?.startsWith(detailRoute) == true) {
            if (!navController.popBackStack()) {
                navController.navigate(com.nendo.argosy.ui.navigation.Screen.Home.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
        com.nendo.argosy.ui.cocoon.CocoonSuspendTest.launcherReturn.value = -1L
    }

    LaunchedEffect(dsm, inputLifecycle) {
        val manager = dsm ?: return@LaunchedEffect
        manager.pendingDeepLink.flowWithLifecycle(inputLifecycle).filterNotNull().collect { uri ->
            if (manager.consumeDeepLink(uri)) currentHandleDeepLink(uri)
        }
    }

    val drawerState = rememberDrawerState(
        initialValue = DrawerValue.Closed
    )

    val inputDispatcher = remember {
        InputDispatcher(
            hapticManager = viewModel.hapticManager,
            soundManager = viewModel.soundManager
        )
    }

    val unconfiguredScreenSet by (
        dsm?.unconfiguredScreenSet
            ?: remember { kotlinx.coroutines.flow.MutableStateFlow<String?>(null) }
        ).collectAsState()

    var screenSetPromptVisible by remember { mutableStateOf(false) }

    LaunchedEffect(unconfiguredScreenSet) {
        if (unconfiguredScreenSet == null) {
            screenSetPromptVisible = false
            return@LaunchedEffect
        }
        screenSetPromptVisible = true
        delay(com.nendo.argosy.core.notification.SCREEN_SET_PROMPT_MS)
        screenSetPromptVisible = false
        dsm?.clearUnconfiguredScreenSet()
    }

    val screenSetPromptHandler = remember(navController) {
        object : InputHandler {
            override fun onContextMenu(): InputResult {
                screenSetPromptVisible = false
                dsm?.clearUnconfiguredScreenSet()
                navController.navigate(
                    Screen.Settings.createRoute(section = SettingsSection.SCREENS.name)
                ) { launchSingleTop = true }
                return InputResult.HANDLED
            }
        }
    }

    LaunchedEffect(screenSetPromptVisible) {
        inputDispatcher.setInterceptHandler(
            if (screenSetPromptVisible) screenSetPromptHandler else null
        )
    }

    val presentationShowsHints by dsm?.presentationShowsHints?.collectAsState()
        ?: remember { mutableStateOf(false) }

    val footerHostController = remember { FooterHostController() }

    val rootFocusRequester = remember { FocusRequester() }
    var resumeCount by remember { mutableStateOf(0) }
    var anyResumeCount by remember { mutableStateOf(0) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        anyResumeCount++
        if (dsm?.isOverlayFocused != true) {
            inputDispatcher.blockInputFor(200)
            inputDispatcher.resetToMainView()
            viewModel.resetAllModals()
            resumeCount++
        }
        viewModel.refreshControllerDetection()
        try { rootFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    if (dsm != null) {
        val swappedGameActive by dsm.swappedIsGameActive.collectAsState()
        LaunchedEffect(isRolesSwapped, companionActive, swappedGameActive) {
            inputDispatcher.resetToMainView()
            val liveRoute = navController.currentBackStackEntry?.destination?.route
            inputDispatcher.clearPendingViewSubscription(keepRoute = liveRoute)
            if (dsm.isOverlayFocused) {
                dsm.isOverlayFocused = false
                dsm.controlCompanion?.onOverlayClosed()
            }
            viewModel.setDrawerOpen(false)
            viewModel.quickSettings.setOpen(false)
            quickMenuViewModel.hide()
        }
    }

    val startDestination = remember(uiState.isLoading, carriedStack) {
        when {
            uiState.isFirstRun -> Screen.FirstRun.route
            else -> carriedStack.firstOrNull() ?: Screen.Home.route
        }
    }

    val navigateFromDrawer: (String) -> Unit = remember {
        { route ->
            val current = navController.currentDestination?.route
            if (route != current) {
                navController.navigate(route) {
                    popUpTo(Screen.Home.route) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    // Settings opens on the other screen, which builds it from scratch: started while the drawer
    // slides shut, that froze the drawer for a moment and it jumped closed. Settings now opens
    // once the drawer has finished closing; other pages still open at once.
    val closeDrawerThenNavigate: (String) -> Unit = remember(scope, drawerState, navigateFromDrawer) {
        { route ->
            if (route.substringBefore("?") == Screen.Settings.route.substringBefore("?")) {
                scope.launch {
                    try {
                        drawerState.close()
                    } finally {
                        navigateFromDrawer(route)
                    }
                }
            } else {
                scope.launch { drawerState.close() }
                navigateFromDrawer(route)
            }
            Unit
        }
    }

    val resumeSuspendedGame: () -> Unit = remember(dsm, inputDispatcher, viewModel, scope, drawerState) {
        {
            inputDispatcher.unsubscribeDrawer()
            viewModel.setDrawerOpen(false)
            scope.launch { drawerState.close() }
            dsm?.gameSessionManager?.resumeSession()
            Unit
        }
    }

    val currentDrawerItems = rememberUpdatedState(drawerItems)
    val currentRouteNow = rememberUpdatedState(currentRoute)
    val drawerInputHandler = remember(viewModel, resumeSuspendedGame) {
        viewModel.createDrawerInputHandler(
            items = { currentDrawerItems.value },
            onNavigate = { route ->
                inputDispatcher.unsubscribeDrawer()
                viewModel.setDrawerOpen(false)
                closeDrawerThenNavigate(route)
            },
            onDismiss = {
                inputDispatcher.unsubscribeDrawer()
                viewModel.setDrawerOpen(false)
            },
            onResumeGame = resumeSuspendedGame,
            onRequestQuitGame = { requestQuitSuspendedGame.value() }
        )
    }

    val openDrawer = remember(drawerInputHandler, hasBuiltInSuspendedGame) {
        wizardGuard@{
            if (uiState.isFirstRun) return@wizardGuard
            viewModel.hideNavBar()
            inputDispatcher.subscribeDrawer(drawerInputHandler)
            viewModel.setDrawerOpen(true)
            val parentRoute = navController.previousBackStackEntry?.destination?.route
            viewModel.initDrawerFocus(
                currentRoute = currentRouteNow.value,
                parentRoute = parentRoute,
                items = drawerItems,
                preferFirstItem = hasBuiltInSuspendedGame
            )
            viewModel.onDrawerOpened()
            viewModel.soundManager.play(SoundType.OPEN_MODAL)
        }
    }

    val closeDrawer = remember {
        {
            inputDispatcher.unsubscribeDrawer()
            viewModel.setDrawerOpen(false)
        }
    }

    val openRommSignIn: () -> Unit = remember(viewModel, musicPlayerViewModel, inputDispatcher, navController) {
        {
            musicPlayerViewModel.closeBrowse()
            inputDispatcher.unsubscribeDrawer()
            viewModel.quickSettings.setOpen(false)
            navController.navigate(
                Screen.Settings.createRoute(section = SettingsSection.ROMM.name)
            ) { launchSingleTop = true }
        }
    }

    val openDeviceAccess: () -> Unit = remember(viewModel, inputDispatcher, navController) {
        {
            inputDispatcher.unsubscribeDrawer()
            viewModel.quickSettings.setOpen(false)
            navController.navigate(
                Screen.Settings.createRoute(section = SettingsSection.PERMISSIONS.name)
            ) { launchSingleTop = true }
        }
    }

    val quickFriendsInputHandler = remember(viewModel, inputDispatcher) {
        QuickFriendsInputHandler(
            controller = viewModel.quickFriends,
            showQuayPass = { viewModel.quickSettings.state.value.isSocialLinked },
            quayPassEnabled = { viewModel.quickSettings.state.value.quayPassEnabled },
            wrapMode = { viewModel.uiState.value.menuWrapMode },
            onToggleQuayPass = { viewModel.quickSettings.toggleQuayPass() },
            onOpenProfile = { userId ->
                inputDispatcher.unsubscribeDrawer()
                viewModel.quickSettings.setOpen(false)
                navigateFromDrawer(Screen.UserProfile.createRoute(userId))
            },
            onEditAvatar = {
                inputDispatcher.unsubscribeDrawer()
                viewModel.quickSettings.setOpen(false)
                navigateFromDrawer(Screen.AvatarDoodle.route)
            }
        )
    }

    val quickConnectionsInputHandler = remember(viewModel) {
        QuickConnectionsInputHandler(
            controller = viewModel.quickConnections,
            wrapMode = { viewModel.uiState.value.menuWrapMode }
        )
    }

    val quickSettingsInputHandler = remember(
        viewModel, musicPlayerViewModel, inputDispatcher, openRommSignIn, openDeviceAccess, quickFriendsInputHandler,
        quickConnectionsInputHandler
    ) {
        QuickSettingsInputRouter(
            panelHandler = viewModel.quickSettings.createInputHandler(
                onDismiss = {
                    inputDispatcher.unsubscribeDrawer()
                    viewModel.quickSettings.setOpen(false)
                },
                onOpenDeviceAccess = openDeviceAccess
            ),
            pageHandlers = mapOf(
                QuickSettingsPage.FRIENDS to quickFriendsInputHandler,
                QuickSettingsPage.CONNECTIONS to quickConnectionsInputHandler,
                QuickSettingsPage.MUSIC to MusicPlayerInputHandler(musicPlayerViewModel, openRommSignIn)
            ),
            activePage = { viewModel.quickSettings.activePage() }
        )
    }

    val musicPageVisible = isQuickSettingsOpen && quickSettingsPage == QuickSettingsPage.MUSIC
    LaunchedEffect(musicPageVisible) {
        if (!musicPageVisible) musicPlayerViewModel.onPageHidden()
    }

    QuickConnectionsEffects(
        controller = viewModel.quickConnections,
        pageVisible = isQuickSettingsOpen && quickSettingsPage == QuickSettingsPage.CONNECTIONS
    )

    // Read in composition: the panel's permission dialogs (Bluetooth and friends) run through
    // this activity's result registry.
    val quickPanelResultOwner = androidx.activity.compose.LocalActivityResultRegistryOwner.current
    val openQuickSettings = remember(quickSettingsInputHandler, openQuickPanelOnTop, isQuickPanelOpen) {
        wizardGuard@{
            if (uiState.isFirstRun) return@wizardGuard
            // On two screens the panel opens on the upper one, over whatever is there: the
            // board below stays where it is, drawer and all.
            // The panel is a window of its own, so the launcher's open flag says nothing
            // about it: the same button has to close it when it is already up.
            if (isQuickPanelOpen?.invoke() == true) {
                hideQuickPanel()
                return@wizardGuard
            }
            if (openQuickPanelOnTop?.invoke(quickPanelResultOwner, QuickSettingsPage.QUICK) == true) {
                return@wizardGuard
            }
            viewModel.hideNavBar()
            inputDispatcher.subscribeDrawer(quickSettingsInputHandler)
            viewModel.quickSettings.setOpen(true)
            viewModel.soundManager.play(SoundType.OPEN_MODAL)
        }
    }

    // L1 opens friends and R1 the device page, so the bumpers pick a page as they open.
    val openQuickSettingsPage = remember(quickSettingsInputHandler, openQuickPanelOnTop, isQuickPanelOpen) {
        pageOpen@{ page: QuickSettingsPage ->
            if (uiState.isFirstRun) return@pageOpen
            if (isQuickPanelOpen?.invoke() == true) {
                hideQuickPanel()
                return@pageOpen
            }
            // On two screens the bumpers open the panel on the upper screen at their page (R1 quick
            // menu, L1 friends); the drawer on this screen is only the fallback.
            if (openQuickPanelOnTop?.invoke(quickPanelResultOwner, page) == true) {
                return@pageOpen
            }
            viewModel.quickSettings.selectPage(page)
            viewModel.hideNavBar()
            inputDispatcher.subscribeDrawer(quickSettingsInputHandler)
            viewModel.quickSettings.setOpen(true)
            viewModel.soundManager.play(SoundType.OPEN_MODAL)
        }
    }

    val closeQuickSettings = remember(hideQuickPanel) {
        {
            inputDispatcher.unsubscribeDrawer()
            hideQuickPanel()
            viewModel.quickSettings.setOpen(false)
        }
    }

    val closeQuickMenu = remember {
        {
            inputDispatcher.unsubscribeDrawer()
            quickMenuViewModel.hide()
        }
    }

    val playFromQuickMenu: (Long) -> Unit = remember(navController, closeQuickMenu) {
        { gameId ->
            closeQuickMenu()
            viewModel.initiateGameLaunch(gameId)
            navController.navigate(Screen.GameDetail.createRoute(gameId)) {
                launchSingleTop = true
            }
        }
    }

    val quickMenuInputHandler = remember(quickMenuViewModel, navController, closeQuickMenu, playFromQuickMenu) {
        QuickMenuInputHandler(
            viewModel = quickMenuViewModel,
            onGameSelect = { gameId ->
                closeQuickMenu()
                navController.navigate(Screen.GameDetail.createRoute(gameId)) {
                    launchSingleTop = true
                }
            },
            onGamePlay = playFromQuickMenu,
            onDismiss = { closeQuickMenu() }
        )
    }

    val openQuickMenu = remember(quickMenuInputHandler) {
        wizardGuard@{
            if (uiState.isFirstRun) return@wizardGuard
            if (isDrawerOpen) closeDrawer()
            if (isQuickSettingsOpen) closeQuickSettings()
            viewModel.hideNavBar()
            inputDispatcher.subscribeDrawer(quickMenuInputHandler)
            quickMenuViewModel.show()
            viewModel.soundManager.play(SoundType.OPEN_MODAL)
        }
    }

    val pendingOverlay by dsm?.pendingOverlayEvent?.collectAsState()
        ?: remember { mutableStateOf(null) }
    LaunchedEffect(pendingOverlay) {
        val eventName = pendingOverlay ?: return@LaunchedEffect
        when (eventName) {
            DualScreenManager.OVERLAY_QUICK_MENU -> openQuickMenu()
            DualScreenManager.OVERLAY_QUICK_SETTINGS -> openQuickSettings()
            else -> openDrawer()
        }
        dsm?.clearPendingOverlay()
    }

    val saveConflictInputHandler = remember(viewModel) {
        object : InputHandler {
            override fun onLeft(): InputResult {
                viewModel.saveConflicts.moveSaveConflictFocus(-1)
                return InputResult.HANDLED
            }
            override fun onRight(): InputResult {
                viewModel.saveConflicts.moveSaveConflictFocus(1)
                return InputResult.HANDLED
            }
            override fun onUp(): InputResult {
                viewModel.saveConflicts.moveSaveConflictFocus(-1)
                return InputResult.HANDLED
            }
            override fun onDown(): InputResult {
                viewModel.saveConflicts.moveSaveConflictFocus(1)
                return InputResult.HANDLED
            }
            override fun onConfirm(): InputResult {
                viewModel.saveConflicts.confirmSaveConflict()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }
            override fun onBack(): InputResult {
                viewModel.saveConflicts.dismissSaveConflict()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }
            override fun onMenu() = InputResult.HANDLED
            override fun onSelect() = InputResult.HANDLED
            override fun onPrevSection() = InputResult.HANDLED
            override fun onNextSection() = InputResult.HANDLED
            override fun onPrevTrigger() = InputResult.HANDLED
            override fun onNextTrigger() = InputResult.HANDLED
            override fun onSecondaryAction() = InputResult.HANDLED
            override fun onContextMenu() = InputResult.HANDLED
            override fun onLeftStickClick() = InputResult.HANDLED
            override fun onRightStickClick() = InputResult.HANDLED
        }
    }

    val backgroundConflictInputHandler = remember(viewModel) {
        BackgroundConflictInputHandler(
            moveFocus = viewModel.saveConflicts::moveBackgroundConflictFocus,
            confirm = viewModel.saveConflicts::confirmBackgroundConflict,
            skip = { viewModel.saveConflicts.resolveBackgroundConflict(ConflictResolution.SKIP) }
        )
    }

    val steamLauncherLinkInputHandler = remember(viewModel) {
        object : CapturingInputHandler {
            override fun onLeft(): InputResult {
                viewModel.steamLauncherLinkController.moveFocus(-1)
                return InputResult.HANDLED
            }
            override fun onRight(): InputResult {
                viewModel.steamLauncherLinkController.moveFocus(1)
                return InputResult.HANDLED
            }
            override fun onUp(): InputResult {
                viewModel.steamLauncherLinkController.moveFocus(-1)
                return InputResult.HANDLED
            }
            override fun onDown(): InputResult {
                viewModel.steamLauncherLinkController.moveFocus(1)
                return InputResult.HANDLED
            }
            override fun onConfirm(): InputResult {
                viewModel.steamLauncherLinkController.confirmFocused()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }
            override fun onBack(): InputResult {
                viewModel.steamLauncherLinkController.dismiss()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }
        }
    }

    val netplayInviteInputHandler = remember(viewModel) {
        object : CapturingInputHandler {
            override fun onLeft(): InputResult {
                viewModel.moveNetplayInviteFocus(-1)
                return InputResult.HANDLED
            }
            override fun onRight(): InputResult {
                viewModel.moveNetplayInviteFocus(1)
                return InputResult.HANDLED
            }
            override fun onUp(): InputResult {
                viewModel.moveNetplayInviteFocus(-1)
                return InputResult.HANDLED
            }
            override fun onDown(): InputResult {
                viewModel.moveNetplayInviteFocus(1)
                return InputResult.HANDLED
            }
            override fun onConfirm(): InputResult {
                if (viewModel.netplayInviteFocusIndex.value == 1) {
                    viewModel.acceptNetplayInvite()
                } else {
                    viewModel.dismissNetplayInvite()
                }
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }
            override fun onBack(): InputResult {
                viewModel.dismissNetplayInvite()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }
        }
    }

    val netplayJoinNeedsInput = netplayJoinState.let {
        it is NetplayJoinState.VerifyingGame &&
            (it.sub is VerifySubState.AmbiguousCandidates || it.sub is VerifySubState.HashMismatchVariants)
    }
    val netplayJoinModalActive = netplayJoinState !is NetplayJoinState.Idle &&
        netplayJoinState !is NetplayJoinState.Cancelled &&
        netplayJoinState !is NetplayJoinState.LaunchReady

    val netplayJoinInputHandler = remember(viewModel) {
        NetplayJoinInputHandler(
            service = viewModel.netplayJoinService(),
            onDismiss = { viewModel.cancelNetplayJoin() }
        )
    }

    val steamLauncherLinkPrompt by viewModel.steamLauncherLinkController.prompt.collectAsState()

    val coreCrashInputHandler = remember(viewModel) {
        object : InputHandler {
            override fun onUp(): InputResult { viewModel.coreCrashController.moveFocus(-1); return InputResult.HANDLED }
            override fun onDown(): InputResult { viewModel.coreCrashController.moveFocus(1); return InputResult.HANDLED }
            override fun onLeft(): InputResult { viewModel.coreCrashController.moveFocus(-1); return InputResult.HANDLED }
            override fun onRight(): InputResult { viewModel.coreCrashController.moveFocus(1); return InputResult.HANDLED }
            override fun onConfirm(): InputResult {
                val dl = viewModel.coreCrashController.downloading.value
                if (dl?.done == true && !dl.failed) {
                    viewModel.launchFromCoreCrash()
                } else {
                    viewModel.coreCrashController.confirmFocused()
                }
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }
            override fun onBack(): InputResult {
                viewModel.coreCrashController.dismiss()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }
            override fun onMenu() = InputResult.HANDLED
            override fun onSelect() = InputResult.HANDLED
            override fun onPrevSection() = InputResult.HANDLED
            override fun onNextSection() = InputResult.HANDLED
            override fun onPrevTrigger() = InputResult.HANDLED
            override fun onNextTrigger() = InputResult.HANDLED
            override fun onSecondaryAction() = InputResult.HANDLED
            override fun onContextMenu() = InputResult.HANDLED
            override fun onLeftStickClick() = InputResult.HANDLED
            override fun onRightStickClick() = InputResult.HANDLED
        }
    }

    LaunchedEffect(coreCrashPrompt, saveConflictInfo, backgroundConflictInfo, netplayInvitePrompt, netplayJoinModalActive, netplayJoinNeedsInput, steamLauncherLinkPrompt, resumeCount) {
        inputDispatcher.setCriticalHandler(
            when {
                coreCrashPrompt != null -> coreCrashInputHandler
                saveConflictInfo != null -> saveConflictInputHandler
                backgroundConflictInfo != null -> backgroundConflictInputHandler
                else -> null
            }
        )
        when {
            steamLauncherLinkPrompt != null -> inputDispatcher.subscribeDrawer(steamLauncherLinkInputHandler)
            netplayJoinNeedsInput || netplayJoinModalActive -> inputDispatcher.subscribeDrawer(netplayJoinInputHandler)
            netplayInvitePrompt != null -> inputDispatcher.subscribeDrawer(netplayInviteInputHandler)
            else -> {
                val released = listOf(
                    steamLauncherLinkInputHandler,
                    netplayJoinInputHandler,
                    netplayInviteInputHandler
                ).any { inputDispatcher.releaseDrawer(it) }
                if (released) {
                    when {
                        viewModel.quickSettings.isOpen.value -> inputDispatcher.subscribeDrawer(quickSettingsInputHandler)
                        viewModel.isDrawerOpen.value -> inputDispatcher.subscribeDrawer(drawerInputHandler)
                        quickMenuViewModel.uiState.value.isVisible -> inputDispatcher.subscribeDrawer(quickMenuInputHandler)
                    }
                }
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.coreCrashLaunch.collect { gameId ->
            navController.navigate(Screen.GameDetail.createRoute(gameId)) {
                launchSingleTop = true
            }
            viewModel.initiateGameLaunch(gameId)
        }
    }

    LaunchedEffect(currentRoute) {
        if (currentRoute != null) {
            inputDispatcher.blockInputFor(Motion.transitionDebounceMs)
        }
        inputDispatcher.setCurrentRoute(currentRoute)
    }

    LaunchedEffect(currentRoute) {
        val isHome = currentRoute?.startsWith(Screen.Home.route) == true
        viewModel.gamepadInputHandler.homeEventEnabled = !isHome
    }

    LaunchedEffect(currentRoute, uiState.isFirstRun, navRingState.destinations.isEmpty()) {
        if (!uiState.isFirstRun && viewModel.isNavRingRoute(currentRoute)) {
            viewModel.showNavBar()
        } else {
            viewModel.hideNavBar()
        }
    }

    LaunchedEffect(isDrawerOpen) {
        if (isDrawerOpen && !drawerState.isOpen) {
            drawerState.open()
        } else if (!isDrawerOpen && drawerState.isOpen) {
            drawerState.close()
        }
    }

    // Waking from sleep can freeze the drawer half-way through its slide; on every resume it is
    // put straight back where it belongs (closed unless it's meant to be open).
    LaunchedEffect(anyResumeCount) {
        repeat(3) { attempt ->
            if (attempt > 0) delay(250)
            val closed = !viewModel.isDrawerOpen.value
            if (closed) {
                inputDispatcher.unsubscribeDrawer()
                com.nendo.argosy.util.ArcoonTrace.log(
                    "drawer resume check: offset=${drawerState.currentOffset} value=${drawerState.currentValue}"
                )
            }
            runCatching {
                drawerState.snapTo(
                    if (!closed) androidx.compose.material3.DrawerValue.Open
                    else androidx.compose.material3.DrawerValue.Closed
                )
            }
        }
    }

    val notifyOverlayClosed: () -> Unit = remember {
        {
            if (dsm != null && dsm.isOverlayFocused) {
                dsm.isOverlayFocused = false
                dsm.controlCompanion?.onOverlayClosed()
                dsm.controlCompanion?.refocusSelf()
            }
        }
    }

    LaunchedEffect(isDualScreenDevice) {
        if (!isDualScreenDevice) return@LaunchedEffect
        var wasOpen = false
        viewModel.isDrawerOpen.collect { open ->
            if (wasOpen && !open) {
                val onHome = navController.currentDestination?.route == Screen.Home.route
                if (onHome) {
                    notifyOverlayClosed()
                } else {
                    dsm?.controlCompanion?.onBackgroundForward()
                }
            }
            wasOpen = open
        }
    }

    LaunchedEffect(isDualScreenDevice, companionActive) {
        if (!isDualScreenDevice || !companionActive) return@LaunchedEffect
        var wasOnHome = true
        snapshotFlow { navBackStackEntry?.destination?.route == Screen.Home.route }
            .collect { onHome ->
                if (!wasOnHome && onHome) {
                    notifyOverlayClosed()
                }
                wasOnHome = onHome
            }
    }

    LaunchedEffect(isDualScreenDevice) {
        if (!isDualScreenDevice) return@LaunchedEffect
        var wasOpen = false
        viewModel.quickSettings.isOpen.collect { open ->
            if (wasOpen && !open) {
                val onHome = navController.currentDestination?.route == Screen.Home.route
                if (onHome) {
                    notifyOverlayClosed()
                } else {
                    dsm?.controlCompanion?.onBackgroundForward()
                }
            }
            wasOpen = open
        }
    }

    LaunchedEffect(isDualScreenDevice) {
        if (!isDualScreenDevice) return@LaunchedEffect
        var wasVisible = false
        quickMenuViewModel.uiState.collect { state ->
            if (wasVisible && !state.isVisible) {
                val onHome = navController.currentDestination?.route == Screen.Home.route
                if (onHome) {
                    notifyOverlayClosed()
                } else {
                    dsm?.controlCompanion?.onBackgroundForward()
                }
            }
            wasVisible = state.isVisible
        }
    }

    LaunchedEffect(drawerState.isOpen) {
        if (!drawerState.isOpen && isDrawerOpen) {
            inputDispatcher.unsubscribeDrawer()
            viewModel.setDrawerOpen(false)
        }
    }

    LaunchedEffect(isDrawerOpen) {
        inputDispatcher.blockInputFor(Motion.transitionDebounceMs)
    }

    LaunchedEffect(inputLifecycle, inputYieldsToPanel) {
        if (inputYieldsToPanel) return@LaunchedEffect
        viewModel.gamepadInputHandler.eventFlow().flowWithLifecycle(inputLifecycle).collect { input ->            val result = inputDispatcher.dispatch(input)
            // Temporary: a held direction that does not travel names its own failure here.
            if (input.isRepeat) {
                com.nendo.argosy.util.ArcoonTrace.log(
                    "input repeat ${input.event} handled=${result.handled} route=$currentRoute " +
                        "modal=${inputDispatcher.hasActiveModal()}"
                )
            }
            if (isQuickSettingsOpen && !result.handled) {
                com.nendo.argosy.util.ArcoonTrace.log(
                    "quick settings did not take ${input.event} (drawer=${inputDispatcher.hasActiveModal()} overlay=${inputDispatcher.hasCapturingOverlay()})"
                )
            }
            val event = input.event
            val isBumper = event == GamepadEvent.PrevSection || event == GamepadEvent.NextSection
            if (!isBumper || inputDispatcher.hasActiveModal()) viewModel.hideNavBar()
            if (!result.handled && !inputDispatcher.hasCapturingOverlay()) {
                when (event) {
                    GamepadEvent.PrevSection, GamepadEvent.NextSection -> {
                        val onCocoonBoard = navController.currentDestination?.route == Screen.Home.route &&
                            !com.nendo.argosy.ui.cocoon.CocoonMode.useClassicHome.value
                        if (onCocoonBoard && !input.isRepeat) {
                            if (isDrawerOpen) {
                                closeDrawer()
                                return@collect
                            }
                            if (!quickMenuState.isVisible) {
                                val page = if (event == GamepadEvent.NextSection) QuickSettingsPage.QUICK else QuickSettingsPage.FRIENDS
                                if (isQuickSettingsOpen) {
                                    closeQuickSettings()
                                } else {
                                    openQuickSettingsPage(page)
                                }
                                return@collect
                            }
                        }
                        val delta = if (event == GamepadEvent.PrevSection) -1 else 1
                        val target = if (
                            !input.isRepeat &&
                            !uiState.isFirstRun &&
                            !isDrawerOpen &&
                            !isQuickSettingsOpen &&
                            !quickMenuState.isVisible
                        ) {
                            viewModel.navRingRouteFrom(navController.currentDestination?.route, delta)
                        } else {
                            null
                        }
                        if (target != null) {
                            viewModel.hapticManager.vibrate(HapticPattern.FOCUS_CHANGE)
                            viewModel.soundManager.play(SoundType.SECTION_CHANGE)
                            navigateFromDrawer(target)
                            viewModel.showNavBar()
                        }
                    }
                    GamepadEvent.Menu -> {
                        if (isDrawerOpen) {
                            closeDrawer()
                        } else {
                            if (isQuickSettingsOpen) closeQuickSettings()
                            if (quickMenuState.isVisible) closeQuickMenu()
                            openDrawer()
                        }
                    }
                    GamepadEvent.Left -> {
                        if (!input.isRepeat &&
                            !isDrawerOpen &&
                            !isQuickSettingsOpen &&
                            !quickMenuState.isVisible
                        ) {
                            openDrawer()
                        }
                    }
                    GamepadEvent.Select -> {
                        if (com.nendo.argosy.ui.dualscreen.selectSwapsRoles()) dsm?.swapRoles()
                    }
                    GamepadEvent.LongSelect -> {
                        if (com.nendo.argosy.ui.dualscreen.selectHoldSwapsRoles()) dsm?.swapRoles()
                    }
                    GamepadEvent.LeftStickClick -> {
                        if (quickMenuState.isVisible) {
                            closeQuickMenu()
                        } else {
                            if (isDrawerOpen) closeDrawer()
                            if (isQuickSettingsOpen) closeQuickSettings()
                            openQuickMenu()
                        }
                    }
                    GamepadEvent.RightStickClick -> {
                        if (isQuickSettingsOpen) {
                            closeQuickSettings()
                        } else {
                            if (isDrawerOpen) closeDrawer()
                            if (quickMenuState.isVisible) closeQuickMenu()
                            openQuickSettings()
                        }
                    }
                    GamepadEvent.Home -> {
                        if (isDrawerOpen) closeDrawer()
                        if (isQuickSettingsOpen) closeQuickSettings()
                        if (quickMenuState.isVisible) closeQuickMenu()
                        val homeRoute = Screen.Home.route
                        val sessions = dsm?.gameSessionManager
                        val cameFromGame = sessions?.sessionState?.value != null ||
                            isEmulatorRunning || sessions?.recentlySuspended() == true
                        if (!cameFromGame && currentRoute != homeRoute) {
                            navController.navigate(homeRoute) {
                                popUpTo(homeRoute) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    val shortcutGate = remember(inputDispatcher, navController) {
        object : UiShortcutGate {
            override fun hotkeysAllowed(): Boolean =
                !uiState.isFirstRun &&
                    !isEmulatorRunning &&
                    navController.currentDestination?.route != Screen.FirstRun.route &&
                    !inputDispatcher.hasCapturingOverlay()

            override fun longBackAllowed(): Boolean =
                hotkeysAllowed() &&
                    !isDrawerOpen &&
                    !isQuickSettingsOpen &&
                    !quickMenuState.isVisible
        }
    }

    DisposableEffect(shortcutGate) {
        val handler = viewModel.gamepadInputHandler
        handler.attachShortcutGate(shortcutGate)
        onDispose { handler.detachShortcutGate(shortcutGate) }
    }

    LaunchedEffect(inputLifecycle, inputYieldsToPanel) {
        if (inputYieldsToPanel) return@LaunchedEffect
        viewModel.gamepadInputHandler.shortcutEventFlow().flowWithLifecycle(inputLifecycle).collect { shortcut ->
            when (shortcut) {
                UiShortcut.OPEN_NAVIGATION -> {
                    if (isDrawerOpen) {
                        closeDrawer()
                    } else {
                        if (isQuickSettingsOpen) closeQuickSettings()
                        if (quickMenuState.isVisible) closeQuickMenu()
                        openDrawer()
                    }
                }
                UiShortcut.OPEN_QUICK_PANEL -> {
                    if (isQuickSettingsOpen) {
                        closeQuickSettings()
                    } else {
                        if (isDrawerOpen) closeDrawer()
                        if (quickMenuState.isVisible) closeQuickMenu()
                        openQuickSettings()
                    }
                }
            }
        }
    }

    LaunchedEffect(inputLifecycle, inputYieldsToPanel) {
        if (inputYieldsToPanel) return@LaunchedEffect
        viewModel.gamepadInputHandler.homeEventFlow().flowWithLifecycle(inputLifecycle).collect {
            // Home pressed inside a game leaves the launcher where the game was started (its
            // folder, with the game focused), as in Cocoon; it is not a trip back to the board.
            val sessions = com.nendo.argosy.DualScreenManagerHolder.instance?.gameSessionManager
            val activeSession = sessions?.sessionState?.value != null || isEmulatorRunning
            val cameFromGame = activeSession || sessions?.recentlySuspended() == true
            // A suspended game keeps its session open, but the launcher is in front: Home works.
            val gameInFront = isEmulatorRunning && sessions?.sessionState?.value == null
            if (gameInFront || cameFromGame) {
            } else {
                if (navController.currentDestination != null) {
                    if (isDrawerOpen) closeDrawer()
                    if (isQuickSettingsOpen) closeQuickSettings()
                    if (quickMenuState.isVisible) closeQuickMenu()
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    val isScrapingArtwork by viewModel.imageCacheManager.progress
        .collectAsState()
        .let { state -> remember { derivedStateOf { state.value.isProcessing } } }

    val localAvatarInfo by remember {
        derivedStateOf {
            com.nendo.argosy.ui.components.friends.LocalUserAvatarInfo(
                userId = drawerUiState.localUser?.id,
                doodle = drawerUiState.localAvatarDoodle
            )
        }
    }

    val frostedBackdrop = rememberFrostedBackdrop()
    val screenFrostedBackdrop = rememberFrostedBackdrop()
    val hasCapturingOverlay = inputDispatcher.hasCapturingOverlay()
    LaunchedEffect(hasCapturingOverlay) {
        if (hasCapturingOverlay) viewModel.hideNavBar()
    }
    CompositionLocalProvider(
        LocalInputDispatcher provides inputDispatcher,
        com.nendo.argosy.ui.input.LocalModalPresence provides inputDispatcher,
        com.nendo.argosy.ui.input.LocalHaptics provides viewModel.hapticManager,
        LocalGamepadInputHandler provides viewModel.gamepadInputHandler,
        LocalABIconsSwapped provides uiState.abIconsSwapped,
        LocalXYIconsSwapped provides uiState.xyIconsSwapped,
        LocalSwapStartSelect provides uiState.swapStartSelect,
        LocalFooterHost provides footerHostController,
        LocalFooterHostVisible provides (!presentationShowsHints && !navRingState.footerYieldsToBar),
        LocalFrostedBackdrop provides frostedBackdrop,
        com.nendo.argosy.ui.common.LocalImageCacheManager provides viewModel.imageCacheManager,
        com.nendo.argosy.ui.components.LocalArtworkScraping provides isScrapingArtwork,
        com.nendo.argosy.ui.components.LocalStatusBarItems provides
            com.nendo.argosy.ui.components.StatusBarItems(
                clock = uiState.showStatusClock,
                battery = uiState.showStatusBattery,
                network = uiState.showStatusNetwork
            ),
        com.nendo.argosy.ui.components.friends.LocalUserAvatarState provides localAvatarInfo
    ) {
        if (uiState.isLoading) {
            AppSplashScreen(statusRes = uiState.startupStatusRes)
            return@CompositionLocalProvider
        }

        val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
        val scrimColor = if (isDarkTheme) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.35f)
        val lastUserActivityAtMs by (
            dsm?.lastUserActivityAtMs
                ?: remember { kotlinx.coroutines.flow.MutableStateFlow(0L) }
            ).collectAsState()
        val mediaPlayback by (
            dsm?.mediaPlayback
                ?: remember { kotlinx.coroutines.flow.MutableStateFlow(null) }
            ).collectAsState()
        val dimmerEnabled = dsm != null && screenDimmerPrefs.enabled && !isEmulatorRunning &&
            !uiState.isFirstRun && mediaPlayback == null
        val bottomReserved = gripReserveBottomInset()

        ScreenDimmerOverlay(
            enabled = dimmerEnabled,
            timeoutMs = screenDimmerPrefs.timeoutMinutes * 60_000L,
            dimLevel = screenDimmerPrefs.level / 100f,
            lastActivityAtMs = lastUserActivityAtMs,
            onWake = { dsm?.notifyUserActivity("mainDimmerTap") }
        ) {
            var keySinkFocused by remember { mutableStateOf(false) }
            val imeManager = remember(context) {
                context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            }

            val onRingDestination = navRingState.destinations.any { NavRing.routeMatches(it.route, currentRoute) }
            val homeAppBarOwnsBottom = currentRoute == Screen.Home.route &&
                presentationShowsHints
            val appPromptShowing = saveConflictInfo != null ||
                backgroundConflictInfo != null ||
                coreCrashPrompt != null ||
                netplayInvitePrompt != null ||
                netplayJoinModalActive ||
                steamLauncherLinkPrompt != null
            val navBarAllowed = onRingDestination &&
                !hasCapturingOverlay &&
                !homeAppBarOwnsBottom &&
                !uiState.isFirstRun &&
                !isDrawerOpen &&
                !isQuickSettingsOpen &&
                !quickMenuState.isVisible &&
                !appPromptShowing
            val currentNavBarAllowed by rememberUpdatedState(navBarAllowed)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(bottom = bottomReserved)
                    .revealOnBottomEdgeTouch(edgeHeight = Dimens.footerHeight) { windowPosition ->
                        val ringAtPress = viewModel.navRingState.value
                        val pressOnFooterHint = footerHostController.hasTappableHintAt(windowPosition)
                        val claimsPress = NavBarVisibility.revealClaimsPress(
                            navBarAllowed = currentNavBarAllowed,
                            barVisible = ringAtPress.isBarVisible,
                            destinationCount = ringAtPress.destinations.size,
                            pressOnFooterHint = pressOnFooterHint
                        )
                        if (NavBarVisibility.revealRaisesBar(currentNavBarAllowed, pressOnFooterHint)) {
                            viewModel.showNavBar()
                        }
                        claimsPress
                    }
                    .onFocusChanged { keySinkFocused = it.isFocused }
                    .focusRequester(rootFocusRequester)
                    .focusable()
                    .doubleTapNoFocus { openQuickMenu() }
            ) {
                /**
                 * The key sink has to take focus back whenever something else has had it.
                 *
                 * Gamepad input reaches the app through this node, and on a dual-screen handheld it
                 * is also what forwards keys to the lower display, so a composable that takes focus
                 * and does not give it back leaves both screens deaf until a touch clears it.
                 *
                 * Every tap is a way to lose it: Compose makes a clickable node focusable and moves
                 * focus to it on press, so watching for a lost sink is the only reclaim that covers
                 * the real cause rather than the few state changes we thought to enumerate. Typing
                 * is the one time something else is meant to hold focus, and a raised keyboard is
                 * what says so.
                 *
                 * A text field takes focus before the keyboard it asks for is up, so the reclaim
                 * waits out that gap and then asks the input manager directly whether a field is
                 * connected. The window inset is not trusted for this: it reads zero here even
                 * with the keyboard on screen, which is what made every text field unusable.
                 */
                LaunchedEffect(keySinkFocused, drawerState.isOpen, uiState.isFirstRun) {
                    if (!keySinkFocused && !drawerState.isOpen) {
                        delay(KEY_SINK_RECLAIM_GRACE_MS)
                        if (imeManager?.isAcceptingText == true) return@LaunchedEffect
                        // A role swap rebuilds this sink; a reclaim that lands while the new
                        // node is not attached yet throws. The next lost-focus event asks again.
                        runCatching { rootFocusRequester.requestFocus() }
                    }
                }

                val keyboardToggle by dsm?.keyboardToggleEvent?.collectAsState()
                    ?: remember { mutableStateOf(0L) }
                LaunchedEffect(keyboardToggle) {
                    if (keyboardToggle == 0L) return@LaunchedEffect
                    delay(KEY_SINK_RECLAIM_GRACE_MS)
                    @Suppress("DEPRECATION")
                    imeManager?.toggleSoftInput(android.view.inputmethod.InputMethodManager.SHOW_FORCED, 0)
                }

                var drawerWidthPx by remember { mutableStateOf(0f) }

                ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = !uiState.isFirstRun,
                scrimColor = scrimColor,
                drawerContent = {
                    /*
                     * After the screen sleeps, the drawer's resting place can be measured before
                     * the screen is back to full size, leaving a strip of it on the left edge. A
                     * closed drawer that isn't moving is therefore never drawn at all.
                     */
                    val drawerShown by remember(drawerState) {
                        derivedStateOf {
                            drawerState.isAnimationRunning ||
                                drawerState.currentValue == androidx.compose.material3.DrawerValue.Open ||
                                drawerState.targetValue == androidx.compose.material3.DrawerValue.Open
                        }
                    }
                    val drawerOpenAnimProgress by animateFloatAsState(
                        targetValue = if (isDrawerOpen || drawerState.isOpen) 1f else 0f,
                        animationSpec = tween(280, easing = com.nendo.argosy.ui.theme.Motion.argosyEase),
                        label = "drawer-open-anim"
                    )
                    Box(
                        modifier = Modifier.graphicsLayer {
                            alpha = (if (drawerShown || isDrawerOpen) 1f else 0f) * drawerOpenAnimProgress.coerceIn(0.1f, 1f)
                            val scale = 0.95f + 0.05f * drawerOpenAnimProgress
                            scaleX = scale
                            scaleY = scale
                        }
                    ) {
                    MainDrawer(
                        items = drawerItems,
                        currentRoute = currentRoute,
                        drawerState = drawerUiState,
                        isOpen = isDrawerOpen,
                        isSettled = isDrawerOpen &&
                            drawerState.currentValue == androidx.compose.material3.DrawerValue.Open &&
                            !drawerState.isAnimationRunning,
                        onNavigate = { route ->
                            inputDispatcher.unsubscribeDrawer()
                            viewModel.setDrawerOpen(false)
                            closeDrawerThenNavigate(route)
                        },
                        onResumeGame = resumeSuspendedGame,
                        modifier = Modifier.onSizeChanged { drawerWidthPx = it.width.toFloat() }
                    )
                    }
                }
            ) {
                val drawerBlurProgress by remember(drawerState) {
                    derivedStateOf {
                        val offset = drawerState.currentOffset
                        val width = drawerWidthPx
                        if (offset.isNaN() || width <= 0f || (!isDrawerOpen && !drawerState.isAnimationRunning)) {
                            0f
                        } else {
                            (1f + offset / width).coerceIn(0f, 1f)
                        }
                    }
                }
                val quickMenuBlur by androidx.compose.animation.core.animateDpAsState(
                    targetValue = if (quickMenuState.isVisible) Motion.blurRadiusDrawer else 0.dp,
                    animationSpec = androidx.compose.animation.core.tween(200),
                    label = "quickMenuBlur"
                )
                val launchRecapBlur = com.nendo.argosy.ui.components.launchRecapBackdropBlur()
                val monthlyRecapBlur = com.nendo.argosy.ui.components.recap.monthlyRecapBackdropBlur()
                // Cocoon blurs Home behind the quick settings / friends drawer too.
                val quickSettingsBlur by androidx.compose.animation.core.animateDpAsState(
                    targetValue = if (isQuickSettingsOpen) Motion.blurRadiusDrawer else 0.dp,
                    animationSpec = androidx.compose.animation.core.tween(Motion.durationSlide),
                    label = "quickSettingsBlur"
                )

                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val monthlyRecapShowing = com.nendo.argosy.ui.components.recap.monthlyRecapShowing()
                    val density = LocalDensity.current
                    var navBarHeight by remember { mutableStateOf(0.dp) }
                    val navBarHoldsSpace = navRingState.isAlwaysVisible && onRingDestination &&
                        !homeAppBarOwnsBottom && !uiState.isFirstRun && navBarHeight > 0.dp
                    val navBarClearance = if (navBarHoldsSpace) navBarHeight + Dimens.spacingSm else 0.dp
                    val navBarLift by androidx.compose.animation.core.animateDpAsState(
                        targetValue = if (
                            navRingState.isAlwaysVisible && !presentationShowsHints && footerHostController.isBarVisible
                        ) {
                            Dimens.footerHeight
                        } else {
                            0.dp
                        },
                        animationSpec = tween(Motion.durationSlide),
                        label = "navBarLift"
                    )
                    CompositionLocalProvider(
                        LocalTransientHomeStatusVisible provides (
                            currentRoute == Screen.Home.route && maxWidth <= maxHeight &&
                                !presentationShowsHints && navBarAllowed && navRingState.isBarVisible
                            ),
                        LocalFrostedBackdrop provides screenFrostedBackdrop,
                        LocalNavBarClearance provides navBarClearance
                    ) {
                        // The pages and the board kept alive under them share the drawer's blur and
                        // the frosted-glass backdrop, as the pages alone used to.
                        Box(
                            modifier = Modifier
                            .fillMaxSize()
                            .frostedBackdropSource(frostedBackdrop)
                            // Read while drawing: the animated blur repaints, it no longer re-runs
                            // this whole screen on every frame of the drawer or a menu opening.
                            .deferredBlur {
                                maxOf(
                                    (drawerBlurProgress * Motion.blurRadiusDrawer.value).dp,
                                    quickMenuBlur,
                                    quickSettingsBlur,
                                    launchRecapBlur,
                                    monthlyRecapBlur
                                )
                            }
                            // Settings opened on the lower screen floats up to the upper one;
                            // this screen never draws its own copy, not even for the moment
                            // before the move.
                            .then(
                                if (hidesSettingsHere) {
                                    Modifier.graphicsLayer { alpha = 0f }
                                } else {
                                    Modifier
                                }
                            )
                        ) {
                        // The Cocoon board kept alive underneath the pages: hidden (not drawn, no
                        // input) while a folder or Settings is open, shown again at once on return.
                        val classicHome by com.nendo.argosy.ui.cocoon.CocoonMode.useClassicHome.collectAsState()
                        val keepHomeAlive = com.nendo.argosy.ui.navigation.KEEP_COCOON_HOME_ALIVE &&
                            !asLiftedPanel && !classicHome
                        val homeActive = currentRoute == Screen.Home.route
                        var homeSeen by remember { mutableStateOf(false) }
                        if (homeActive) homeSeen = true
                        if (keepHomeAlive && homeSeen) {
                            val homeAlpha by animateFloatAsState(
                                targetValue = if (homeActive) 1f else 0f,
                                animationSpec = tween(if (homeActive) 120 else 100),
                                label = "cocoonHomeLayer"
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .layout { measurable, constraints ->
                                        val placeable = measurable.measure(constraints)
                                        layout(placeable.width, placeable.height) {
                                            // fully hidden: not placed, so not drawn and not touchable
                                            if (homeAlpha > 0f) placeable.place(0, 0)
                                        }
                                    }
                                    .graphicsLayer { alpha = homeAlpha }
                            ) {
                                com.nendo.argosy.ui.navigation.CocoonHomeHost(navController, active = homeActive)
                            }
                        }
                        NavGraph(
                            cocoonHomeOutside = keepHomeAlive,
                            navController = navController,
                            startDestination = startDestination,
                            // The floating panel closes straight from Settings: going Home first
                            // drew Home in the panel and took over the top screen's highlight.
                            settingsShownElsewhere = !asLiftedPanel &&
                                (settingsLiftOwner != null || dsm?.companionHoldsPrimaryNow() == true),
                            onSettingsExit = if (asLiftedPanel) {
                                { settingsLiftOwner?.let { dsm?.dropSettingsLift(it) } }
                            } else {
                                null
                            },
                            onDrawerToggle = { if (isDrawerOpen) closeDrawer() else openDrawer() },
                            argosyViewModel = viewModel,
                            videoPreviewBlocked = isDrawerOpen || isQuickSettingsOpen ||
                                quickMenuState.isVisible || appPromptShowing || monthlyRecapShowing,
                            onPlayMedia = { itemId, startOver ->
                                dsm?.playMediaItem(itemId, startOver)
                                    ?: PlayerActivity.start(
                                        context = context,
                                        args = PlayerArgs(
                                            itemId = itemId,
                                            startPositionMs = if (startOver) 0L else -1L
                                        )
                                    )
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        }
                    }

                    FloatingNavBar(
                        visible = navBarAllowed && navRingState.isBarVisible,
                        destinations = navRingState.destinations,
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            navigateFromDrawer(route)
                            viewModel.showNavBar()
                        },
                        onInteract = { viewModel.showNavBar() },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = Dimens.spacingSm + navBarLift)
                            .onSizeChanged { size ->
                                if (size.height > 0) navBarHeight = with(density) { size.height.toDp() }
                            },
                        badgeFor = drawerUiState::badgeCountFor
                    )
                }
            }

            com.nendo.argosy.ui.components.recap.MonthlyRecapOverlay()

            com.nendo.argosy.ui.components.LaunchOverlay()

            val suspendedSessionToQuit = suspendedGameSession?.takeIf { it.builtIn }
            com.nendo.argosy.ui.primitives.ArgosyConfirmModalHost(
                visible = quitSuspendedGameId != null &&
                    quitSuspendedGameId == suspendedSessionToQuit?.gameId,
                title = stringResource(R.string.ui_drawer_resume_quit_title),
                message = stringResource(
                    R.string.ui_drawer_resume_quit_message,
                    suspendedSessionToQuit?.title.orEmpty()
                ),
                confirmLabel = stringResource(R.string.ui_drawer_resume_quit_confirm),
                destructive = true,
                onConfirm = {
                    val targetGameId = suspendedSessionToQuit?.gameId
                    val currentSession = dsm?.gameSessionManager?.sessionState?.value
                    if (targetGameId != null &&
                        currentSession?.gameId == targetGameId &&
                        currentSession.builtIn
                    ) {
                        dsm?.gameSessionManager?.closeSession()
                    }
                    quitSuspendedGameId = null
                    inputDispatcher.unsubscribeDrawer()
                    viewModel.setDrawerOpen(false)
                    scope.launch { drawerState.close() }
                },
                onDismiss = { quitSuspendedGameId = null }
            )

            val mutedNotificationKeys = if (currentRoute == Screen.SyncMonitor.route) {
                com.nendo.argosy.domain.usecase.sync.SyncNotificationKeys.ALL
            } else {
                emptySet()
            }
            LaunchedEffect(mutedNotificationKeys) { dsm?.setMutedNotificationKeys(mutedNotificationKeys) }
            val notificationsOnPresentation by dsm?.companionHoldsPrimary?.collectAsState()
                ?: remember { mutableStateOf(false) }
            if (!notificationsOnPresentation) {
                NotificationHost(
                    manager = viewModel.notificationManager,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    mutedKeys = mutedNotificationKeys
                )
            }

            com.nendo.argosy.core.notification.ScreenSetPrompt(
                visible = screenSetPromptVisible,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = Dimens.spacingMd, bottom = Dimens.headerHeight)
            )

            androidx.compose.runtime.CompositionLocalProvider(
                com.nendo.argosy.ui.primitives.LocalCocoonCursorSuppressed provides !quickMenuState.isVisible
            ) {
                QuickMenuOverlay(
                    viewModel = quickMenuViewModel,
                    onGameSelect = { gameId ->
                        closeQuickMenu()
                        navController.navigate(Screen.GameDetail.createRoute(gameId)) {
                            launchSingleTop = true
                        }
                    },
                    onGamePlay = playFromQuickMenu,
                    closeQuickMenu = closeQuickMenu
                )
            }

            val musicNeedsSignIn = musicPageVisible &&
                musicBrowseNotice ==
                com.nendo.argosy.ui.components.musicplayer.MusicBrowseNotice.SIGN_IN_FOR_PLAYLISTS
            val signInHint = stringResource(R.string.ui_quick_settings_music_hint_sign_in)
            androidx.compose.runtime.CompositionLocalProvider(
                com.nendo.argosy.ui.primitives.LocalCocoonCursorSuppressed provides !isQuickSettingsOpen
            ) {
                QuickSettingsPanel(
                    onHintClick = { button ->
                        if (button == com.nendo.argosy.ui.components.InputButton.Y && musicNeedsSignIn) {
                            openRommSignIn()
                        }
                    },
                    isVisible = isQuickSettingsOpen,
                    state = quickSettingsState,
                    page = quickSettingsPage,
                    focusedIndex = quickSettingsFocusIndex,
                    controller = viewModel.quickSettings,
                    onOpenDeviceAccess = openDeviceAccess,
                    friendsPage = {
                        QuickSettingsFriendsPage(
                            state = quickFriendsState,
                            showQuayPass = quickSettingsState.isSocialLinked,
                            quayPassEnabled = quickSettingsState.quayPassEnabled,
                            onRowClick = { quickFriendsInputHandler.tapRow(it) },
                            onRowLongClick = { quickFriendsInputHandler.longPressRow(it) },
                            onActionClick = { index, action -> quickFriendsInputHandler.tapAction(index, action) }
                        )
                    },
                    connectionsPage = {
                        QuickSettingsConnectionsPage(
                            state = quickConnectionsState,
                            onRowClick = quickConnectionsInputHandler::tapRow,
                            onShowList = quickConnectionsInputHandler::tapList,
                            onForget = quickConnectionsInputHandler::tapForget
                        )
                    },
                    musicPage = {
                        QuickSettingsMusicPage(viewModel = musicPlayerViewModel, onOpenRommSignIn = openRommSignIn)
                    },
                    onDismiss = closeQuickSettings,
                    footerHints = listOfNotNull(
                        (com.nendo.argosy.ui.components.InputButton.Y to signInHint).takeIf { musicNeedsSignIn }
                    )
                )
            }

            QuickFriendsModals(
                state = quickFriendsState,
                controller = viewModel.quickFriends,
                onOpenProfile = quickFriendsInputHandler::openProfile,
                onEditAvatar = quickFriendsInputHandler::editAvatar
            )

            QuickConnectionsModals(state = quickConnectionsState, controller = viewModel.quickConnections)

            saveConflictInfo?.let { info ->
                SaveConflictModal(
                    info = info,
                    focusedButton = saveConflictButtonIndex,
                    onKeepLocal = { viewModel.saveConflicts.dismissSaveConflict() },
                    onOverwrite = { viewModel.saveConflicts.forceUploadConflictSave() },
                    onSnapshotChoice = viewModel.saveConflicts::answerSnapshotConflict
                )
            }

            backgroundConflictInfo?.let { info ->
                BackgroundSyncConflictDialog(
                    conflictInfo = info,
                    focusIndex = backgroundConflictButtonIndex,
                    onKeepLocal = { viewModel.saveConflicts.resolveBackgroundConflict(ConflictResolution.KEEP_LOCAL) },
                    onKeepServer = { viewModel.saveConflicts.resolveBackgroundConflict(ConflictResolution.KEEP_SERVER) },
                    onSkip = { viewModel.saveConflicts.resolveBackgroundConflict(ConflictResolution.SKIP) },
                    snapshotConflict = backgroundConflictSnapshot,
                    onSnapshotChoice = viewModel.saveConflicts::resolveBackgroundSnapshotConflict
                )
            }

            val sessionEndOverlay by viewModel.sessionEndOverlay.collectAsState()
            SyncOverlay(
                syncProgress = sessionEndOverlay?.syncProgress,
                gameTitle = sessionEndOverlay?.gameTitle,
                onGrantPermission = sessionEndOverlay?.onGrantPermission,
                onDisableSync = sessionEndOverlay?.onDisableSync,
                onOpenSettings = sessionEndOverlay?.onOpenSettings,
                onSkip = sessionEndOverlay?.onSkip
            )

            coreCrashPrompt?.let { prompt ->
                CoreCrashModal(
                    prompt = prompt,
                    focusedIndex = coreCrashFocusIndex,
                    downloading = coreCrashDownloading,
                    onSelect = { index ->
                        viewModel.coreCrashController.setFocus(index)
                        viewModel.coreCrashController.confirmFocused()
                    },
                    onDismiss = { viewModel.coreCrashController.dismiss() }
                )
            }

            val pushedApk = pushedApkPrompt
            com.nendo.argosy.ui.primitives.ArgosyConfirmModalHost(
                visible = pushedApk != null,
                title = stringResource(R.string.ui_pushed_apk_title, pushedApk?.title.orEmpty()),
                message = stringResource(
                    R.string.ui_pushed_apk_message,
                    pushedApk?.serverHost ?: stringResource(R.string.ui_pushed_apk_unknown_server),
                    pushedApk?.packageName.orEmpty(),
                    pushedApk?.signerSha256 ?: stringResource(R.string.ui_pushed_apk_unknown_signer)
                ),
                confirmLabel = stringResource(R.string.ui_pushed_apk_install),
                cancelLabel = stringResource(R.string.ui_pushed_apk_not_now),
                onConfirm = { viewModel.apkInstallManager.confirmPushedInstall() },
                onDismiss = { viewModel.apkInstallManager.dismissPushedInstall() }
            )

            netplayInvitePrompt?.let { invite ->
                NetplayInviteModal(
                    invite = invite,
                    focusedButton = netplayInviteFocusIndex,
                    onJoin = { viewModel.acceptNetplayInvite() },
                    onDismiss = { viewModel.dismissNetplayInvite() }
                )
            }

            NetplayJoinModal(
                state = netplayJoinState,
                onDismiss = { viewModel.cancelNetplayJoin() }
            )

            val steamLauncherLinkFocusIndex by viewModel.steamLauncherLinkController.focusIndex.collectAsState()
            val steamMarkOptions by viewModel.steamLauncherLinkController.markOptions.collectAsState()
            steamLauncherLinkPrompt?.let { prompt ->
                com.nendo.argosy.ui.components.SteamLauncherLinkModal(
                    prompt = prompt,
                    focusIndex = steamLauncherLinkFocusIndex,
                    markOptions = steamMarkOptions,
                    onMarkAsInstalled = { pkg -> viewModel.steamLauncherLinkController.confirmMarkInstalled(pkg) },
                    onDismiss = { viewModel.steamLauncherLinkController.dismiss() }
                )
            }

            if (presentationShowsHints) {
                val topEntry = footerHostController.top
                val relayed = topEntry?.hints.orEmpty().map {
                    com.nendo.argosy.ui.dualscreen.CompanionHint(it.button, it.action, forced = topEntry?.forced == true)
                }
                LaunchedEffect(relayed) {
                    com.nendo.argosy.DualScreenManagerHolder.instance?.publishControlHints(relayed)
                }
            } else {
                AnimatedVisibility(
                    visible = !navRingState.footerYieldsToBar,
                    enter = slideInVertically(tween(Motion.durationSlide)) { it },
                    exit = slideOutVertically(tween(Motion.durationSlide)) { it },
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    FooterHost(controller = footerHostController)
                }
            }
            // First-run / after-update optimization: progress and Skip on this (launcher) screen.
            com.nendo.argosy.ui.cocoon.CocoonOptimizeControls(showWordmark = !presentationShowsHints)
            }
        }
    }
}

@Composable
private fun AppSplashScreen(@StringRes statusRes: Int?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.spacingLg)
        ) {
            androidx.compose.material3.Text(
                text = androidx.compose.ui.res.stringResource(R.string.argosyapp_splash_title),
                style = androidx.compose.material3.MaterialTheme.typography.headlineLarge,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                letterSpacing = 8.sp
            )
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(Dimens.iconLg),
                color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                trackColor = androidx.compose.material3.MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                strokeWidth = Dimens.borderMedium
            )
            if (statusRes != null) {
                androidx.compose.material3.Text(
                    text = stringResource(statusRes),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }
    }
}
