package com.nendo.argosy.ui.screens.library

import com.nendo.argosy.ui.common.deferredBlur
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.layout.aspectRatio
import com.nendo.argosy.ui.common.rememberCoverAspectRatio
import com.nendo.argosy.ui.common.labelRes
import com.nendo.argosy.ui.primitives.cocoonBrackets
import com.nendo.argosy.ui.screens.library.components.LibraryPlatformGrid
import com.nendo.argosy.ui.screens.library.components.LibraryPlatformGridEmpty
import com.nendo.argosy.ui.screens.library.components.LibraryPlatformGridHeaderHeight
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.zIndex
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.outlined.MusicNote
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.material.icons.outlined.Tv
import com.nendo.argosy.ui.cocoon.theme.cocoonIconOverlay
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nendo.argosy.R
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.model.SourceFilter
import com.nendo.argosy.data.preferences.GridDensity
import com.nendo.argosy.data.preferences.SelectSwapMode
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.fastAnimateScrollToItem
import com.nendo.argosy.ui.components.withNavBarClearance
import com.nendo.argosy.ui.components.ActiveFilterChipRow
import com.nendo.argosy.ui.components.ActiveFilterChipUi
import com.nendo.argosy.ui.components.AddToCollectionModal
import com.nendo.argosy.ui.components.FooterHint
import androidx.compose.ui.text.style.TextAlign
import com.nendo.argosy.ui.components.AlphabetSidebar
import com.nendo.argosy.ui.components.AlphabetSidebarWidth
import com.nendo.argosy.ui.components.CollectionItem
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.liftedReorderHints
import com.nendo.argosy.ui.components.DiscPickerModal
import com.nendo.argosy.ui.components.MemcardPickerModal
import com.nendo.argosy.ui.screens.collections.dialogs.CreateCollectionDialog
import com.nendo.argosy.ui.icons.InputIcons
import com.nendo.argosy.ui.input.DiscPickerInputHandler
import com.nendo.argosy.ui.input.MemcardPickerInputHandler
import com.nendo.argosy.ui.input.VariantPickerInputHandler
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.input.ModalPresenceEffect
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.theme.LocalBoxArtStyle
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.generated.ColorTokens
import com.nendo.argosy.ui.components.GameCard
import com.nendo.argosy.ui.components.SourceBadge
import com.nendo.argosy.ui.screens.home.GameDownloadIndicator
import com.nendo.argosy.ui.screens.home.HomeGameUi
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import com.nendo.argosy.data.preferences.LibraryListStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
fun LibraryScreen(
    isDefaultView: Boolean,
    onGameSelect: (Long) -> Unit,
    onMediaLibrarySelect: (String) -> Unit,
    onNavigateToDefault: () -> Unit,
    onDrawerToggle: () -> Unit,
    initialPlatformId: Long? = null,
    initialSource: String? = null,
    initialTileFilters: String? = null,
    onOpenApps: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenGameSheet: (Long, String) -> Unit = { id, _ -> onGameSelect(id) },
    onNavigate: (String) -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    // Record the folder as it draws, so a sheet can open over a still of it (no screen flash).
    val backdropLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val backdropScope = androidx.compose.runtime.rememberCoroutineScope()
    // Jingles: a short sound when the cursor rests on a game; Y ▸ Jingle picks one per game.
    val jingleContext = androidx.compose.ui.platform.LocalContext.current
    val restingGame = uiState.focusedGame
    LaunchedEffect(restingGame?.id) {
        com.nendo.argosy.ui.cocoon.CocoonJingles.stop(jingleContext)
        val game = restingGame ?: return@LaunchedEffect
        kotlinx.coroutines.delay(650)
        com.nendo.argosy.ui.cocoon.CocoonJingles.playFor(jingleContext, game.id)
    }
    DisposableEffect(Unit) { onDispose { com.nendo.argosy.ui.cocoon.CocoonJingles.stop(jingleContext) } }
    viewModel.onOpenGameSheet = { id, sheet ->
        backdropScope.launch {
            com.nendo.argosy.ui.cocoon.CocoonBackdrop.image.value =
                runCatching { backdropLayer.toImageBitmap() }.getOrNull()
            onOpenGameSheet(id, sheet)
        }
    }
    val quickNavigation by viewModel.quickNavigationEnabled.collectAsState()
    val downloadIndicators = viewModel.downloadIndicators.collectAsState()
    val initialGridIndex = remember { viewModel.gameIndexToGridIndex(uiState.focusedIndex) }
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = initialGridIndex)
    // For loading the covers just past the folder's edge with the art each tile will show.
    val imageCacheManagerForPrefetch = com.nendo.argosy.ui.common.LocalImageCacheManager.current
    val platformGridState = rememberLazyGridState()
    var isProgrammaticScroll by remember { mutableStateOf(false) }

    LaunchedEffect(initialPlatformId) {
        if (initialPlatformId != null) {
            viewModel.setInitialPlatform(initialPlatformId)
        }
    }

    LaunchedEffect(initialTileFilters) {
        LibraryFilterArgs.decode(initialTileFilters)?.let { viewModel.setInitialTileFilters(it) }
    }

    LaunchedEffect(initialSource) {
        if (initialSource != null) {
            val sourceFilter = SourceFilter.entries.find { it.name == initialSource }
            if (sourceFilter != null) {
                viewModel.setInitialSourceFilter(sourceFilter)
            }
        }
    }

    LaunchedEffect(uiState.currentPlatformIndex) {
        gridState.scrollToItem(0)
    }

    LaunchedEffect(uiState.games.size) {
        if (uiState.games.isNotEmpty() && uiState.focusedIndex > 0) {
            gridState.scrollToItem(viewModel.gameIndexToGridIndex(uiState.focusedIndex))
        }
    }

    val density = LocalDensity.current
    val isCompactList = uiState.isListLayout && uiState.libraryListStyle == LibraryListStyle.COMPACT
    var compactHeaderHeightPx by remember { mutableStateOf(0) }
    var folderTopPx by remember { mutableStateOf(0) }
    val folderTopDp = with(androidx.compose.ui.platform.LocalDensity.current) { folderTopPx.toDp() }
    val defaultHeaderHeightPx = with(density) { Dimens.headerHeightLg.toPx() }.toInt()
    val headerHeightPx = if (isCompactList && compactHeaderHeightPx > 0) {
        compactHeaderHeightPx
    } else {
        defaultHeaderHeightPx
    }
    val footerHeight = withNavBarClearance(Dimens.footerHeight)
    val footerHeightPx = with(density) { footerHeight.toPx() }.toInt()

    LaunchedEffect(uiState.focusedIndex, uiState.lastFocusMove) {
        if (uiState.lastFocusMove == null || uiState.games.isEmpty()) return@LaunchedEffect

        val layoutInfo = gridState.layoutInfo
        val viewportHeight = layoutInfo.viewportSize.height
        if (viewportHeight == 0) return@LaunchedEffect

        val itemHeight = layoutInfo.visibleItemsInfo.firstOrNull()?.size?.height ?: return@LaunchedEffect

        val effectiveHeight = viewportHeight - headerHeightPx - footerHeightPx
        val centeringOffset = (effectiveHeight - itemHeight) / 2

        isProgrammaticScroll = true
        gridState.animateScrollToItem(
            index = viewModel.gameIndexToGridIndex(uiState.focusedIndex),
            scrollOffset = -centeringOffset
        )
        isProgrammaticScroll = false
    }

    LaunchedEffect(uiState.sectionJumpTrigger) {
        if (uiState.sectionJumpTrigger == 0 || uiState.games.isEmpty()) return@LaunchedEffect

        val gridIndex = viewModel.gameIndexToGridIndex(uiState.focusedIndex)
        val layoutInfo = gridState.layoutInfo
        val viewportHeight = layoutInfo.viewportSize.height
        if (viewportHeight == 0) {
            gridState.scrollToItem(gridIndex)
            return@LaunchedEffect
        }

        val itemHeight = layoutInfo.visibleItemsInfo.firstOrNull()?.size?.height ?: 0
        val effectiveHeight = viewportHeight - headerHeightPx - footerHeightPx
        val centeringOffset = if (itemHeight > 0) (effectiveHeight - itemHeight) / 2 else 0

        isProgrammaticScroll = true
        gridState.fastAnimateScrollToItem(
            index = gridIndex,
            scrollOffset = -centeringOffset
        )
        isProgrammaticScroll = false
    }

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.isScrollInProgress }
            .collect { isScrolling ->
                if (isScrolling && !isProgrammaticScroll) {
                    viewModel.enterTouchMode()
                }
            }
    }


    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is LibraryEvent.LaunchIntent -> {
                    try {
                        context.startActivity(event.intent)
                    } catch (e: Exception) {
                        android.util.Log.e("LibraryScreen", "Failed to start activity", e)
                    }
                }
            }
        }
    }

    val inputDispatcher = LocalInputDispatcher.current
    val onCocoonMenuRow: (com.nendo.argosy.ui.cocoon.CocoonFolderMenuRow) -> Unit = { row ->
        when (row) {
            com.nendo.argosy.ui.cocoon.CocoonFolderMenuRow.APPS -> onOpenApps()
            com.nendo.argosy.ui.cocoon.CocoonFolderMenuRow.CREATE_FOLDER -> viewModel.showCreateCollectionFromModal()
            com.nendo.argosy.ui.cocoon.CocoonFolderMenuRow.SORT -> viewModel.toggleFilterMenu()
            com.nendo.argosy.ui.cocoon.CocoonFolderMenuRow.SEARCH -> onOpenSearch()
            com.nendo.argosy.ui.cocoon.CocoonFolderMenuRow.SETTINGS -> onOpenSettings()
        }
    }
    val dockLaunchContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { com.nendo.argosy.ui.cocoon.CocoonFolderDock.focus.value = null }
    val dockFocus by com.nendo.argosy.ui.cocoon.CocoonFolderDock.focus.collectAsState()
    val currentDockApps = { com.nendo.argosy.DualScreenManagerHolder.instance?.homeAppsFlow?.value.orEmpty() }
    // Cocoon folder motion: covers arrive in a wave from the left; leaving, they go in the reverse
    // wave and the header slides back up before Home comes back.
    val coverWave = remember { com.nendo.argosy.ui.cocoon.CocoonCoverWave() }
    val coverWaveShown = androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val headerOut = remember { androidx.compose.animation.core.Animatable(0f) }
    val leaveScope = androidx.compose.runtime.rememberCoroutineScope()
    val leavingFolder = remember { booleanArrayOf(false) }
    val reducedMotion = com.nendo.argosy.ui.theme.LocalMotionTier.current == com.nendo.argosy.ui.theme.MotionTier.Reduced
    val leaveFolder: () -> Unit = {
        if (com.nendo.argosy.ui.cocoon.CocoonMode.useClassicHome.value || reducedMotion) {
            onNavigateToDefault()
        } else if (!leavingFolder[0]) {
            leavingFolder[0] = true
            leaveScope.launch {
                launch {
                    headerOut.animateTo(1f, androidx.compose.animation.core.tween(130, easing = androidx.compose.animation.core.FastOutLinearInEasing))
                }
                coverWave.playOut()
                onNavigateToDefault()
                leavingFolder[0] = false
            }
        }
    }
        val inputHandler = remember(onGameSelect, onMediaLibrarySelect, onDrawerToggle, isDefaultView) {
        com.nendo.argosy.ui.cocoon.cocoonFolderMenuInput(
            base = viewModel.createInputHandler(
                isDefaultView = isDefaultView,
                onGameSelect = onGameSelect,
                onMediaLibrarySelect = onMediaLibrarySelect,
                onNavigateToDefault = leaveFolder,
                onDrawerToggle = onDrawerToggle
            ),
            menu = viewModel.cocoonMenu,
            onRow = { row -> onCocoonMenuRow(row) },
            suspendedFocused = {
                val suspended = com.nendo.argosy.DualScreenManagerHolder.instance?.gameSessionManager?.sessionState?.value
                suspended != null && suspended.gameId == viewModel.uiState.value.focusedGame?.id
            },
            // X: Cocoon's "Quit game" sheet for the suspended game.
            onCloseSuspended = {
                val game = viewModel.uiState.value.focusedGame
                val suspended = com.nendo.argosy.DualScreenManagerHolder.instance?.gameSessionManager?.sessionState?.value
                if (game != null && suspended != null) {
                    com.nendo.argosy.ui.cocoon.CocoonSessionDialog.show(
                        com.nendo.argosy.ui.cocoon.CocoonSessionDialog.Quit(
                            gameId = suspended.gameId,
                            title = suspended.title,
                            fallbackCover = game.coverPath
                        )
                    )
                }
            },
            // A on another game while one is suspended: Cocoon's "Switch games" sheet first.
            interceptLaunch = {
                val game = viewModel.uiState.value.focusedGame
                val suspended = com.nendo.argosy.DualScreenManagerHolder.instance?.gameSessionManager?.sessionState?.value
                if (game == null || suspended == null || suspended.gameId == game.id ||
                    !game.isDownloaded || game.needsInstall || viewModel.uiState.value.showQuickMenu ||
                    viewModel.uiState.value.showFilterMenu
                ) {
                    false
                } else {
                    com.nendo.argosy.ui.cocoon.CocoonSessionDialog.show(
                        com.nendo.argosy.ui.cocoon.CocoonSessionDialog.Switch(
                            fromGameId = suspended.gameId,
                            fromTitle = suspended.title,
                            toGameId = game.id,
                            toCover = game.coverPath,
                            emulatorName = suspended.emulatorName ?: suspended.platformName,
                            onLaunch = { viewModel.launchGame(game.id) }
                        )
                    )
                    true
                }
            },
            dockSize = { com.nendo.argosy.ui.cocoon.COCOON_DOCK_SIZE },
            onDockSlot = { slot ->
                when (val target = com.nendo.argosy.ui.cocoon.cocoonDockTarget(slot, currentDockApps())) {
                    com.nendo.argosy.ui.cocoon.CocoonDockTarget.AllApps,
                    com.nendo.argosy.ui.cocoon.CocoonDockTarget.AddApp -> onOpenApps()
                    com.nendo.argosy.ui.cocoon.CocoonDockTarget.Pods -> com.nendo.argosy.ui.cocoon.CocoonPods.open()
                    is com.nendo.argosy.ui.cocoon.CocoonDockTarget.App ->
                        dockLaunchContext.packageManager.getLaunchIntentForPackage(target.packageName)?.let { intent ->
                            runCatching { dockLaunchContext.startActivity(intent) }
                        }
                }
            },
            grid = object : com.nendo.argosy.ui.cocoon.CocoonGridNavigation {
                override fun active(): Boolean {
                    val st = viewModel.uiState.value
                    return !st.isListLayout && !st.isPlatformGrid && !st.showQuickMenu &&
                        !st.showFilterMenu && !st.showAddToCollectionModal && !st.isSectionRailFocused &&
                        st.games.isNotEmpty()
                }
                override fun move(dRow: Int, dCol: Int): Boolean {
                    val st = viewModel.uiState.value
                    val target = com.nendo.argosy.ui.cocoon.CocoonFolderGrid.move(
                        st.focusedIndex, st.games.size, com.nendo.argosy.ui.cocoon.CocoonFolderGrid.rows.value, dRow, dCol
                    ) ?: return false
                    viewModel.setFocusIndex(target)
                    return true
                }
            }
        )
    }
    val cocoonMenuOpen by viewModel.cocoonMenu.open.collectAsState()
    val cocoonRows by com.nendo.argosy.ui.cocoon.CocoonFolderGrid.rows.collectAsState()
    val folderHaptics = com.nendo.argosy.ui.input.LocalHaptics.current
    // Cocoon's folder entrance: header and covers rise a short way into place as they fade in;
    // the dock above them stays still.
    /*
     * Returning from a suspended game: the launcher steps off the game's own page and back onto
     * this folder, so the grid is composed again from nothing. It has to land already settled -
     * replaying the entrance showed the bare background while the covers waved in, which is the
     * flash seen between the game and the folder it was started from.
     */
    val returnedFromSuspend = remember { com.nendo.argosy.ui.cocoon.CocoonSuspendTest.freshAny() }
    val folderEnter = remember {
        androidx.compose.animation.core.Animatable(if (returnedFromSuspend) 0f else 1f)
    }
    // Rise once the covers actually exist; started any earlier it finishes before the games
    // arrive and the covers just pop in.
    val coversReady = uiState.games.isNotEmpty()
    LaunchedEffect(coversReady) {
        if (!coversReady) {
            kotlinx.coroutines.delay(700) // an empty folder still gets its header
        }
        if (folderEnter.value > 0f) {
            launch {
                folderEnter.animateTo(0f, androidx.compose.animation.core.tween(180, easing = com.nendo.argosy.ui.theme.Motion.argosyEase))
            }
        }
        if (coversReady && !coverWave.played) {
            // only on the way in from Home: coming back from a game's page the covers just stay
            if (returnedFromSuspend || reducedMotion || coverWaveShown.value) coverWave.skipIn()
            else {
                coverWaveShown.value = true
                // A soft tick under each column as the covers populate, so the population is felt
                // in the order it is seen. Reduced motion skips it: the covers are already there,
                // so there is no wave to ride.
                val columns = if (cocoonRows <= 0) {
                    1
                } else {
                    (uiState.games.size + cocoonRows - 1) / cocoonRows
                }
                coverWave.playInReportingColumns(columns) {
                    folderHaptics?.vibrate(com.nendo.argosy.ui.input.HapticPattern.FOCUS_CHANGE)
                }
            }
        }
    }
    val folderRise = with(LocalDensity.current) { 26.dp.toPx() }
    val cocoonMenuIndex by viewModel.cocoonMenu.index.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_LIBRARY)
                viewModel.onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_LIBRARY)
        viewModel.republishCompanionDetail()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.clearCompanionDetail()
        }
    }

    val siblingChoiceOpen = viewModel.siblingChoiceState.collectAsState().value != null
    val showAnyOverlay = uiState.showFilterMenu || uiState.showQuickMenu || uiState.showAddToCollectionModal || uiState.syncOverlayState != null || uiState.discPickerState != null || uiState.variantPickerState != null || uiState.memcardPickerState != null || siblingChoiceOpen
    val modalBlur by animateDpAsState(
        targetValue = if (showAnyOverlay) Motion.blurRadiusModal else 0.dp,
        animationSpec = Motion.focusSpringDp,
        label = "modalBlur"
    )

    // Cocoon's Quit / Switch sheet blurs the folder behind it as it opens.

    // Coming back from a suspended game (or opening its folder), the cursor lands on that game.
    val sessionManager = com.nendo.argosy.DualScreenManagerHolder.instance?.gameSessionManager
    val suspendedSession by remember(sessionManager) {
        sessionManager?.sessionState ?: kotlinx.coroutines.flow.MutableStateFlow(null)
    }.collectAsState()
    val suspendedGameId = suspendedSession?.gameId
    var focusedSuspendedFor by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(suspendedGameId, uiState.games.isNotEmpty()) {
        if (suspendedGameId != null && suspendedGameId != focusedSuspendedFor && uiState.games.isNotEmpty()) {
            if (viewModel.focusGameSilently(suspendedGameId)) focusedSuspendedFor = suspendedGameId
        }
    }

    val swipeThreshold = with(LocalDensity.current) { 50.dp.toPx() }
    val edgeThreshold = with(LocalDensity.current) { 80.dp.toPx() }
    val currentOnDrawerToggle by rememberUpdatedState(onDrawerToggle)
    val currentIsPlatformGrid by rememberUpdatedState(uiState.isPlatformGrid)

    val swipeGestureModifier = Modifier.pointerInput(swipeThreshold, edgeThreshold) {
        var totalDragX = 0f
        var totalDragY = 0f
        var startX = 0f
        detectDragGestures(
            onDragStart = { offset ->
                totalDragX = 0f
                totalDragY = 0f
                startX = offset.x
            },
            onDragEnd = {
                when {
                    startX < edgeThreshold && totalDragX > swipeThreshold -> currentOnDrawerToggle()
                    currentIsPlatformGrid -> {}
                    totalDragX > swipeThreshold && abs(totalDragX) > abs(totalDragY) -> viewModel.previousPlatform()
                    totalDragX < -swipeThreshold && abs(totalDragX) > abs(totalDragY) -> viewModel.nextPlatform()
                }
            },
            onDrag = { _, dragAmount ->
                totalDragX += dragAmount.x
                totalDragY += dragAmount.y
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithContent {
                backdropLayer.record { this@drawWithContent.drawContent() }
                drawLayer(backdropLayer)
            }
    ) {
        // One sliding cursor for the dock and the covers, as in Cocoon.
        com.nendo.argosy.ui.primitives.CocoonCursorHost(
            // read while drawing, so the blur animation repaints instead of recomposing the folder
            modifier = Modifier.fillMaxSize().deferredBlur {
                val sessionDialogBlur = Motion.blurRadiusModal * com.nendo.argosy.ui.cocoon.CocoonSessionDialog.blurFraction
                if (sessionDialogBlur > modalBlur) sessionDialogBlur else modalBlur
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { clip = false }
                    .then(swipeGestureModifier)
            ) {
                // Opening a folder, the game list arrives a moment after the platform list. Until it
                // has (or a short wait passes), show nothing rather than flashing "no games here"
                // or the platform picker before the covers appear.
                var settled by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(700)
                    settled = true
                }
                when {
                    uiState.isLoading || (!settled && uiState.games.isEmpty()) -> {
                        Box(modifier = Modifier.fillMaxSize())
                    }
                    uiState.isPlatformGrid -> {
                        val configuration = LocalConfiguration.current
                        LaunchedEffect(configuration.screenWidthDp) {
                            viewModel.updateScreenWidth(configuration.screenWidthDp)
                        }

                        if (uiState.platformGridIsEmpty) {
                            LibraryPlatformGridEmpty()
                        } else {
                            val haptics = LocalHapticFeedback.current
                            LibraryPlatformGrid(
                                cells = uiState.platformCells,
                                focusedIndex = uiState.platformGridFocusedIndex,
                                heldIndex = uiState.platformReorder?.let { reorder ->
                                    uiState.platformCells.indexOfFirst { it.isPlatform } + reorder.heldIndex
                                },
                                columns = uiState.platformGridColumns,
                                gridState = platformGridState,
                                onCellClick = { index ->
                                    if (uiState.isReorderingPlatforms) {
                                        viewModel.placeHeldPlatformCell(index)
                                    } else {
                                        viewModel.openLandingCell(index, onMediaLibrarySelect)
                                    }
                                },
                                onCellLongClick = { index ->
                                    if (viewModel.liftPlatformCellAt(index)) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                }
                            )
                        }
                    }
                    uiState.games.isEmpty() -> {
                        EmptyLibrary(
                            platformName = uiState.currentPlatform?.name,
                            activeFilters = uiState.activeFilters
                        )
                    }
                    else -> {
                        key(uiState.currentPlatformIndex) {
                            val gridSpacing = uiState.gridSpacingDp.dp
                            val columnsCount = uiState.columnsCount
                            val boxArtStyle = LocalBoxArtStyle.current
                            val aspectRatio = boxArtStyle.aspectRatio

                            val configuration = LocalConfiguration.current
                            LaunchedEffect(configuration.screenWidthDp) {
                                viewModel.updateScreenWidth(configuration.screenWidthDp)
                            }

                            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                val sidebarWidth = if (uiState.showSectionSidebar) AlphabetSidebarWidth else 0.dp
                                val totalSpacing = gridSpacing * (columnsCount + 1)
                                val columnWidth = (maxWidth - totalSpacing - sidebarWidth) / columnsCount
                                val cardHeight = columnWidth / aspectRatio

                                if (uiState.isListLayout) {
                                    LibraryGameList(
                                        uiState = uiState,
                                        viewModel = viewModel,
                                        sidebarWidth = sidebarWidth,
                                        headerHeightPx = headerHeightPx,
                                        footerHeightPx = footerHeightPx,
                                        onGameSelect = onGameSelect
                                    )
                                } else if (boxArtStyle.nativeAspectRatio) {
                                    LibraryMasonryGrid(
                                        uiState = uiState,
                                        viewModel = viewModel,
                                        columnsCount = columnsCount,
                                        gridSpacing = gridSpacing,
                                        sidebarWidth = sidebarWidth,
                                        fallbackAspectRatio = aspectRatio,
                                        bottomPadding = cardHeight,
                                        headerHeightPx = headerHeightPx,
                                        footerHeightPx = footerHeightPx,
                                        onGameSelect = onGameSelect
                                    )
                                } else {
                                // Cocoon's folder: covers in rows that run off to the right, like
                                // the board. R3 picks how many rows; one row adds the name card.
                                com.nendo.argosy.ui.cocoon.CocoonFolderCovers(
                                    keys = remember(uiState.games) { uiState.games.map { it.id } },
                                    focusedIndex = uiState.focusedIndex,
                                    rows = cocoonRows,
                                    topPadding = maxOf(
                                        com.nendo.argosy.ui.cocoon.COCOON_FOLDER_HEADER_HEIGHT + com.nendo.argosy.ui.cocoon.COCOON_DOCK_HEIGHT,
                                        folderTopDp
                                    ) + 6.dp,
                                    focusedTitle = uiState.focusedGame?.title,
                                    focusedSubtitle = uiState.focusedGame?.platformDisplayName,
                                    modifier = Modifier.fillMaxSize(),
                                    wave = coverWave,
                                    // the same cover each tile shows (see GameCard)
                                    coverPathFor = { i ->
                                        uiState.games.getOrNull(i)?.let { g ->
                                            uiState.repairedCoverPaths[g.id]
                                                ?: imageCacheManagerForPrefetch?.artFor(g.id)?.coverPath
                                                ?: g.coverPath
                                        }
                                    }
                                ) { index, cellSize ->
                                    val game = uiState.games.getOrNull(index) ?: return@CocoonFolderCovers
                                    val isFocused = index == uiState.focusedIndex && dockFocus == null
                                    LibraryGameCard(
                                        game = game,
                                        isFocused = isFocused,
                                        showFocus = !uiState.isTouchMode || uiState.hasSelectedGame,
                                        cardHeight = cellSize,
                                        showPlatformBadge = uiState.currentPlatformIndex < 0,
                                        coverPathOverride = uiState.repairedCoverPaths[game.id],
                                        onCoverLoadFailed = viewModel::repairCoverImage,
                                        downloadIndicatorFor = {
                                            downloadIndicators.value[it] ?: GameDownloadIndicator.NONE
                                        },
                                        // Square folder tiles always take the flat cover, zoomed to fill:
                                        // a 3D box is tall and fitting it left empty bands on both sides.
                                        useBoxArt = false,
                                        onClick = { viewModel.handleItemTap(index, onGameSelect) },
                                        onLongClick = { viewModel.handleItemLongPress(index) },
                                        modifier = Modifier
                                            .zIndex(if (isFocused) 1f else 0f)
                                            .cocoonIconOverlay(game.platformSlug)
                                    )
                                }
                                }

                                if (uiState.showSectionSidebar) {
                                    AlphabetSidebar(
                                        availableLetters = uiState.sectionLabels,
                                        currentLetter = uiState.currentSectionLabel,
                                        focusedLetter = uiState.sectionRailFocusedLabel,
                                        onLetterClick = { viewModel.jumpToSection(it) },
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .fillMaxHeight()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.align(Alignment.TopCenter)) {
                // Opening a folder the view starts as the platform list for a moment before the
                // games arrive; keep the dock and folder header up the whole time so the dock
                // doesn't blink out and back in.
                val openedForFolder = initialPlatformId != null || initialSource != null || initialTileFilters != null
                if (uiState.isPlatformGrid && !openedForFolder) {
                    LibraryPlatformGridHeader(
                        platformCount = uiState.platformCellCount,
                        mediaLibraryCount = uiState.mediaCellCount
                    )
                } else {
                    val activeFilterChips = remember(uiState.activeFilters) { uiState.activeFilters.chips }
                    // Cocoon keeps the dock of app shortcuts above a folder's header.
                    val dockApps by (com.nendo.argosy.DualScreenManagerHolder.instance?.homeAppsFlow
                        ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyList<String>()) }).collectAsState()
                    val dockContext = androidx.compose.ui.platform.LocalContext.current
                    Column(
                        modifier = Modifier
                            .onSizeChanged { folderTopPx = it.height }
                            .graphicsLayer {
                                // read while drawing, so the folder zoom repaints the header and
                                // dock instead of rebuilding them on every frame
                                val z = com.nendo.argosy.ui.cocoon.CocoonFolderZoom.progress?.value ?: 1f
                                val s = androidx.compose.ui.util.lerp(0.92f, 1f, z)
                                scaleX = s
                                scaleY = s
                                alpha = z.coerceIn(0f, 1f)
                            }
                            .background(com.nendo.argosy.ui.theme.CocoonSurface.Background)
                    ) {
                    com.nendo.argosy.ui.cocoon.CocoonDock(
                        apps = dockApps,
                        focusedIndex = dockFocus ?: -1,
                        onTap = { slot ->
                            when (val target = com.nendo.argosy.ui.cocoon.cocoonDockTarget(slot, dockApps)) {
                                com.nendo.argosy.ui.cocoon.CocoonDockTarget.AllApps,
                                com.nendo.argosy.ui.cocoon.CocoonDockTarget.AddApp -> onOpenApps()
                                com.nendo.argosy.ui.cocoon.CocoonDockTarget.Pods -> com.nendo.argosy.ui.cocoon.CocoonPods.open()
                                is com.nendo.argosy.ui.cocoon.CocoonDockTarget.App ->
                                    dockContext.packageManager.getLaunchIntentForPackage(target.packageName)?.let { intent ->
                                        runCatching { dockContext.startActivity(intent) }
                                    }
                            }
                        }
                    )
                    com.nendo.argosy.ui.cocoon.CocoonFolderHeader(
                        modifier = Modifier
                            .onSizeChanged { compactHeaderHeightPx = it.height }
                            .graphicsLayer {
                                // slides down into place under the dock, and back up on leaving
                                val away = maxOf(folderEnter.value, headerOut.value)
                                translationY = -away * folderRise
                                alpha = 1f - away
                            },
                        title = uiState.currentPlatform?.displayName
                            ?: stringResource(R.string.library_header_all_platforms),
                        sortLabel = stringResource(uiState.activeFilters.sort.option.labelRes),
                        onBack = { inputHandler.onBack() },
                        onSort = { viewModel.toggleFilterMenu() },
                        onToggleLayout = { viewModel.toggleLibraryLayout() }
                    )
                    }
                }
            }

            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                if (uiState.isPlatformGrid) {
                    LibraryPlatformGridFooter(
                        isReordering = uiState.isReorderingPlatforms,
                        canReorder = uiState.focusedPlatformCell != null,
                        onHintClick = { button ->
                            when (button) {
                                InputButton.A -> viewModel.dropPlatformCell()
                                InputButton.B -> viewModel.cancelPlatformCellLift()
                                InputButton.Y -> if (uiState.isReorderingPlatforms) {
                                    viewModel.dropPlatformCell()
                                } else {
                                    viewModel.liftPlatformCell()
                                }
                                else -> {}
                            }
                        }
                    )
                } else {
                    val isViewingHidden = uiState.activeFilters.source == SourceFilter.HIDDEN
                    if (false) LibraryFooter(
                        focusedGame = uiState.focusedGame,
                        isViewingHidden = isViewingHidden,
                        canSwitchPlatform = uiState.platforms.isNotEmpty(),
                        isSectionRailFocused = uiState.isSectionRailFocused,
                        quickNavigation = quickNavigation,
                        canJumpSection = uiState.sectionLabels.size > 1,
                        onHintClick = { button ->
                            when (button) {
                                InputButton.A -> if (uiState.isSectionRailFocused) {
                                    viewModel.confirmSectionRail()
                                } else {
                                    uiState.focusedGame?.let { onGameSelect(it.id) }
                                }
                                InputButton.B -> viewModel.exitSectionRail()
                                InputButton.Y -> uiState.focusedGame?.let {
                                    if (isViewingHidden) viewModel.unhideGame(it.id)
                                    else viewModel.toggleFavorite(it.id)
                                }
                                InputButton.X -> viewModel.toggleFilterMenu()
                                InputButton.LT_RT -> if (quickNavigation) {
                                    viewModel.nextPlatform()
                                } else {
                                    viewModel.jumpToAdjacentSection(1)
                                }
                                InputButton.LB_RB -> viewModel.nextPlatform()
                                InputButton.RIGHT_STICK_VERTICAL -> viewModel.jumpToAdjacentSection(1)
                                InputButton.SELECT -> if (com.nendo.argosy.ui.dualscreen.selectSwapsRoles()) {
                                    com.nendo.argosy.DualScreenManagerHolder.instance?.swapRoles()
                                } else {
                                    viewModel.toggleQuickMenu()
                                }
                                else -> {}
                            }
                        }
                    )
                }
            }
        }

        if (cocoonMenuOpen && !uiState.isPlatformGrid) {
            com.nendo.argosy.ui.cocoon.CocoonFolderMenuSheet(
                focusedIndex = cocoonMenuIndex,
                onRow = { row ->
                    viewModel.cocoonMenu.hide()
                    onCocoonMenuRow(row)
                },
                onDismiss = { viewModel.cocoonMenu.hide() },
                onFocus = { viewModel.cocoonMenu.focus(it) }
            )
        }

        AnimatedVisibility(
            visible = uiState.showFilterMenu,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            FilterMenuOverlay(
                uiState = uiState,
                onDismiss = { viewModel.toggleFilterMenu() },
                onCategorySelect = { viewModel.setFilterCategory(it) },
                onOptionSelect = { index ->
                    viewModel.moveFilterOptionFocus(index - uiState.filterOptionIndex)
                    viewModel.confirmFilterSelection()
                },
                onSearchQueryChange = { viewModel.updateSearchQuery(it) }
            )
        }

        AnimatedVisibility(
            visible = uiState.showQuickMenu,
            enter = fadeIn(androidx.compose.animation.core.tween(120)),
            exit = fadeOut(androidx.compose.animation.core.tween(100))
        ) {
            uiState.focusedGame?.let { game ->
                com.nendo.argosy.ui.cocoon.CocoonPopulating(com.nendo.argosy.ui.cocoon.CocoonPopulate.Style.DROP, com.nendo.argosy.ui.cocoon.CocoonPopulate.CursorEntry.GROW_FROM_LEFT) { QuickMenuOverlay(
                    game = game,
                    rows = uiState.quickMenuRowList(),
                    focusIndex = uiState.quickMenuFocusIndex,
                    onDismiss = { viewModel.toggleQuickMenu() },
                    onPrimaryAction = {
                        viewModel.toggleQuickMenu()
                        when {
                            game.needsInstall -> viewModel.installApk(game.id)
                            game.isDownloaded -> viewModel.launchGame(game.id)
                            game.source == com.nendo.argosy.data.model.GameSource.STEAM -> viewModel.linkSteamLauncher(game.id)
                            else -> viewModel.downloadGame(game.id)
                        }
                    },
                    onFavorite = { viewModel.toggleFavorite(game.id) },
                    onLaunchOnScreen = { index ->
                        viewModel.toggleQuickMenu()
                        viewModel.launchOnScreen(game.id, index)
                    },
                    onDetails = {
                        viewModel.toggleQuickMenu()
                        onGameSelect(game.id)
                    },
                    onAddToCollection = {
                        viewModel.toggleQuickMenu()
                        viewModel.showAddToCollectionModal(game.id)
                    },
                    onAddToGrid = {
                        viewModel.toggleQuickMenu()
                        viewModel.addGameToHomeGrid(game.id)
                    },
                    onActiveVariant = { viewModel.openActiveVariant(game.id) },
                    onRefresh = { viewModel.refreshGameData(game.id) },
                    onResyncPlatform = {
                        viewModel.toggleQuickMenu()
                        viewModel.syncCurrentPlatform()
                    },
                    onDelete = {
                        viewModel.toggleQuickMenu()
                        if (game.isAndroidApp) viewModel.uninstallAndroidApp(game.id) else viewModel.deleteLocalFile(game.id)
                    },
                    onRemoveFromLibrary = {
                        viewModel.toggleQuickMenu()
                        viewModel.removeFromLibrary(game.id)
                    },
                    onHide = {
                        viewModel.toggleQuickMenu()
                        if (game.isHidden) viewModel.unhideGame(game.id) else viewModel.hideGame(game.id)
                    },
                    onEdit = {
                        viewModel.toggleQuickMenu()
                        onOpenGameSheet(game.id, "edit")
                    },
                    onAchievements = {
                        viewModel.toggleQuickMenu()
                        onOpenGameSheet(game.id, "achievements")
                    }
                ) }
            }
        }

        com.nendo.argosy.ui.common.GameConfirmHost(viewModel.gameConfirm)

        AnimatedVisibility(
            visible = uiState.showAddToCollectionModal,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            AddToCollectionModal(
                collections = uiState.collections.map { collection ->
                    CollectionItem(
                        id = collection.id,
                        name = collection.name,
                        isInCollection = collection.isInCollection
                    )
                },
                focusIndex = uiState.collectionModalFocusIndex,
                onToggleCollection = { viewModel.toggleGameInCollection(it) },
                onCreate = { viewModel.showCreateCollectionFromModal() },
                onDismiss = { viewModel.dismissAddToCollectionModal() }
            )
        }

        if (uiState.showCreateCollectionDialog) {
            CreateCollectionDialog(
                onDismiss = { viewModel.hideCreateCollectionDialog() },
                onCreate = { name -> viewModel.createCollectionFromModal(name) }
            )
        }

        uiState.discPickerState?.let { pickerState ->
            DiscPickerModal(
                discs = pickerState.discs,
                focusIndex = uiState.discPickerFocusIndex,
                onSelectDisc = viewModel::selectDisc,
                onDismiss = viewModel::dismissDiscPicker
            )
        }

        val discPickerInputHandler = remember(viewModel) {
            DiscPickerInputHandler(
                getDiscs = { uiState.discPickerState?.discs ?: emptyList() },
                getFocusIndex = { uiState.discPickerFocusIndex },
                onFocusChange = { viewModel.setDiscPickerFocusIndex(it) },
                onSelect = { viewModel.selectDisc(it) },
                onDismiss = { viewModel.dismissDiscPicker() },
                wrapMode = { viewModel.menuWrapMode }
            )
        }

        val showDiscPicker = uiState.discPickerState != null
        LaunchedEffect(showDiscPicker) {
            if (showDiscPicker) viewModel.setDiscPickerFocusIndex(0)
        }
        ModalInputEffect(
            active = showDiscPicker,
            handler = discPickerInputHandler,
            inputDispatcher = inputDispatcher
        )

        uiState.variantPickerState?.let { pickerState ->
            com.nendo.argosy.ui.screens.gamedetail.modals.VariantPickerModal(
                variants = pickerState.variants,
                focusIndex = uiState.variantPickerFocusIndex,
                onSelectVariant = viewModel::selectVariant,
                onDismiss = viewModel::dismissVariantPicker
            )
        }

        val variantPickerInputHandler = remember(viewModel) {
            VariantPickerInputHandler(
                getVariants = { uiState.variantPickerState?.variants ?: emptyList() },
                getFocusIndex = { uiState.variantPickerFocusIndex },
                onFocusChange = { viewModel.setVariantPickerFocusIndex(it) },
                onSelect = { viewModel.selectVariant(it) },
                onDismiss = { viewModel.dismissVariantPicker() },
                wrapMode = { viewModel.menuWrapMode }
            )
        }

        val showVariantPicker = uiState.variantPickerState != null
        LaunchedEffect(showVariantPicker) {
            if (showVariantPicker) viewModel.setVariantPickerFocusIndex(0)
        }
        ModalInputEffect(
            active = showVariantPicker,
            handler = variantPickerInputHandler,
            inputDispatcher = inputDispatcher
        )

        val siblingChoiceState by viewModel.siblingChoiceState.collectAsState()
        com.nendo.argosy.ui.screens.common.SiblingChoiceModalHost(
            state = siblingChoiceState,
            onMove = viewModel::moveSiblingChoiceFocus,
            onFocus = viewModel::setSiblingChoiceFocus,
            onConfirm = viewModel::confirmSiblingChoice,
            onDismiss = viewModel::dismissSiblingChoice
        )

        uiState.memcardPickerState?.let { pickerState ->
            MemcardPickerModal(
                cards = pickerState.cards,
                focusIndex = uiState.memcardPickerFocusIndex,
                selectedCardPath = null,
                onSelectCard = viewModel::selectMemcard,
                onDismiss = viewModel::dismissMemcardPicker
            )
        }

        val memcardPickerInputHandler = remember(viewModel) {
            MemcardPickerInputHandler(
                getCards = { uiState.memcardPickerState?.cards ?: emptyList() },
                getFocusIndex = { uiState.memcardPickerFocusIndex },
                onFocusChange = { viewModel.setMemcardPickerFocusIndex(it) },
                onSelect = { viewModel.selectMemcard(it) },
                onDismiss = { viewModel.dismissMemcardPicker() },
                wrapMode = { viewModel.menuWrapMode }
            )
        }

        val showMemcardPicker = uiState.memcardPickerState != null
        LaunchedEffect(showMemcardPicker) {
            if (showMemcardPicker) viewModel.setMemcardPickerFocusIndex(0)
        }
        ModalInputEffect(
            active = showMemcardPicker,
            handler = memcardPickerInputHandler,
            inputDispatcher = inputDispatcher
        )


        LetterOverlay(
            letter = uiState.overlaySectionLabel,
            visible = uiState.showSectionOverlay
        )

        com.nendo.argosy.ui.cocoon.CocoonSessionDialogHost()
        com.nendo.argosy.ui.cocoon.CocoonPodsOverlay(onNavigate = onNavigate)
        DisposableEffect(Unit) {
            onDispose { com.nendo.argosy.ui.cocoon.CocoonSessionDialog.reset() }
        }
    }
}

@Composable
private fun LibraryHeader(
    platformName: String,
    gameCount: Int,
    activeFilterChips: List<ActiveFilterChipUi> = emptyList(),
    focusedGameTitle: String? = null,
    onPreviousPlatform: () -> Unit = {},
    onNextPlatform: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val aspectRatioClass = com.nendo.argosy.ui.theme.LocalUiScale.current.aspectRatioClass
    val maxNameLength = when (aspectRatioClass) {
        com.nendo.argosy.ui.theme.AspectRatioClass.ULTRA_TALL -> 12
        com.nendo.argosy.ui.theme.AspectRatioClass.TALL -> 16
        else -> null
    }
    val displayName = if (maxNameLength != null && platformName.length > maxNameLength) {
        platformName.take(maxNameLength - 1) + "…"
    } else {
        platformName
    }

    val showLibraryLabel = aspectRatioClass != com.nendo.argosy.ui.theme.AspectRatioClass.ULTRA_TALL

    val surfaceColor = MaterialTheme.colorScheme.surface
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (compact) Modifier else Modifier.height(Dimens.headerHeightLg))
            .background(
                Brush.verticalGradient(
                    0.0f to surfaceColor,
                    0.6f to surfaceColor.copy(alpha = 0.8f),
                    1.0f to Color.Transparent
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingMd),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.library_header_back_desc),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .clickableNoFocus(onClick = onBack)
                                .padding(Dimens.spacingXs)
                                .size(Dimens.iconMd)
                        )
                        Spacer(modifier = Modifier.width(Dimens.spacingXs))
                    }
                    if (showLibraryLabel) {
                        Text(
                            text = stringResource(R.string.library_header_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = if (showLibraryLabel) Modifier else Modifier.weight(1f)
                ) {
                    val navIconTint = MaterialTheme.colorScheme.onSurfaceVariant

                    Row(
                        modifier = Modifier
                            .clickableNoFocus(onClick = onPreviousPlatform)
                            .padding(Dimens.spacingSm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = InputIcons.TriggerLeft,
                            contentDescription = stringResource(R.string.library_header_previous_platform),
                            tint = navIconTint,
                            modifier = Modifier.size(Dimens.iconSm)
                        )
                    }

                    Spacer(modifier = Modifier.width(Dimens.spacingXs))

                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.width(Dimens.spacingXs))

                    Row(
                        modifier = Modifier
                            .clickableNoFocus(onClick = onNextPlatform)
                            .padding(Dimens.spacingSm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = InputIcons.TriggerRight,
                            contentDescription = stringResource(R.string.library_header_next_platform),
                            tint = navIconTint,
                            modifier = Modifier.size(Dimens.iconSm)
                        )
                    }
                }

                Text(
                    text = pluralStringResource(
                        R.plurals.library_header_game_count,
                        gameCount,
                        gameCount
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (activeFilterChips.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Dimens.spacingXs))
                ActiveFilterChipRow(
                    label = stringResource(R.string.library_header_active_filters_label),
                    chips = activeFilterChips
                )
            }

            if (focusedGameTitle != null) {
                Spacer(modifier = Modifier.height(Dimens.spacingXs))

                AnimatedContent(
                    targetState = focusedGameTitle,
                    transitionSpec = {
                        ContentTransform(
                            targetContentEnter = fadeIn(tween(150)),
                            initialContentExit = fadeOut(tween(100))
                        )
                    },
                    label = "focusedGameTitle"
                ) { title ->
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Header for the platform landing. It carries no platform stepper: the grid itself is the chooser,
 * so the trigger arrows the game list needs would point at nothing here. Nor does it echo the focused
 * cell's name, which the cell under the cursor is already saying.
 *
 * The count names the media libraries only when there are any, so a device with no media account
 * reads exactly as it did before rather than being told it has none.
 */
@Composable
private fun LibraryPlatformGridHeader(platformCount: Int, mediaLibraryCount: Int) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(LibraryPlatformGridHeaderHeight)
            .background(
                Brush.verticalGradient(
                    0.0f to surfaceColor,
                    0.6f to surfaceColor.copy(alpha = 0.8f),
                    1.0f to Color.Transparent
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingMd),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.library_landing_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            val platformText = pluralStringResource(
                R.plurals.library_landing_platform_count,
                platformCount,
                platformCount
            )
            val mediaText = if (mediaLibraryCount == 0) {
                null
            } else {
                pluralStringResource(
                    R.plurals.library_landing_media_library_count,
                    mediaLibraryCount,
                    mediaLibraryCount
                )
            }
            Text(
                text = listOfNotNull(platformText, mediaText).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Masonry variant of the library grid used when "Native Aspect Ratio" is on.
 * Each cover keeps its real proportions (resolved from the image bounds) so the
 * grid flows Pinterest-style instead of forcing every card into a fixed shape.
 * Focus navigation still runs through the regular reading-order navigator; the
 * focused card is scrolled to its real position via the staggered layout info.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryMasonryGrid(
    uiState: LibraryUiState,
    viewModel: LibraryViewModel,
    columnsCount: Int,
    gridSpacing: Dp,
    sidebarWidth: Dp,
    fallbackAspectRatio: Float,
    bottomPadding: Dp,
    headerHeightPx: Int,
    footerHeightPx: Int,
    onGameSelect: (Long) -> Unit
) {
    val initialIndex = remember { viewModel.gameIndexToGridIndex(uiState.focusedIndex) }
    val downloadIndicators = viewModel.downloadIndicators.collectAsState()
    val staggeredState = rememberLazyStaggeredGridState(initialFirstVisibleItemIndex = initialIndex)
    var isProgrammaticScroll by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.currentPlatformIndex) {
        staggeredState.scrollToItem(0)
    }

    LaunchedEffect(uiState.games.size) {
        if (uiState.games.isNotEmpty() && uiState.focusedIndex > 0) {
            staggeredState.scrollToItem(viewModel.gameIndexToGridIndex(uiState.focusedIndex))
        }
    }

    LaunchedEffect(uiState.focusedIndex, uiState.lastFocusMove) {
        if (uiState.lastFocusMove == null || uiState.games.isEmpty()) return@LaunchedEffect

        val layoutInfo = staggeredState.layoutInfo
        val viewportHeight = layoutInfo.viewportSize.height
        if (viewportHeight == 0) return@LaunchedEffect

        val gridIndex = viewModel.gameIndexToGridIndex(uiState.focusedIndex)
        val focusedItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == gridIndex }
        val itemHeight = focusedItem?.size?.height ?: 0
        val effectiveHeight = viewportHeight - headerHeightPx - footerHeightPx
        val centeringOffset = (effectiveHeight - itemHeight) / 2

        isProgrammaticScroll = true
        staggeredState.animateScrollToItem(
            index = gridIndex,
            scrollOffset = -centeringOffset
        )
        isProgrammaticScroll = false
    }

    LaunchedEffect(uiState.sectionJumpTrigger) {
        if (uiState.sectionJumpTrigger == 0 || uiState.games.isEmpty()) return@LaunchedEffect

        val gridIndex = viewModel.gameIndexToGridIndex(uiState.focusedIndex)
        val layoutInfo = staggeredState.layoutInfo
        val viewportHeight = layoutInfo.viewportSize.height
        if (viewportHeight == 0) {
            staggeredState.scrollToItem(gridIndex)
            return@LaunchedEffect
        }

        val focusedItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == gridIndex }
        val itemHeight = focusedItem?.size?.height ?: 0
        val effectiveHeight = viewportHeight - headerHeightPx - footerHeightPx
        val centeringOffset = if (itemHeight > 0) (effectiveHeight - itemHeight) / 2 else 0

        isProgrammaticScroll = true
        staggeredState.fastAnimateScrollToItem(
            index = gridIndex,
            scrollOffset = -centeringOffset
        )
        isProgrammaticScroll = false
    }

    LaunchedEffect(staggeredState) {
        snapshotFlow { staggeredState.isScrollInProgress }
            .collect { isScrolling ->
                if (isScrolling && !isProgrammaticScroll) {
                    viewModel.enterTouchMode()
                }
            }
    }

    val currentGridItems by rememberUpdatedState(uiState.gridItems)
    LaunchedEffect(staggeredState) {
        snapshotFlow { staggeredState.layoutInfo.visibleItemsInfo }
            .collect { visible ->
                val items = currentGridItems
                viewModel.setMasonryCells(
                    visible.mapNotNull { info ->
                        val gridItem = items.getOrNull(info.index)
                        if (gridItem is LibraryGridItem.Game) {
                            FocusCellBounds(
                                gameIndex = gridItem.gameIndex,
                                left = info.offset.x,
                                top = info.offset.y,
                                right = info.offset.x + info.size.width,
                                bottom = info.offset.y + info.size.height
                            )
                        } else null
                    }
                )
            }
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.setMasonryCells(emptyList()) }
    }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(columnsCount),
        state = staggeredState,
        contentPadding = PaddingValues(
            start = gridSpacing,
            end = gridSpacing + sidebarWidth,
            top = Dimens.headerHeightLg,
            bottom = withNavBarClearance(bottomPadding + gridSpacing)
        ),
        horizontalArrangement = Arrangement.spacedBy(gridSpacing),
        verticalItemSpacing = gridSpacing,
        modifier = Modifier.fillMaxSize()
    ) {
        uiState.gridItems.forEachIndexed { _, gridItem ->
            when (gridItem) {
                is LibraryGridItem.Header -> item(
                    key = "header-${gridItem.label}",
                    span = StaggeredGridItemSpan.FullLine
                ) {
                    SectionDivider(label = gridItem.label)
                }
                is LibraryGridItem.Game -> item(
                    key = gridItem.game.id,
                    span = StaggeredGridItemSpan.SingleLane
                ) {
                    val isFocused = gridItem.gameIndex == uiState.focusedIndex
                    val coverPath = uiState.repairedCoverPaths[gridItem.game.id]
                        ?: com.nendo.argosy.ui.common.rememberResolvedCoverPath(gridItem.game.id, gridItem.game.coverPath)
                    val ratio = gridItem.game.box3dPath?.takeIf { uiState.boxArt3d }
                        ?.let { rememberCoverAspectRatio(it, fallbackAspectRatio) }
                        ?: rememberCoverAspectRatio(coverPath, fallbackAspectRatio)
                    LibraryGameCard(
                        game = gridItem.game,
                        isFocused = isFocused,
                        showFocus = !uiState.isTouchMode || uiState.hasSelectedGame,
                        cardHeight = null,
                        showPlatformBadge = uiState.currentPlatformIndex < 0,
                        coverPathOverride = uiState.repairedCoverPaths[gridItem.game.id],
                        onCoverLoadFailed = viewModel::repairCoverImage,
                        downloadIndicatorFor = {
                            downloadIndicators.value[it] ?: GameDownloadIndicator.NONE
                        },
                        useBoxArt = uiState.boxArt3d,
                        onClick = { viewModel.handleItemTap(gridItem.gameIndex, onGameSelect) },
                        onLongClick = { viewModel.handleItemLongPress(gridItem.gameIndex) },
                        modifier = Modifier
                            .aspectRatio(ratio)
                            .zIndex(if (isFocused) 1f else 0f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryGameCard(
    game: LibraryGameUi,
    isFocused: Boolean,
    showFocus: Boolean,
    cardHeight: Dp?,
    showPlatformBadge: Boolean = true,
    coverPathOverride: String? = null,
    onCoverLoadFailed: ((Long, String) -> Unit)? = null,
    downloadIndicatorFor: (Long) -> GameDownloadIndicator = { GameDownloadIndicator.NONE },
    useBoxArt: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val effectiveFocused = isFocused && showFocus
    val saturation: Float? = null // Cocoon keeps unfocused covers in full colour
    val indicatorFor by rememberUpdatedState(downloadIndicatorFor)
    val downloadIndicator by remember(game.id) { derivedStateOf { indicatorFor(game.id) } }
    GameCard(
        game = HomeGameUi(
            id = game.id,
            title = game.title,
            platformId = game.platformId,
            platformSlug = game.platformSlug,
            platformDisplayName = game.platformDisplayName,
            coverPath = game.coverPath,
            boxSpinePath = game.boxSpinePath,
            box3dPath = game.box3dPath,
            gradientColors = game.gradientColors,
            backgroundPath = null,
            developer = null,
            releaseYear = null,
            genre = null,
            isFavorite = game.isFavorite,
            isDownloaded = game.isDownloaded
        ),
        isFocused = effectiveFocused,
        showPlatformBadge = showPlatformBadge,
        coverPathOverride = coverPathOverride,
        onCoverLoadFailed = onCoverLoadFailed,
        downloadIndicator = downloadIndicator,
        saturationOverride = saturation,
        useBoxArt = useBoxArt,
        modifier = modifier
            .fillMaxWidth()
            .then(if (cardHeight != null) Modifier.height(cardHeight) else Modifier)
            .clickableNoFocus(onClick = onClick, onLongClick = onLongClick)
    )
}

@Composable
private fun LibraryPlatformGridFooter(
    isReordering: Boolean,
    canReorder: Boolean,
    onHintClick: (InputButton) -> Unit
) {
    val liftedHints = liftedReorderHints(
        move = stringResource(R.string.library_landing_hint_move),
        cancel = stringResource(R.string.library_landing_hint_cancel),
        moveButton = InputButton.DPAD
    )
    val reorderHint = stringResource(R.string.library_landing_hint_reorder)
    val hints = when {
        isReordering -> liftedHints
        canReorder -> listOf(InputButton.Y to reorderHint)
        else -> emptyList()
    }
    FooterHints(
        hints = hints,
        onHintClick = onHintClick,
        forced = isReordering
    )
}

@Composable
private fun LibraryFooter(
    focusedGame: LibraryGameUi?,
    isViewingHidden: Boolean = false,
    canSwitchPlatform: Boolean = false,
    isSectionRailFocused: Boolean = false,
    quickNavigation: Boolean = true,
    canJumpSection: Boolean = false,
    onHintClick: ((InputButton) -> Unit)? = null
) {
    val selectSwapMode = com.nendo.argosy.ui.dualscreen.selectSwapModeState()
    val selectSwapsRoles = selectSwapMode == SelectSwapMode.TAP
    val railHints = listOf(
        InputButton.A to stringResource(R.string.library_footer_hint_rail_jump),
        InputButton.B to stringResource(R.string.library_footer_hint_rail_back)
    )
    val hints = if (isSectionRailFocused) railHints else buildList {
        if (canSwitchPlatform) {
            val platformButtons = if (quickNavigation) InputButton.LT_RT else InputButton.LB_RB
            add(platformButtons to stringResource(R.string.library_footer_hint_switch_platform))
        }
        if (canJumpSection) {
            val sectionButton = if (quickNavigation) InputButton.RIGHT_STICK_VERTICAL else InputButton.LT_RT
            add(sectionButton to stringResource(R.string.library_footer_hint_jump_section))
        }
        add(
            InputButton.A to stringResource(
                if (selectSwapsRoles && focusedGame != null) {
                    R.string.library_footer_hint_details_hold_quick_menu
                } else {
                    R.string.library_footer_hint_details
                }
            )
        )
        add(InputButton.Y to when {
            isViewingHidden -> stringResource(R.string.library_footer_hint_unhide)
            focusedGame?.isFavorite == true -> stringResource(R.string.library_footer_hint_unfavorite)
            else -> stringResource(R.string.library_footer_hint_favorite)
        })
        add(InputButton.X to stringResource(R.string.library_footer_hint_filter))
        add(
            InputButton.SELECT to stringResource(
                when (selectSwapMode) {
                    SelectSwapMode.TAP -> R.string.library_footer_hint_swap_screens
                    SelectSwapMode.HOLD -> R.string.library_footer_hint_quick_menu_hold_swap_screens
                    null -> R.string.library_footer_hint_quick_menu
                }
            )
        )
    }
    FooterHints(
        hints = hints,
        onHintClick = onHintClick
    )
}

@Composable
internal fun SectionDivider(
    label: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 0.dp else Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun EmptyLibrary(platformName: String?, activeFilters: ActiveFilters) {
    val isFiltered = activeFilters.activeCount > 0
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (platformName != null) {
                    stringResource(R.string.library_empty_title_platform, platformName)
                } else {
                    stringResource(R.string.library_empty_title_all)
                },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            Text(
                text = if (isFiltered) {
                    stringResource(
                        R.string.library_empty_filtered_body,
                        activeFilters.summary(LocalContext.current)
                    )
                } else {
                    stringResource(R.string.library_empty_body)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            if (isFiltered) {
                Spacer(modifier = Modifier.height(Dimens.spacingMd))
                FooterHint(
                    button = InputButton.X,
                    action = stringResource(R.string.library_empty_filtered_hint)
                )
            }
        }
    }
}

@Composable
private fun FilterMenuOverlay(
    uiState: LibraryUiState,
    onDismiss: () -> Unit,
    onCategorySelect: (FilterCategory) -> Unit,
    onOptionSelect: (Int) -> Unit,
    onSearchQueryChange: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val filterContext = LocalContext.current
    val options = uiState.currentCategoryOptions(filterContext)
    val categories = uiState.availableCategories
    val isMultiSelect = uiState.isCurrentCategoryMultiSelect
    val selectedIndices = uiState.selectedIndicesInCurrentCategory
    val isSearchCategory = uiState.currentFilterCategory == FilterCategory.SEARCH
    val searchQuery = uiState.activeFilters.searchQuery
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(uiState.filterOptionIndex) {
        if (!isSearchCategory && options.isNotEmpty() && uiState.filterOptionIndex in options.indices) {
            val viewportHeight = listState.layoutInfo.viewportSize.height
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val itemHeight = visibleItems.firstOrNull()?.size ?: 0

            if (itemHeight > 0 && viewportHeight > 0) {
                val centerOffset = (viewportHeight - itemHeight) / 2
                val paddingBuffer = (itemHeight * Motion.scrollPaddingPercent).toInt()
                listState.animateScrollToItem(
                    index = uiState.filterOptionIndex,
                    scrollOffset = -centerOffset + paddingBuffer
                )
            } else {
                listState.animateScrollToItem(uiState.filterOptionIndex)
            }
        }
    }

    LaunchedEffect(isSearchCategory) {
        if (isSearchCategory && uiState.screenWidthDp > 900) {
            focusRequester.requestFocus()
        }
    }

    ModalPresenceEffect()
    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor)
            .clickableNoFocus(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(Dimens.modalWidthLg + 80.dp)
                .clip(RoundedCornerShape(Dimens.radiusLg))
                .background(MaterialTheme.colorScheme.surface)
                .clickableNoFocus(enabled = false) {}
                .padding(Dimens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
        ) {
            Text(
                text = stringResource(R.string.library_filter_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            if (uiState.activeFilters.activeCount > 0) {
                Text(
                    text = stringResource(
                        R.string.library_filter_active_summary,
                        uiState.activeFilters.summary(LocalContext.current)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val categoryRowState = rememberLazyListState()
            val currentCategoryIndex = categories.indexOf(uiState.currentFilterCategory)
            LaunchedEffect(currentCategoryIndex) {
                if (currentCategoryIndex >= 0) {
                    categoryRowState.animateScrollToItemCentered(currentCategoryIndex)
                }
            }
            androidx.compose.foundation.lazy.LazyRow(
                state = categoryRowState,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                items(categories.size) { index ->
                    val category = categories[index]
                    val isCurrent = category == uiState.currentFilterCategory
                    val hasActiveFilters = uiState.activeFilters.isActive(category)

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Dimens.radiusMd))
                            .clickableNoFocus { onCategorySelect(category) }
                            .then(
                                if (hasActiveFilters && !isCurrent) {
                                    Modifier.border(
                                        width = Dimens.borderMedium,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(Dimens.radiusMd)
                                    )
                                } else Modifier
                            )
                            .background(
                                if (isCurrent) LocalArgosyTheme.current.focusAccent.copy(alpha = 0.15f)
                                    .compositeOver(MaterialTheme.colorScheme.surface)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
                    ) {
                        Text(
                            text = stringResource(category.labelRes),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isCurrent) lerp(LocalArgosyTheme.current.focusAccent, Color.White, 0.45f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            if (isSearchCategory) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.radiusMd))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(Dimens.spacingMd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Dimens.iconSm)
                    )
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            Box {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.library_filter_search_placeholder),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }

                if (options.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.library_filter_recent_searches),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 150.dp),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                ) {
                    itemsIndexed(options) { index, recentQuery ->
                        val isFocused = index == uiState.filterOptionIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Dimens.radiusSm))
                                .then(
                                    if (isFocused) Modifier.background(LocalArgosyTheme.current.focusAccent.copy(alpha = 0.15f))
                                    else Modifier
                                )
                                .clickableNoFocus { onOptionSelect(index) }
                                .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = if (isFocused) lerp(LocalArgosyTheme.current.focusAccent, Color.White, 0.45f)
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(Dimens.iconSm)
                            )
                            Text(
                                text = recentQuery,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isFocused) lerp(LocalArgosyTheme.current.focusAccent, Color.White, 0.45f)
                                        else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                ) {
                    itemsIndexed(options) { index, option ->
                        val isFocused = index == uiState.filterOptionIndex
                        val isSelected = when {
                            isMultiSelect -> index in selectedIndices
                            uiState.currentFilterCategory == FilterCategory.SORT -> index == uiState.selectedSortIndex
                            uiState.currentFilterCategory == FilterCategory.PLAYERS -> index == uiState.selectedPlayersIndex
                            else -> index == uiState.selectedSourceIndex
                        }
                        FilterOptionItem(
                            label = option,
                            isFocused = isFocused,
                            isSelected = isSelected,
                            onClick = { onOptionSelect(index) }
                        )
                    }
                }
            }

            FooterHints(
                hints = if (isSearchCategory) {
                    listOf(
                        InputButton.X to stringResource(R.string.library_filter_hint_clear),
                        InputButton.B to stringResource(R.string.library_filter_hint_close_search)
                    )
                } else {
                    listOf(
                        InputButton.X to stringResource(R.string.library_filter_hint_reset),
                        InputButton.A to if (isMultiSelect) {
                            stringResource(R.string.library_filter_hint_toggle)
                        } else {
                            stringResource(R.string.library_filter_hint_select)
                        },
                        InputButton.B to stringResource(R.string.library_filter_hint_close_options)
                    )
                }
            )
        }
    }
}

@Composable
private fun FilterOptionItem(
    label: String,
    isFocused: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .clickableNoFocus(onClick = onClick)
            .background(
                when {
                    isFocused -> LocalArgosyTheme.current.focusAccent.copy(alpha = 0.15f)
                        .compositeOver(MaterialTheme.colorScheme.surface)
                    isSelected -> MaterialTheme.colorScheme.surfaceVariant
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
            )
            .padding(Dimens.spacingMd),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isFocused) lerp(LocalArgosyTheme.current.focusAccent, Color.White, 0.45f)
                    else MaterialTheme.colorScheme.onSurface
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = if (isFocused) lerp(LocalArgosyTheme.current.focusAccent, Color.White, 0.45f)
                       else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Dimens.iconSm)
            )
        }
    }
}

@Composable
private fun QuickMenuOverlay(
    game: LibraryGameUi,
    rows: List<LibraryQuickMenuRow>,
    focusIndex: Int,
    onDismiss: () -> Unit,
    onPrimaryAction: () -> Unit,
    onFavorite: () -> Unit,
    onDetails: () -> Unit,
    onLaunchOnScreen: (Int) -> Unit = {},
    onAddToCollection: () -> Unit,
    onAddToGrid: () -> Unit,
    onActiveVariant: () -> Unit,
    onRefresh: () -> Unit,
    onResyncPlatform: () -> Unit,
    onDelete: () -> Unit,
    onRemoveFromLibrary: () -> Unit,
    onHide: () -> Unit,
    onEdit: () -> Unit = {},
    onAchievements: () -> Unit = {}
) {
    val primaryIcon = when {
        game.needsInstall -> Icons.Default.InstallMobile
        game.isDownloaded -> Icons.Outlined.PlayArrow
        else -> Icons.Default.Download
    }
    val primaryLabel = when {
        game.needsInstall -> stringResource(R.string.library_quickmenu_install)
        game.isDownloaded -> stringResource(R.string.cocoon_menu_launch)
        else -> stringResource(R.string.library_quickmenu_download)
    }

    data class MenuEntry(
        val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
        val label: String,
        val isDangerous: Boolean = false,
        /** Right opens another step of rows (Launch ▸ the two screens). */
        val hasSubmenu: Boolean = false,
        val onClick: () -> Unit
    )

    val favoriteLabel = if (game.isFavorite) {
        stringResource(R.string.library_quickmenu_unfavorite)
    } else {
        stringResource(R.string.library_quickmenu_favorite)
    }
    val detailsLabel = stringResource(R.string.library_quickmenu_details)
    val addToCollectionLabel = stringResource(R.string.library_quickmenu_add_to_collection)
    val addToGridLabel = stringResource(R.string.library_quickmenu_add_to_grid)
    val activeVariantLabel = stringResource(R.string.library_quickmenu_active_variant)
    val refreshLabel = stringResource(R.string.cocoon_scrape_game)
    val resyncLabel = stringResource(R.string.library_quickmenu_resync_platform)
    val deleteLabel = if (game.isAndroidApp && game.isDownloaded) {
        stringResource(R.string.library_quickmenu_uninstall)
    } else {
        stringResource(R.string.library_quickmenu_delete_download)
    }
    val removeFromLibraryLabel = stringResource(R.string.library_quickmenu_remove_from_library)
    val hideLabel = if (game.isHidden) {
        stringResource(R.string.library_quickmenu_show)
    } else {
        stringResource(R.string.library_quickmenu_hide)
    }

    val editLabel = stringResource(R.string.cocoon_menu_edit)
    val achievementsLabel = stringResource(R.string.cocoon_menu_achievements)
    val launchScreen1Label = stringResource(R.string.cocoon_launch_on_screen, 1)
    val jingleLabel = stringResource(R.string.cocoon_jingle)
    val launchScreen2Label = stringResource(R.string.cocoon_launch_on_screen, 2)
    // Launch can only offer a screen when the game is here and the device has two of them.
    val launchScreenChoice = launchScreenChoiceFor(game)

    fun LibraryQuickMenuRow.toEntry(): MenuEntry = when (this) {
        LibraryQuickMenuRow.PRIMARY -> MenuEntry(
            primaryIcon,
            primaryLabel,
            hasSubmenu = launchScreenChoice,
            onClick = onPrimaryAction
        )
        LibraryQuickMenuRow.LAUNCH_SCREEN_1 -> MenuEntry(Icons.Outlined.Tv, launchScreen1Label, onClick = { onLaunchOnScreen(0) })
        LibraryQuickMenuRow.LAUNCH_SCREEN_2 -> MenuEntry(Icons.Outlined.Tv, launchScreen2Label, onClick = { onLaunchOnScreen(1) })
        LibraryQuickMenuRow.FAVORITE -> MenuEntry(
            if (game.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
            favoriteLabel,
            onClick = onFavorite
        )
        LibraryQuickMenuRow.DETAILS -> MenuEntry(Icons.Default.Info, detailsLabel, onClick = onDetails)
        LibraryQuickMenuRow.ADD_TO_COLLECTION -> MenuEntry(
            Icons.AutoMirrored.Filled.PlaylistAdd,
            addToCollectionLabel,
            onClick = onAddToCollection
        )
        LibraryQuickMenuRow.ADD_TO_GRID -> MenuEntry(Icons.Default.GridView, addToGridLabel, onClick = onAddToGrid)
        LibraryQuickMenuRow.ACTIVE_VARIANT -> MenuEntry(
            Icons.Default.Layers,
            activeVariantLabel,
            onClick = onActiveVariant
        )
        LibraryQuickMenuRow.RESYNC_PLATFORM -> MenuEntry(Icons.Outlined.Refresh, resyncLabel, onClick = onResyncPlatform)
        LibraryQuickMenuRow.DELETE -> MenuEntry(
            Icons.Default.DeleteOutline,
            deleteLabel,
            isDangerous = true,
            onClick = onDelete
        )
        LibraryQuickMenuRow.REMOVE_FROM_LIBRARY -> MenuEntry(
            Icons.Default.RemoveCircleOutline,
            removeFromLibraryLabel,
            isDangerous = true,
            onClick = onRemoveFromLibrary
        )
        LibraryQuickMenuRow.HIDE -> MenuEntry(
            if (game.isHidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
            hideLabel,
            onClick = onHide
        )
        LibraryQuickMenuRow.ACHIEVEMENTS -> MenuEntry(
            Icons.Outlined.EmojiEvents,
            achievementsLabel,
            onClick = onAchievements
        )
    }

    val options = rows.filterNot { it.isDangerous }.map { it.toEntry() }
    val dangerousOptions = rows.filter { it.isDangerous }.map { it.toEntry() }

    ModalPresenceEffect()
    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)
    val listState = rememberLazyListState()
    val listIndex = if (focusIndex < options.size) focusIndex else focusIndex + 1
    FocusedScroll(listState = listState, focusedIndex = listIndex)

    // Cocoon's options menu: the item enlarged on the left with its name, the actions on the right.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickableNoFocus(onClick = onDismiss)
    ) {
        val screenHeight = maxHeight
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.spacingLg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(0.45f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val coverSize = (screenHeight * 0.38f).coerceAtMost(220.dp)
                // Cocoon: the cover grows up into place beside the menu.
                val grow = remember { androidx.compose.animation.core.Animatable(0.5f) }
                LaunchedEffect(game.id) {
                    grow.animateTo(1f, androidx.compose.animation.core.tween(220, easing = com.nendo.argosy.ui.theme.Motion.argosyEase))
                }
                coil.compose.AsyncImage(
                    model = com.nendo.argosy.ui.common.rememberFileImageModel(game.coverPath),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .size(coverSize)
                        .graphicsLayer { scaleX = grow.value; scaleY = grow.value }
                        .clip(RoundedCornerShape(16.dp))
                        .background(com.nendo.argosy.ui.theme.CocoonSurface.Chip)
                )
                Spacer(modifier = Modifier.height(Dimens.spacingMd))
                Text(
                    text = game.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = com.nendo.argosy.ui.theme.CocoonSurface.TextPrimary,
                    maxLines = 2,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(0.55f)
                    .heightIn(max = screenHeight * 0.9f)
                    .clickableNoFocus(enabled = false) {},
                // Cocoon: one unified panel, rows split by hairlines, brackets room at the sides.
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp, horizontal = 10.dp)
            ) {
                val all = options + dangerousOptions
                itemsIndexed(all) { index, entry ->
                    com.nendo.argosy.ui.cocoon.CocoonMenuRow(
                        icon = entry.icon,
                        label = entry.label,
                        focused = focusIndex == index,
                        isFirst = index == 0,
                        isLast = index == all.lastIndex,
                        onClick = entry.onClick,
                        color = if (entry.isDangerous) LocalArgosyTheme.current.destructive
                            else com.nendo.argosy.ui.theme.CocoonSurface.TextPrimary,
                        // The chevron is the same right-press hint the dock app's Launch carries.
                        trailing = if (entry.hasSubmenu) {
                            {
                                androidx.compose.material3.Icon(
                                    androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = com.nendo.argosy.ui.theme.CocoonSurface.TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else null
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    label: String,
    value: String? = null,
    isFocused: Boolean = false,
    isDangerous: Boolean = false,
    onClick: () -> Unit
) {
    val contentColor = if (isDangerous) LocalArgosyTheme.current.destructive else com.nendo.argosy.ui.theme.CocoonSurface.TextPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .cocoonBrackets(focused = isFocused, outset = 3.dp)
            .background(com.nendo.argosy.ui.theme.CocoonSurface.Chip, RoundedCornerShape(14.dp))
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
            color = contentColor,
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = com.nendo.argosy.ui.theme.CocoonSurface.TextSecondary
            )
        }
    }
}

@Composable
private fun LetterOverlay(
    letter: String,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(100)),
        exit = fadeOut(tween(400)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = letter,
                style = MaterialTheme.typography.displayLarge,
                fontSize = 120.sp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                fontWeight = FontWeight.Bold
            )
        }
    }
}


