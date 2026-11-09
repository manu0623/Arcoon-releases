package com.nendo.argosy.ui.cocoon

import com.nendo.argosy.ui.common.deferredBlur
import androidx.compose.material.icons.outlined.Add
import androidx.compose.foundation.background
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.wrapContentSize
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.verticalScroll
import com.nendo.argosy.ui.cocoon.theme.cocoonDockIconClip
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.TableRows
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.animation.core.animateFloat
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nendo.argosy.R
import com.nendo.argosy.ui.dualscreen.PresentOnCompanion
import com.nendo.argosy.ui.dualscreen.PresentationSlot
import com.nendo.argosy.ui.dualscreen.SlotOwner
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.primitives.cocoonBrackets
import com.nendo.argosy.ui.theme.CocoonSurface
import com.nendo.argosy.ui.util.clickableNoFocus

private val BOARD_OWNER = SlotOwner("cocoon-board")

/**
 * Cocoon's home board: three rows of folders that run off to the right. On a two-screen device the
 * focused folder is drawn large on the presentation screen; on one screen it sits above the board.
 */
@Composable
fun CocoonHomeScreen(
    onOpenPlatform: (Long) -> Unit,
    onOpenCollection: (Long) -> Unit,
    onOpenAndroidGames: () -> Unit,
    onOpenEmulators: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditPlatform: (Long) -> Unit = {},
    onNavigate: (String) -> Unit = {},
    /**
     * False while Home is kept alive underneath another screen (a folder, Settings): it then takes
     * no input and does not drive the top screen. Becoming true again is "coming back".
     */
    active: Boolean = true,
    viewModel: CocoonHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val manager by com.nendo.argosy.DualScreenManagerHolder.instanceFlow.collectAsState()
    // The game left running keeps a square on the board. It is read from the session itself, so it
    // goes the moment that game is resumed or quits.
    val suspended by (
        manager?.gameSessionManager?.sessionState
            ?: remember {
                kotlinx.coroutines.flow.MutableStateFlow<
                    com.nendo.argosy.domain.model.multitasking.SuspendedGameSession?>(null)
            }
        ).collectAsState()
    LaunchedEffect(suspended) { viewModel.setSuspendedSession(suspended) }
    val hasPresentation by (
        manager?.hasPresentationScreen ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }
        ).collectAsState()
    var returningFromFolder by remember { androidx.compose.runtime.mutableStateOf(CocoonFolderZoom.returning) }

    // Folder open: the board zooms into the folder and fades as the folder screen comes in; coming
    // back it zooms out of the same folder (the reverse).
    val folderZoom = remember {
        androidx.compose.animation.core.Animatable(if (CocoonFolderZoom.returning) 1f else 0f)
    }
    LaunchedEffect(returningFromFolder) {
        if (!returningFromFolder) return@LaunchedEffect
        CocoonFolderZoom.returning = false
        CocoonFolderZoom.opening = false
        folderZoom.animateTo(0f, androidx.compose.animation.core.tween(190, easing = com.nendo.argosy.ui.theme.Motion.argosyEase))
        returningFromFolder = false
    }
    CocoonFolderZoom.progress = folderZoom
    val zoomScope = androidx.compose.runtime.rememberCoroutineScope()
    fun zoomInto(then: () -> Unit) {
        if (folderZoom.isRunning) return
        zoomScope.launch {
            CocoonFolderZoom.returning = true
            CocoonFolderZoom.opening = true
            folderZoom.animateTo(1f, androidx.compose.animation.core.tween(140, easing = androidx.compose.animation.core.FastOutLinearInEasing))
            then()
        }
    }

    val open: (CocoonTile) -> Unit = remember(onOpenPlatform, onOpenCollection, onOpenAndroidGames, onOpenEmulators, onOpenSettings) {
        { tile ->
            when (val target = tile.target) {
                is CocoonTileTarget.Platform -> zoomInto { onOpenPlatform(target.platformId) }
                is CocoonTileTarget.Collection -> zoomInto { onOpenCollection(target.collectionId) }
                is CocoonTileTarget.Smart -> zoomInto { viewModel.openHomeTile(tile) }
                CocoonTileTarget.AndroidGames -> onOpenAndroidGames()
                CocoonTileTarget.Emulators -> onOpenEmulators()
                CocoonTileTarget.Settings -> onOpenSettings()
                is CocoonTileTarget.App, is CocoonTileTarget.Pod, is CocoonTileTarget.Widget,
                is CocoonTileTarget.Game, is CocoonTileTarget.Group,
                is CocoonTileTarget.NowPlaying -> viewModel.openHomeTile(tile)
                CocoonTileTarget.Gap, is CocoonTileTarget.Covered -> Unit
            }
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CocoonHomeEvent.LaunchApp -> try {
                    context.startActivity(event.intent, event.options)
                } catch (e: Exception) {
                    android.util.Log.e("CocoonHome", "Failed to launch app", e)
                }
                is CocoonHomeEvent.Navigate -> onNavigate(event.route)
            }
        }
    }

    val inputDispatcher = LocalInputDispatcher.current
    val inputHandler = remember(open, onOpenSettings) {
        viewModel.createInputHandler(
            onOpen = open,
            onOpenSettings = onOpenSettings,
            onUseClassicHome = { CocoonMode.requestClassicHome(true) },
            onOpenAllApps = onOpenAndroidGames,
            onEditTile = { tile ->
                (tile.target as? CocoonTileTarget.Platform)?.let { onEditPlatform(it.platformId) }
            }
        )
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val isActive by androidx.compose.runtime.rememberUpdatedState(active)
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && isActive) {
                inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_HOME)
                viewModel.refreshCollections()
                if (CocoonFolderZoom.returning) returningFromFolder = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (isActive) inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_HOME)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Kept alive underneath: coming back to Home does what a fresh Home used to do on arrival
    // (take the pad, refresh, zoom back out of the folder) without rebuilding the board.
    var wasActive by remember { androidx.compose.runtime.mutableStateOf(active) }
    LaunchedEffect(active) {
        if (active && !wasActive) {
            inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_HOME)
            viewModel.refreshCollections()
            if (CocoonFolderZoom.returning) returningFromFolder = true
        }
        wasActive = active
    }

    val focused = state.focusedTile
    if (focused != null && active) {
        val artGameId = (focused.target as? CocoonTileTarget.NowPlaying)?.gameId
            ?: (focused.target as? CocoonTileTarget.Game)?.gameId
        val art = artGameId?.let { com.nendo.argosy.ui.common.rememberResolvedArt(it) }
        PresentOnCompanion(
            BOARD_OWNER,
            PresentationSlot.CocoonHero(
                title = focused.title,
                titleRes = focused.titleRes,
                kindRes = focused.kindRes,
                glyph = focused.glyph,
                platformSlug = focused.platformSlug,
                colorIndex = focused.colorIndex,
                artPath = art?.backgroundPath ?: art?.coverPath,
                hidesMirror = focused.target is CocoonTileTarget.NowPlaying
            )
        )
    }

    // Cocoon blurs the board behind its Y menu.
    val boardBlur by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (state.tileMenuOpen || state.dockMenuOpen || state.addAppOpen || state.startMenuOpen || state.choice != null) 10.dp else 0.dp,
        animationSpec = androidx.compose.animation.core.tween(120),
        label = "board-blur"
    )
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading -> Unit
            state.tiles.isEmpty() -> Text(
                text = stringResource(R.string.cocoon_board_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = CocoonSurface.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(32.dp)
            )
            // One sliding cursor for the dock and the folders, as in Cocoon.
            else -> com.nendo.argosy.ui.primitives.CocoonCursorHost(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { CocoonFolderZoom.board = it.boundsInRoot() }
                    .graphicsLayer {
                        // the board steps back a touch and fades; the folder itself pops (below)
                        val z = folderZoom.value
                        transformOrigin = CocoonFolderZoom.origin()
                        scaleX = 1f - z * 0.04f
                        scaleY = 1f - z * 0.04f
                        alpha = 1f - z
                    }
                    .deferredBlur { boardBlur }
            ) { Column(modifier = Modifier.fillMaxSize()) {
                val dock: @Composable () -> Unit = {
                    CocoonDock(
                        apps = state.dockApps,
                        focusedIndex = if (state.inDock) state.dockIndex else -1,
                        onTap = { slot ->
                            viewModel.focusDock(slot)
                            when (cocoonDockTarget(slot, state.dockApps)) {
                                CocoonDockTarget.AllApps -> onOpenAndroidGames()
                                // an empty slot: pick an app to go in this very slot
                                CocoonDockTarget.AddApp -> viewModel.openAddApp(replacing = null)
                                CocoonDockTarget.Pods -> com.nendo.argosy.ui.cocoon.CocoonPods.open()
                                is CocoonDockTarget.App -> viewModel.launchDockApp(slot - 1)
                            }
                        }
                    )
                }
                // Dock Style ▸ position: above the board (Cocoon's default) or under it.
                if (!CocoonHomeStore.dockBottom) dock()
                CocoonGroupBreadcrumb(state.groupTitles)
                if (!hasPresentation && focused != null) {
                    // Single Screen Mode: hero above the board, as Cocoon does on one display.
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth().weight(CocoonHomeStore.heroShare),
                        contentAlignment = Alignment.Center
                    ) {
                        CocoonHeroCard(
                            title = focused.title ?: focused.titleRes?.let { stringResource(it) }.orEmpty(),
                            kind = stringResource(focused.kindRes),
                            glyph = focused.glyph,
                            platformSlug = focused.platformSlug,
                            colorIndex = focused.colorIndex,
                            iconSize = (maxHeight * 0.62f).coerceAtMost(200.dp),
                        )
                    }
                }
                CocoonBoard(
                    state = state,
                    onTap = { index, tile ->
                        viewModel.setFocus(index)
                        open(tile)
                    },
                    animateReturnJourney = returningFromFolder,
                    onTapEmpty = { index -> viewModel.setFocus(index) },
                    modifier = Modifier.fillMaxWidth().weight(if (!hasPresentation) 1f - CocoonHomeStore.heroShare else 1f)
                )
                if (CocoonHomeStore.dockBottom) dock()
            } }
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = state.dockMenuOpen,
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(120)),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(100))
        ) {
            val packageName = (cocoonDockTarget(state.dockIndex, state.dockApps) as? CocoonDockTarget.App)?.packageName
            if (packageName != null) com.nendo.argosy.ui.cocoon.CocoonPopulating(com.nendo.argosy.ui.cocoon.CocoonPopulate.Style.DROP, com.nendo.argosy.ui.cocoon.CocoonPopulate.CursorEntry.GROW_FROM_LEFT) { CocoonDockMenu(
                packageName = packageName,
                focusedIndex = state.dockMenuIndex,
                showLaunchPicker = needsCocoonScreenPicker(rememberDockLaunchScreens()),
                launchScreens = state.dockLaunchScreens,
                launchScreenIndex = state.dockLaunchScreenIndex,
                onRow = { index ->
                    viewModel.focusDockMenu(index)
                    inputHandler.onConfirm()
                },
                onLaunchScreen = { index ->
                    viewModel.focusDockLaunchScreen(index)
                    inputHandler.onConfirm()
                },
                onDismiss = { viewModel.closeDockMenu() }
            ) }
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = state.addAppOpen,
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(140)),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(100))
        ) {
            CocoonAddAppPicker(
                apps = state.addApps,
                focusedIndex = state.addAppIndex,
                replacing = state.addAppReplacing != null,
                onPick = { viewModel.pickAddApp(it) },
                onDismiss = { viewModel.closeAddApp() }
            )
        }
        // Cocoon's Start menu.
        androidx.compose.animation.AnimatedVisibility(
            visible = state.startMenuOpen,
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(120)),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(100))
        ) {
            com.nendo.argosy.ui.cocoon.CocoonPopulating(com.nendo.argosy.ui.cocoon.CocoonPopulate.Style.DROP, key = state.startNewOpen) { CocoonListMenu(
                title = stringResource(if (state.startNewOpen) R.string.cocoon_start_new else R.string.cocoon_start_title),
                headerIcon = if (state.startNewOpen) androidx.compose.material.icons.Icons.Outlined.Add else androidx.compose.material.icons.Icons.Outlined.Menu,
                rows = cocoonStartRows(state.startNewOpen).map { row ->
                    Pair(when (row) {
                        CocoonStartRow.NEW -> androidx.compose.material.icons.Icons.Outlined.Add
                        CocoonStartRow.NEW_FOLDER -> androidx.compose.material.icons.Icons.Outlined.CreateNewFolder
                        CocoonStartRow.NEW_COLLECTION -> androidx.compose.material.icons.Icons.Outlined.Folder
                        CocoonStartRow.NEW_HOME_FOLDER -> androidx.compose.material.icons.Icons.Outlined.FolderSpecial
                        CocoonStartRow.ADD_APP -> androidx.compose.material.icons.Icons.Outlined.AddBox
                        CocoonStartRow.ADD_WIDGET -> androidx.compose.material.icons.Icons.Outlined.Widgets
                        CocoonStartRow.ADD_POD -> androidx.compose.material.icons.Icons.Outlined.Apps
                        CocoonStartRow.ADD_SMART -> androidx.compose.material.icons.Icons.Outlined.AutoAwesome
                        CocoonStartRow.EDIT_GRID -> androidx.compose.material.icons.Icons.Outlined.GridView
                        CocoonStartRow.SETTINGS -> androidx.compose.material.icons.Icons.Outlined.Settings
                        CocoonStartRow.SEARCH -> androidx.compose.material.icons.Icons.Outlined.Search
                        CocoonStartRow.MORE -> androidx.compose.material.icons.Icons.Outlined.MoreHoriz
                    }, if (row == CocoonStartRow.NEW) stringResource(row.labelRes) + "  ▸" else stringResource(row.labelRes))
                },
                focusedIndex = state.startMenuIndex,
                onRow = { index ->
                    viewModel.focusStartMenu(index)
                    inputHandler.onConfirm()
                },
                onDismiss = { viewModel.closeStartMenu() }
            ) }
        }
        // Start ▸ Add Widget / Add Pod: the second step. The list keeps drawing for the length of
        // its exit fade after B closes it, so it remembers what was open; falling back to the
        // first choice for that beat flashed the widget list on the way out.
        var lastChoice by androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf(CocoonChoice.WIDGETS)
        }
        state.choice?.let { lastChoice = it }
        androidx.compose.animation.AnimatedVisibility(
            visible = state.choice != null,
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(120)),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(100))
        ) {
            val choice = lastChoice
            com.nendo.argosy.ui.cocoon.CocoonPopulating(com.nendo.argosy.ui.cocoon.CocoonPopulate.Style.DROP, key = lastChoice) { CocoonListMenu(
                title = stringResource(
                    when (choice) {
                        CocoonChoice.WIDGETS -> R.string.cocoon_start_add_widget
                        CocoonChoice.PODS -> R.string.cocoon_start_add_pod
                        CocoonChoice.SMART -> R.string.cocoon_start_add_smart
                        CocoonChoice.MOVE -> R.string.cocoon_tile_menu_move
                        CocoonChoice.COLLECTIONS -> R.string.cocoon_choose_collection
                    }
                ),
                headerIcon = when (choice) {
                    CocoonChoice.WIDGETS -> androidx.compose.material.icons.Icons.Outlined.Widgets
                    CocoonChoice.PODS -> androidx.compose.material.icons.Icons.Outlined.Apps
                    CocoonChoice.SMART -> androidx.compose.material.icons.Icons.Outlined.AutoAwesome
                    CocoonChoice.MOVE -> androidx.compose.material.icons.Icons.Outlined.DriveFileMove
                    CocoonChoice.COLLECTIONS -> androidx.compose.material.icons.Icons.Outlined.Folder
                },
                rows = if (choice == CocoonChoice.MOVE) {
                    viewModel.moveTargets().map { (id, name) ->
                        Pair(
                            if (id == null) androidx.compose.material.icons.Icons.Outlined.Home
                            else androidx.compose.material.icons.Icons.Outlined.Folder,
                            name
                        )
                    }
                } else if (choice == CocoonChoice.COLLECTIONS) {
                    val mine = state.collectionChoices
                    if (mine.isEmpty()) {
                        listOf(androidx.compose.material.icons.Icons.Outlined.Folder to stringResource(R.string.cocoon_no_collections))
                    } else {
                        mine.map { androidx.compose.material.icons.Icons.Outlined.Folder to it.name }
                    }
                } else if (choice == CocoonChoice.SMART) {
                    CocoonSmartKind.entries.map { kind ->
                        Pair(
                            when (kind) {
                                CocoonSmartKind.FAVORITES -> androidx.compose.material.icons.Icons.Outlined.StarOutline
                                CocoonSmartKind.RECENT -> androidx.compose.material.icons.Icons.Outlined.History
                                CocoonSmartKind.MOST_PLAYED -> androidx.compose.material.icons.Icons.Outlined.TrendingUp
                                CocoonSmartKind.NEWLY_ADDED -> androidx.compose.material.icons.Icons.Outlined.NewReleases
                            },
                            stringResource(kind.labelRes)
                        )
                    }
                } else if (choice == CocoonChoice.WIDGETS) {
                    CocoonWidgetKind.entries.map { kind ->
                        Pair(when (kind) {
                            CocoonWidgetKind.CLOCK -> androidx.compose.material.icons.Icons.Outlined.Schedule
                            CocoonWidgetKind.CLOCK_ANALOG -> androidx.compose.material.icons.Icons.Outlined.AccessTime
                            CocoonWidgetKind.RECENT -> androidx.compose.material.icons.Icons.Outlined.History
                            CocoonWidgetKind.LAST_PLAYED -> androidx.compose.material.icons.Icons.Outlined.SportsEsports
                            CocoonWidgetKind.RANDOM -> androidx.compose.material.icons.Icons.Outlined.Casino
                            CocoonWidgetKind.PLAYTIME -> androidx.compose.material.icons.Icons.Outlined.Timer
                            CocoonWidgetKind.MUSIC -> androidx.compose.material.icons.Icons.Outlined.MusicNote
                            CocoonWidgetKind.IMAGE_PIN -> androidx.compose.material.icons.Icons.Outlined.Image
                            CocoonWidgetKind.BLANK -> androidx.compose.material.icons.Icons.Outlined.CropFree
                        }, stringResource(kind.labelRes))
                    }
                } else {
                    CocoonPodKind.entries.map { kind ->
                        Pair(when (kind) {
                            CocoonPodKind.LOG -> androidx.compose.material.icons.Icons.Outlined.Insights
                            CocoonPodKind.SCREENSHOTS -> androidx.compose.material.icons.Icons.Outlined.PhotoLibrary
                        }, stringResource(kind.labelRes))
                    }
                },
                focusedIndex = state.choiceIndex,
                onRow = { index -> viewModel.pickChoice(index) },
                onDismiss = { viewModel.closeChoice() }
            ) }
        }
        if (state.pickingImage) {
            com.nendo.argosy.ui.filebrowser.FileBrowserScreen(
                mode = com.nendo.argosy.ui.filebrowser.FileBrowserMode.FILE_SELECTION,
                title = stringResource(R.string.cocoon_widget_image_pin),
                fileFilter = com.nendo.argosy.ui.filebrowser.FileFilter.IMAGE,
                onPathSelected = { path -> viewModel.pinImage(path) },
                onDismiss = { viewModel.closeImagePicker() }
            )
        }
        CocoonPodsOverlay(onNavigate = onNavigate)
        if (state.namingGroup) {
            com.nendo.argosy.ui.screens.collections.dialogs.CollectionNameDialog(
                title = stringResource(R.string.cocoon_start_new_home_folder),
                label = stringResource(R.string.cocoon_app_name),
                confirmLabel = stringResource(R.string.cocoon_themes_apply),
                initialName = "",
                gamepadInput = true,
                onDismiss = { viewModel.closeNameGroup() },
                onSubmit = { name -> viewModel.createGroup(name) }
            )
        }
        state.appEditPackage?.let { pkg ->
            CocoonAppEditSheet(
                packageName = pkg,
                loadScreen = { viewModel.appScreen(pkg) },
                onSetScreen = { key -> viewModel.setAppScreen(pkg, key) },
                onDismiss = { viewModel.closeAppEdit() }
            )
        }
        state.appearanceKey?.let { key ->
            CocoonAppearanceSheet(
                tile = state.tiles.firstOrNull { it.key == key },
                look = viewModel.lookFor(key),
                onChange = { look -> viewModel.setLook(key, look) },
                onDismiss = { viewModel.closeAppearance() }
            )
        }
        if (state.screenshotsOpen) {
            CocoonScreenshotsPod(onDismiss = { viewModel.closeScreenshots() })
        }
        if (state.newFolderOpen) {
            com.nendo.argosy.ui.screens.collections.dialogs.CreateCollectionDialog(
                onDismiss = { viewModel.closeNewFolder() },
                onCreate = { name -> viewModel.createFolder(name) }
            )
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = state.editingGrid,
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(150)),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
        ) {
            Text(
                text = stringResource(R.string.cocoon_edit_grid_hint),
                style = MaterialTheme.typography.labelLarge,
                color = CocoonSurface.TextPrimary,
                modifier = Modifier
                    .background(CocoonSurface.Chip, androidx.compose.foundation.shape.RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = state.tileMenuOpen && focused != null,
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(120)),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(100))
        ) {
            if (focused != null) com.nendo.argosy.ui.cocoon.CocoonPopulating(com.nendo.argosy.ui.cocoon.CocoonPopulate.Style.DROP, com.nendo.argosy.ui.cocoon.CocoonPopulate.CursorEntry.GROW_FROM_LEFT) { CocoonTileMenu(
                tile = focused,
                rows = state.tileMenuRows,
                focusedIndex = state.tileMenuIndex,
                deleteArmed = state.deleteArmed,
                onRow = { index ->
                    viewModel.focusTileMenu(index)
                    inputHandler.onConfirm()
                },
                onDismiss = { viewModel.closeTileMenu() }
            ) }
        }
    }
}

@Composable
private fun CocoonBoard(
    state: CocoonHomeState,
    onTap: (Int, CocoonTile) -> Unit,
    animateReturnJourney: Boolean,
    modifier: Modifier = Modifier,
    onTapEmpty: (Int) -> Unit = {},
) {
    val gridState = rememberLazyGridState()
    val appearedKeys = remember(animateReturnJourney) { mutableSetOf<String>() }
    BoxWithConstraints(modifier = modifier) {
        // Measured from Cocoon on the Thor's 1240x1080 lower panel: ~95dp tiles on a ~120dp pitch,
        // three rows, five columns in view with a sixth clipped at the edge.
        // Cocoon packs the folders tightly: the gap between them is about a quarter of a tile,
        // and the three rows sit together as one block instead of spreading over the height.
        val rowPitch = maxHeight / (COCOON_BOARD_ROWS + 0.35f)
        // R3 changes the row count: the tiles grow or shrink smoothly while they slide into place.
        val tileSize by androidx.compose.animation.core.animateDpAsState(
            (rowPitch * 0.80f).coerceIn(56.dp, 140.dp),
            androidx.compose.animation.core.tween(260, easing = com.nendo.argosy.ui.theme.Motion.argosyEase),
            label = "cocoonTileSize"
        )
        var reflowing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        val boardRows = COCOON_BOARD_ROWS
        val firstRows = androidx.compose.runtime.remember { boardRows }
        LaunchedEffect(boardRows) {
            if (boardRows == firstRows && !reflowing) return@LaunchedEffect
            reflowing = true
            kotlinx.coroutines.delay(450)
            reflowing = false
        }
        val gap = tileSize * 0.24f
        val blockHeight = tileSize * COCOON_BOARD_ROWS + gap * (COCOON_BOARD_ROWS - 1) + gap
        val visibleColumns = (maxWidth / (tileSize + gap)).toInt().coerceAtLeast(1)

        // Cocoon keeps the cursor in the middle: once it reaches the centre column the board
        // slides under it instead. The slide uses the cursor's own 150 ms ease-out, so the board
        // arriving and the corners arriving land together and the cursor holds still.
        //
        // Only the first placement after the board appears is instant, so a cold start or a return
        // does not play a long slide to the folder that already holds the cursor. Everything after
        // it slides, the way a cover slides into view inside a folder - see [boardSlide].
        val density = androidx.compose.ui.platform.LocalDensity.current
        val pitchPx = with(density) { (tileSize + gap).toPx() }
        val firstPlacement = androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf(true)
        }
        LaunchedEffect(state.focusedIndex, visibleColumns, state.slotCount, animateReturnJourney) {
            if (state.tiles.isEmpty() || state.inDock) return@LaunchedEffect
            val initial = firstPlacement.value
            firstPlacement.value = false
            val move = boardSlide(
                focusedIndex = state.focusedIndex,
                rows = COCOON_BOARD_ROWS,
                slotCount = state.slotCount,
                visibleColumns = visibleColumns,
                firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset,
                pitchPx = pitchPx,
                firstPlacement = initial
            ) ?: return@LaunchedEffect
            if (move.instant) {
                gridState.scrollBy(move.deltaPx)
                return@LaunchedEffect
            }
            gridState.animateScrollBy(
                move.deltaPx,
                androidx.compose.animation.core.tween(150, easing = androidx.compose.animation.core.CubicBezierEasing(0.33f, 1f, 0.68f, 1f))
            )
        }

        LazyHorizontalGrid(
            rows = GridCells.Fixed(COCOON_BOARD_ROWS),
            state = gridState,
            userScrollEnabled = true,
            contentPadding = PaddingValues(horizontal = gap, vertical = gap / 2),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
            modifier = Modifier
                .fillMaxWidth()
                .height(blockHeight.coerceAtMost(maxHeight))
                .align(Alignment.Center)
        ) {
            items(
                count = state.slotCount,
                key = { index -> state.tiles.getOrNull(index)?.key ?: "empty-$index" }
            ) { index ->
                val raw = state.tiles.getOrNull(index)
                if (raw?.target is CocoonTileTarget.Covered) {
                    // part of a bigger widget drawn from its own slot
                    Box(modifier = Modifier.size(tileSize))
                    return@items
                }
                val tile = raw?.takeIf { it.target !is CocoonTileTarget.Gap }
                if (tile == null) {
                    // Cocoon's empty space: nothing drawn, but the cursor can rest here. While the
                    // cursor is on it, a soft "+" shows that something can be put here (A or Y).
                    val emptyFocused = !state.inDock && index == state.focusedIndex && !state.editingGrid
                    val plus by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (emptyFocused) 1f else 0f,
                        animationSpec = androidx.compose.animation.core.tween(160),
                        label = "cocoon-empty-plus"
                    )
                    Box(
                        modifier = Modifier
                            .size(tileSize)
                            .cocoonBrackets(focused = !state.inDock && index == state.focusedIndex, outset = 1.dp)
                            .clickableNoFocus { onTapEmpty(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (plus > 0.01f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(tileSize * 0.08f)
                                    .graphicsLayer { alpha = plus; scaleX = 0.9f + 0.1f * plus; scaleY = 0.9f + 0.1f * plus }
                                    .background(
                                        androidx.compose.ui.graphics.Color.White.copy(alpha = 0.07f),
                                        androidx.compose.foundation.shape.RoundedCornerShape(tileSize * 0.22f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Outlined.Add,
                                    contentDescription = null,
                                    tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f),
                                    modifier = Modifier.size(tileSize * 0.38f)
                                )
                            }
                        }
                    }
                    return@items
                }
                val lifted = state.editingGrid && index == state.focusedIndex
                val isFocusedTile = !state.inDock && index == state.focusedIndex
                val arrival = remember(tile.key) {
                    androidx.compose.animation.core.Animatable(
                        if (animateReturnJourney && appearedKeys.add(tile.key)) 0f else 1f
                    )
                }
                LaunchedEffect(tile.key, arrival) {
                    if (arrival.value < 1f) {
                        val columnsAway = kotlin.math.abs(index / COCOON_BOARD_ROWS - state.focusedIndex / COCOON_BOARD_ROWS)
                        kotlinx.coroutines.delay((columnsAway * 28L).coerceAtMost(140L))
                        arrival.animateTo(
                            1f,
                            androidx.compose.animation.core.tween(
                                240,
                                easing = com.nendo.argosy.ui.theme.Motion.argosyEase
                            )
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(tileSize)
                        .graphicsLayer {
                            alpha = arrival.value
                            translationX = tileSize.toPx() * 0.18f * (1f - arrival.value)
                            scaleX = 0.96f + 0.04f * arrival.value
                            scaleY = 0.96f + 0.04f * arrival.value
                        }
                        .then(
                            if (isFocusedTile) Modifier
                                .onGloballyPositioned { CocoonFolderZoom.tile = it.boundsInRoot() }
                                .graphicsLayer {
                                    // the opened folder swells as the board falls away from it
                                    // only on the way in: coming back, the cursor corners measure the
                                    // folder at its normal size, so it must not start swollen
                                    val z = if (CocoonFolderZoom.opening) CocoonFolderZoom.progress?.value ?: 0f else 0f
                                    val pop = 1f + 0.18f * kotlin.math.sin(z * Math.PI.toFloat() * 0.5f)
                                    scaleX = pop
                                    scaleY = pop
                                }
                            else Modifier
                        )
                        .then(
                            if (state.editingGrid) {
                                Modifier.animateItem(
                                    placementSpec = androidx.compose.animation.core.tween<androidx.compose.ui.unit.IntOffset>(
                                        220, easing = com.nendo.argosy.ui.theme.Motion.argosyEase
                                    )
                                )
                            } else if (reflowing) {
                                // Placement animates for the tiles that were already on screen;
                                // the ones newly in view come in with their own slide (below).
                                Modifier.animateItem(
                                    fadeInSpec = null,
                                    placementSpec = androidx.compose.animation.core.tween<androidx.compose.ui.unit.IntOffset>(
                                        260, easing = com.nendo.argosy.ui.theme.Motion.argosyEase
                                    ),
                                    fadeOutSpec = null
                                )
                            } else {
                                Modifier
                            }
                        )
                        .then(
                            if (reflowing && !state.editingGrid) {
                                Modifier.cocoonReflowSlide(tile.key)
                            } else {
                                Modifier
                            }
                        )
                        .zIndex(if (lifted) 3f else if (tile.spanCols > 1 || tile.spanRows > 1) 2f else 0f),
                    contentAlignment = Alignment.Center
                ) {
                    if (tile.spanCols > 1 || tile.spanRows > 1) {
                        // a bigger widget spreads from its slot over the ones it covers
                        val w = tileSize * tile.spanCols + gap * (tile.spanCols - 1)
                        val h = tileSize * tile.spanRows + gap * (tile.spanRows - 1)
                        CocoonWidgetTile(
                            payload = tile.platformSlug,
                            size = w,
                            height = h,
                            modifier = Modifier
                                .wrapContentSize(Alignment.TopStart, unbounded = true)
                                .then(if (lifted) Modifier.cocoonLifted() else Modifier)
                                .graphicsLayer { alpha = if (state.editingGrid && !lifted) 0.55f else 1f }
                                .cocoonBrackets(focused = isFocusedTile && !lifted, outset = 1.dp)
                                .clickableNoFocus { onTap(index, tile) }
                        )
                        return@Box
                    }
                    if (tile.target is CocoonTileTarget.NowPlaying) {
                        // The game left running: a square among the folders, one press from being back.
                        CocoonNowPlayingTile(
                            gameId = tile.target.gameId,
                            title = tile.title.orEmpty(),
                            size = tileSize,
                            focused = !state.inDock && index == state.focusedIndex && !lifted,
                            modifier = Modifier
                                .then(if (lifted) Modifier.cocoonLifted() else Modifier)
                                .clickableNoFocus { onTap(index, tile) }
                        )
                        return@Box
                    }
                    val tapTile = remember(index, tile, onTap) { { onTap(index, tile) } }
                    CocoonBoardFolder(
                        tile = tile,
                        size = tileSize,
                        lifted = lifted,
                        dimmed = state.editingGrid && !lifted,
                        focused = isFocusedTile && !lifted,
                        onTap = tapTile,
                    )
                }
            }
        }
    }
}

/**
 * One folder on the board. Its own composable with plain inputs, so moving the cursor redraws
 * only the folder it left and the one it reached; the rest of the board is skipped.
 */
@Composable
private fun CocoonBoardFolder(
    tile: CocoonTile,
    size: androidx.compose.ui.unit.Dp,
    lifted: Boolean,
    dimmed: Boolean,
    focused: Boolean,
    onTap: () -> Unit,
) {
    CocoonFolderIcon(
        colorIndex = tile.colorIndex,
        glyph = tile.glyph,
        platformSlug = tile.platformSlug,
        size = size,
        modifier = Modifier
            .then(if (lifted) Modifier.cocoonLifted() else Modifier)
            .graphicsLayer { alpha = if (dimmed) 0.55f else 1f }
            .cocoonBrackets(focused = focused, outset = 1.dp)
            .clickableNoFocus(onClick = onTap)
    )
}

/** Cocoon's dock: a centred row of real app icons along the top of the board. */
@Composable
fun CocoonDock(
    apps: List<String>,
    focusedIndex: Int,
    onTap: (Int) -> Unit,
) {
    // Cocoon's dock: All apps on the left, six app slots, Pods on the right; the focused one
    // carries the corner brackets. Dock Style can give it a bar behind it or hide it until the
    // cursor goes up to it.
    val shown = !CocoonHomeStore.dockAutoHide || focusedIndex >= 0
    androidx.compose.animation.AnimatedVisibility(
        visible = shown,
        enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
    ) {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .then(
                if (CocoonHomeStore.dockBackground) {
                    Modifier
                        .background(CocoonSurface.Panel.copy(alpha = 0.85f), androidx.compose.foundation.shape.RoundedCornerShape(50))
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                } else Modifier
            ),
        horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val size = 34.dp
        // Personalization ▸ Dock icon shape: every slot sits on a plate of that shape, which is
        // what the settings preview draws. With no shape chosen the icons keep Cocoon's bare look
        // (app icons alone were clipped before, and a launcher icon's own rounded art made that
        // change all but invisible).
        val dockShape = com.nendo.argosy.ui.cocoon.theme.cocoonDockIconShape()
        for (slot in 0 until COCOON_DOCK_SIZE) {
            val slotModifier = Modifier
                .size(size)
                .cocoonBrackets(focused = focusedIndex == slot, outset = 4.dp)
                .clickableNoFocus { onTap(slot) }
            Box(modifier = slotModifier, contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (dockShape == null) Modifier
                            else Modifier
                                .clip(dockShape)
                                .background(CocoonSurface.Chip.copy(alpha = 0.5f))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when (val target = cocoonDockTarget(slot, apps)) {
                        CocoonDockTarget.AllApps -> CocoonDockGlyph(
                            ring = false,
                            modifier = Modifier.size(if (dockShape == null) 28.dp else 20.dp)
                        )
                        CocoonDockTarget.Pods -> CocoonDockGlyph(
                            ring = true,
                            modifier = Modifier.size(if (dockShape == null) 28.dp else 20.dp)
                        )
                        is CocoonDockTarget.App -> CocoonAppIcon(
                            packageName = target.packageName,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(if (dockShape == null) 0.dp else 3.dp)
                                .cocoonDockIconClip()
                        )
                        CocoonDockTarget.AddApp -> if (focusedIndex == slot) {
                            Text(
                                text = "+",
                                style = MaterialTheme.typography.titleMedium,
                                color = CocoonSurface.TextSecondary.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
    }
    }
}

/** Cocoon's Y menu on a board tile: the tile enlarged with its name, the actions beside it. */
@Composable
private fun CocoonTileMenu(
    tile: CocoonTile,
    rows: List<CocoonTileMenuRow>,
    focusedIndex: Int,
    deleteArmed: Boolean = false,
    onRow: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    com.nendo.argosy.ui.input.ModalPresenceEffect()
    // One progress drives the whole summon: the tile leaves its board slot and travels left
    // into place while the rows open beside it, rather than both simply fading in.
    val enter = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(tile.key) {
        enter.animateTo(1f, androidx.compose.animation.core.tween(230, easing = com.nendo.argosy.ui.theme.Motion.argosyEase))
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f))
            .clickableNoFocus(onClick = onDismiss)
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(0.45f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Where the tile sits on the board when Y is pressed: the icon starts there
                // and travels to this column, which is the "item moves left" half of the summon.
                var startDx by remember(tile.key) { androidx.compose.runtime.mutableStateOf<Float?>(null) }
                CocoonFolderIcon(
                    colorIndex = tile.colorIndex,
                    glyph = tile.glyph,
                    platformSlug = tile.platformSlug,
                    size = 120.dp,
                    modifier = Modifier
                        .onGloballyPositioned { coords ->
                            if (startDx == null) {
                                val pos = coords.positionInRoot()
                                val centre = pos.x + coords.size.width / 2f
                                val from = CocoonFolderZoom.tile.center.x.takeIf { CocoonFolderZoom.tile.width > 0f } ?: centre
                                startDx = from - centre
                            }
                        }
                        .graphicsLayer {
                            val settle = 1f - enter.value
                            translationX = (startDx ?: 0f) * settle
                            val grow = 0.55f + 0.45f * enter.value
                            scaleX = grow
                            scaleY = grow
                            alpha = 0.35f + 0.65f * enter.value
                        }
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = tile.title ?: tile.titleRes?.let { stringResource(it) }.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    color = CocoonSurface.TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
            // Y's menu now also carries the Home rows, so it scrolls to keep the focused row in view.
            val menuScroll = androidx.compose.foundation.rememberScrollState()
            LaunchedEffect(focusedIndex, menuScroll.maxValue) {
                val target = if (rows.size <= 1) 0 else menuScroll.maxValue * focusedIndex / rows.lastIndex
                menuScroll.animateScrollTo(target)
            }
            Column(
                modifier = Modifier
                    .weight(0.55f)
                    .padding(horizontal = 8.dp)
                    .verticalScroll(menuScroll)
                    // The inset sits inside the scroll viewport: a focused row draws its brackets
                    // outside its bounds, and the viewport's clip was cutting the first one off.
                    .padding(vertical = 8.dp)
            ) {
                rows.forEachIndexed { index, row ->
                    val danger = row == CocoonTileMenuRow.DELETE_FOLDER || row == CocoonTileMenuRow.REMOVE
                    CocoonMenuRow(
                        icon = when (row) {
                            CocoonTileMenuRow.OPEN -> androidx.compose.material.icons.Icons.Outlined.PlayArrow
                            CocoonTileMenuRow.PLATFORM -> androidx.compose.material.icons.Icons.Outlined.Settings
                            CocoonTileMenuRow.EDIT_GRID -> androidx.compose.material.icons.Icons.Outlined.GridView
                            CocoonTileMenuRow.NEW_FOLDER -> androidx.compose.material.icons.Icons.Outlined.CreateNewFolder
                            CocoonTileMenuRow.DELETE_FOLDER -> androidx.compose.material.icons.Icons.Outlined.Delete
                            CocoonTileMenuRow.REMOVE -> androidx.compose.material.icons.Icons.Outlined.RemoveCircleOutline
                            CocoonTileMenuRow.APPEARANCE -> androidx.compose.material.icons.Icons.Outlined.Palette
                            CocoonTileMenuRow.EDIT_APP -> androidx.compose.material.icons.Icons.Outlined.Edit
                            CocoonTileMenuRow.SIZE -> androidx.compose.material.icons.Icons.Outlined.AspectRatio
                            CocoonTileMenuRow.MOVE -> androidx.compose.material.icons.Icons.Outlined.DriveFileMove
                            CocoonTileMenuRow.NEW -> androidx.compose.material.icons.Icons.Outlined.Add
                            CocoonTileMenuRow.SEARCH -> androidx.compose.material.icons.Icons.Outlined.Search
                            CocoonTileMenuRow.SETTINGS -> androidx.compose.material.icons.Icons.Outlined.Settings
                            CocoonTileMenuRow.CLOSE -> androidx.compose.material.icons.Icons.Filled.Close
                        },
                        label = if (danger && deleteArmed) {
                            stringResource(R.string.cocoon_tile_menu_delete_confirm)
                        } else if (row == CocoonTileMenuRow.NEW) {
                            stringResource(row.labelRes) + "  ▸"
                        } else {
                            stringResource(row.labelRes)
                        },
                        focused = index == focusedIndex,
                        isFirst = index == 0,
                        isLast = index == rows.lastIndex,
                        onClick = { onRow(index) },
                        color = if (danger) androidx.compose.ui.graphics.Color(0xFFFF6B6B) else CocoonSurface.TextPrimary,
                        trailing = null
                    )
                }
            }
        }
    }
}


/**
 * A tile that was off the right edge before the row count changed is composed fresh at its new
 * place. Without this it simply appeared; it slides in from the right instead.
 */
@Composable
private fun Modifier.cocoonReflowSlide(key: Any?): Modifier {
    val arrive = androidx.compose.runtime.remember(key) {
        androidx.compose.animation.core.Animatable(0f)
    }
    androidx.compose.runtime.LaunchedEffect(key) {
        arrive.animateTo(
            1f,
            androidx.compose.animation.core.tween(
                260,
                easing = com.nendo.argosy.ui.theme.Motion.argosyEase
            )
        )
    }
    return this.graphicsLayer {
        alpha = 0.25f + 0.75f * arrive.value
        translationX = (1f - arrive.value) * 56.dp.toPx()
    }
}

/** Edit Grid: the lifted tile sits a little larger and rocks gently while it is being moved. */
@Composable
private fun Modifier.cocoonLifted(): Modifier {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "cocoon-lifted")
    val angle by transition.animateFloat(
        initialValue = -2.5f,
        targetValue = 2.5f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(140),
            androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "cocoon-lifted-angle"
    )
    return this.graphicsLayer {
        rotationZ = angle
        scaleX = 1.08f
        scaleY = 1.08f
        shadowElevation = 18f
    }
}


/**
 * The dock's two fixed marks, drawn in Cocoon's manner: four white-ringed dots in a square for All
 * apps, and four overlapping rings around a grey centre for Settings.
 */
@Composable
private fun CocoonDockGlyph(ring: Boolean, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.minDimension
        val c = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
        val white = androidx.compose.ui.graphics.Color.White
        val dark = androidx.compose.ui.graphics.Color(0xFF2A2A2E)
        val grey = androidx.compose.ui.graphics.Color(0xFF8E8E93)
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.09f)
        if (!ring) {
            val r = w * 0.2f
            val d = w * 0.24f
            for (dx in listOf(-d, d)) for (dy in listOf(-d, d)) {
                val p = androidx.compose.ui.geometry.Offset(c.x + dx, c.y + dy)
                drawCircle(dark, r, p)
                drawCircle(white, r, p, style = stroke)
            }
        } else {
            val r = w * 0.24f
            val d = w * 0.2f
            drawCircle(grey, w * 0.2f, c)
            for ((dx, dy) in listOf(0f to -d, d to 0f, 0f to d, -d to 0f)) {
                drawCircle(white, r, androidx.compose.ui.geometry.Offset(c.x + dx, c.y + dy), style = stroke)
            }
        }
    }
}


/** The screens a dock app can start on, read once per menu: displays do not change mid-menu. */
@Composable
private fun rememberDockLaunchScreens(): List<CocoonLaunchScreen> = remember {
    cocoonLaunchScreens(
        com.nendo.argosy.DualScreenManagerHolder.instance?.focusableDisplays().orEmpty()
    )
}

/**
 * Cocoon's Y menu on a dock app: the app's icon and name, then Launch / Edit App / App Info / Clear
 * Slot. Launch carries the chevron to the screen picker when the device has more than one screen.
 */
@Composable
private fun CocoonDockMenu(
    packageName: String,
    focusedIndex: Int,
    showLaunchPicker: Boolean,
    launchScreens: List<CocoonLaunchScreen>,
    launchScreenIndex: Int,
    onRow: (Int) -> Unit,
    onLaunchScreen: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    com.nendo.argosy.ui.input.ModalPresenceEffect()
    val context = androidx.compose.ui.platform.LocalContext.current
    val label = remember(packageName) {
        CocoonAppStore.name(packageName) ?: runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f))
            .clickableNoFocus(onClick = onDismiss)
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(0.45f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val grow = remember { androidx.compose.animation.core.Animatable(0.55f) }
                LaunchedEffect(packageName) {
                    grow.animateTo(1f, androidx.compose.animation.core.tween(220, easing = com.nendo.argosy.ui.theme.Motion.argosyEase))
                }
                coil.compose.AsyncImage(
                    model = com.nendo.argosy.ui.coil.AppIconData(packageName),
                    contentDescription = null,
                    modifier = Modifier
                        .size(110.dp)
                        .graphicsLayer { scaleX = grow.value; scaleY = grow.value }
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleLarge,
                    color = CocoonSurface.TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
            Column(modifier = Modifier.weight(0.55f).padding(horizontal = 8.dp)) {
                if (launchScreens.isNotEmpty()) {
                    // Launch ▸ the two screens replace the menu's rows: no second panel, no
                    // header of its own, just the choice the chevron promised.
                    launchScreens.forEachIndexed { index, screen ->
                        CocoonMenuRow(
                            icon = androidx.compose.material.icons.Icons.Filled.Tv,
                            label = stringResource(R.string.cocoon_launch_on_screen, screen.number),
                            focused = index == launchScreenIndex,
                            isFirst = index == 0,
                            isLast = index == launchScreens.lastIndex,
                            onClick = { onLaunchScreen(index) },
                        )
                    }
                    return@Column
                }
                val rows = CocoonDockMenuRow.entries
                rows.forEachIndexed { index, row ->
                    val danger = row == CocoonDockMenuRow.CLEAR
                    CocoonMenuRow(
                        icon = when (row) {
                            CocoonDockMenuRow.LAUNCH -> androidx.compose.material.icons.Icons.Outlined.PlayArrow
                            CocoonDockMenuRow.EDIT -> androidx.compose.material.icons.Icons.Outlined.Edit
                            CocoonDockMenuRow.CHANGE -> androidx.compose.material.icons.Icons.Outlined.SwapHoriz
                            CocoonDockMenuRow.INFO -> androidx.compose.material.icons.Icons.Outlined.Info
                            CocoonDockMenuRow.CLEAR -> androidx.compose.material.icons.Icons.Outlined.Delete
                        },
                        label = stringResource(row.labelRes),
                        focused = index == focusedIndex,
                        isFirst = index == 0,
                        isLast = index == rows.lastIndex,
                        onClick = { onRow(index) },
                        color = if (danger) androidx.compose.ui.graphics.Color(0xFFFF6B6B) else CocoonSurface.TextPrimary,
                        // No button glyph on Edit App: Y is what opened this menu, so a Y
                        // beside the row read as "press Y" over the very row Y cannot press.
                        trailing = when (row) {
                            CocoonDockMenuRow.LAUNCH -> if (showLaunchPicker) {
                                {
                                    androidx.compose.material3.Icon(
                                        androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = CocoonSurface.TextPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            } else null
                            else -> null
                        }
                    )
                }
            }
        }
    }
}


/** Cocoon's app picker: every installed app as an icon grid; A puts the focused one in the dock. */
@Composable
private fun CocoonAddAppPicker(
    apps: List<CocoonPickApp>,
    focusedIndex: Int,
    replacing: Boolean,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    com.nendo.argosy.ui.input.ModalPresenceEffect()
    val grid = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    LaunchedEffect(focusedIndex, apps.size) {
        if (apps.isEmpty()) return@LaunchedEffect
        val visible = grid.layoutInfo.visibleItemsInfo
        val first = visible.firstOrNull()?.index ?: 0
        val last = visible.lastOrNull()?.index ?: 0
        val row = focusedIndex / COCOON_PICK_COLUMNS
        if (focusedIndex < first) grid.animateScrollToItem(row * COCOON_PICK_COLUMNS)
        else if (focusedIndex > last - COCOON_PICK_COLUMNS) {
            grid.animateScrollToItem(((row - 1).coerceAtLeast(0)) * COCOON_PICK_COLUMNS)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.82f))
            .clickableNoFocus(onClick = onDismiss)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp)) {
            Text(
                text = stringResource(if (replacing) R.string.cocoon_dock_edit_app else R.string.cocoon_dock_add_app),
                style = MaterialTheme.typography.titleLarge,
                color = CocoonSurface.TextPrimary
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(14.dp))
            com.nendo.argosy.ui.primitives.CocoonCursorHost(modifier = Modifier.fillMaxSize()) {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(COCOON_PICK_COLUMNS),
                    state = grid,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
                ) {
                    items(apps.size, key = { apps[it].packageName }) { index ->
                        val app = apps[index]
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickableNoFocus { onPick(index) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            coil.compose.AsyncImage(
                                model = com.nendo.argosy.ui.coil.AppIconData(app.packageName),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(72.dp)
                                    .cocoonBrackets(focused = index == focusedIndex, outset = 4.dp)
                            )
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (index == focusedIndex) CocoonSurface.TextPrimary else CocoonSurface.TextSecondary,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}


/** A Cocoon side menu: a header on the left, rows on the right (Start menu and its steps). */
@Composable
private fun CocoonListMenu(
    title: String,
    headerIcon: androidx.compose.ui.graphics.vector.ImageVector,
    rows: List<Pair<androidx.compose.ui.graphics.vector.ImageVector, String>>,
    focusedIndex: Int,
    onRow: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    com.nendo.argosy.ui.input.ModalPresenceEffect()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f))
            .clickableNoFocus(onClick = onDismiss)
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(0.4f), horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.Icon(
                    headerIcon, contentDescription = null, tint = CocoonSurface.TextPrimary,
                    modifier = Modifier.size(72.dp)
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(10.dp))
                Text(text = title, style = MaterialTheme.typography.titleLarge, color = CocoonSurface.TextPrimary, textAlign = TextAlign.Center)
            }
            val scroll = androidx.compose.foundation.rememberScrollState()
            LaunchedEffect(focusedIndex) {
                // keep the focused row in view on long lists
                if (scroll.maxValue > 0 && rows.size > 1) {
                    scroll.animateScrollTo(scroll.maxValue * focusedIndex / (rows.size - 1))
                }
            }
            Column(
                modifier = Modifier
                    .weight(0.6f)
                    .padding(horizontal = 8.dp)
                    .verticalScroll(scroll)
                    // same inset as the tile menu: brackets must not be clipped by the viewport
                    .padding(vertical = 8.dp)
            ) {
                rows.forEachIndexed { index, (icon, label) ->
                    CocoonMenuRow(
                        icon = icon,
                        label = label,
                        focused = index == focusedIndex,
                        isFirst = index == 0,
                        isLast = index == rows.lastIndex,
                        onClick = { onRow(index) },
                        color = CocoonSurface.TextPrimary,
                        trailing = null
                    )
                }
            }
        }
    }
}


/** Where the board zooms from when a folder opens, and whether it owes the zoom back out. */
object CocoonFolderZoom {
    var returning = false
    /** True while a folder is opening (the folder pops); false on the way back. */
    var opening = false
    /** The board's open/close progress (0 = Home, 1 = folder), read while drawing. */
    var progress: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>? = null
    var board: androidx.compose.ui.geometry.Rect = androidx.compose.ui.geometry.Rect.Zero
    var tile: androidx.compose.ui.geometry.Rect = androidx.compose.ui.geometry.Rect.Zero

    fun origin(): androidx.compose.ui.graphics.TransformOrigin {
        if (board.width <= 0f || board.height <= 0f || tile == androidx.compose.ui.geometry.Rect.Zero) {
            return androidx.compose.ui.graphics.TransformOrigin.Center
        }
        return androidx.compose.ui.graphics.TransformOrigin(
            ((tile.center.x - board.left) / board.width).coerceIn(0f, 1f),
            ((tile.center.y - board.top) / board.height).coerceIn(0f, 1f)
        )
    }
}


/** Inside a Home folder: "Home › Folder › Folder" over the board; B goes back up a level. */
@Composable
private fun CocoonGroupBreadcrumb(titles: List<String>) {
    if (titles.isEmpty()) return
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        com.nendo.argosy.ui.primitives.InputGlyph(
            button = com.nendo.argosy.ui.components.InputButton.B,
            size = 18.dp,
            tint = CocoonSurface.TextSecondary
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        Text(
            text = (listOf(stringResource(R.string.cocoon_home)) + titles).joinToString("  ›  "),
            style = MaterialTheme.typography.titleMedium,
            color = CocoonSurface.TextPrimary,
            maxLines = 1
        )
    }
}
