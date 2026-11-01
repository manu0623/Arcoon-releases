package com.nendo.argosy.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick as semanticClick
import androidx.compose.ui.semantics.onLongClick as semanticLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.ui.coil.AppIconData
import com.nendo.argosy.ui.input.CapturingInputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.input.ModalPresenceEffect
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.theme.generated.DimensionTokens
import com.nendo.argosy.ui.util.horizontalEdgeFade
import com.nendo.argosy.ui.util.touchOnly

const val APP_BAR_NOTHING_FOCUSED = -2
const val APP_BAR_DRAWER_INDEX = -1

data class DisplayFocusTarget(val displayId: Int, val number: Int)

@Composable
fun CompanionAppBar(
    apps: List<String>,
    onAppClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusedIndex: Int = APP_BAR_NOTHING_FOCUSED,
    reorder: DockReorderSession? = null,
    onReorderStart: (String) -> Long? = { null },
    onReorderTarget: (Long, Int) -> Unit = { _, _ -> },
    onReorderSave: (Long) -> Unit = {},
    onReorderCancel: (Long) -> Unit = {},
    onAppHold: (String) -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    onKeyboardToggle: (() -> Unit)? = null,
    focusDisplays: List<DisplayFocusTarget> = emptyList(),
    focusPickerOpen: Boolean = false,
    focusPickerIndex: Int = 0,
    onFocusPickerToggle: (() -> Unit)? = null,
    onFocusPickerMove: (Int) -> Unit = {},
    onFocusDisplay: (Int) -> Unit = {},
    onSwapRoles: (() -> Unit)? = null,
    toolsOpen: Boolean = false,
    toolIndex: Int = 1,
    onToolsToggle: () -> Unit = {},
    onToolFocus: (Int) -> Unit = {},
    onToolsDismiss: () -> Unit = {},
    controllerInputEnabled: Boolean = true,
    maximumWidth: Dp = Dimens.breadcrumbMaxWidth
) {
    var showToolFocus by remember { mutableStateOf(true) }
    LaunchedEffect(toolsOpen) {
        if (!toolsOpen) showToolFocus = true
    }
    val uiScale = LocalUiScale.current
    val dockScale = maxOf(uiScale.scale, minimumTouchTarget / DimensionTokens.Icon.xl.dp)
    CompositionLocalProvider(LocalUiScale provides uiScale.copy(scale = dockScale)) {
        val listState = rememberLazyListState()
        val appIconSize = Dimens.iconXl
        val controlIconSize = Dimens.iconMd
        val slot = maxOf(minimumTouchTarget, appIconSize)
        val gap = Dimens.spacingSm
        val padding = Dimens.spacingMd
        val ringOutset = Dimens.spacingXs
        val density = LocalDensity.current
        val drag = rememberDockDragReorderState(
            listState, with(density) { padding.toPx() },
            onStart = { onReorderStart(it) },
            onTarget = onReorderTarget, onSave = onReorderSave, onCancel = onReorderCancel,
            onHold = onAppHold
        )
        DockReorderEffects(listState, drag, apps, reorder)
        val reorderActions = dockReorderActions(
            listState, reorder,
            onTarget = onReorderTarget, onSave = onReorderSave, onCancel = onReorderCancel
        )
        val dockLabel = stringResource(R.string.dock_accessibility_label)
        val height = slot + gap * 2
        val caretRotation by animateFloatAsState(
            targetValue = if (toolsOpen) 180f else 0f,
            animationSpec = tween(Motion.durationMicro),
            label = "dock-caret"
        )

        LaunchedEffect(focusedIndex, apps.size, reorder?.controller) {
            if (reorder?.controller != false && focusedIndex in apps.indices) {
                val layout = listState.layoutInfo
                val item = layout.visibleItemsInfo.firstOrNull { it.index == focusedIndex }
                if (reorder == null || item == null || item.offset < layout.viewportStartOffset ||
                    item.offset + item.size > layout.viewportEndOffset) {
                    listState.animateScrollToItem(focusedIndex)
                }
            }
        }

        val toolsAvailable = dockToolsEnabled(
            onSwapRoles, onKeyboardToggle, onFocusPickerToggle, focusDisplays
        ).any { it }
        BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            val widths = dockWidths(
                maximum = maxWidth.value,
                preferredMaximum = maximumWidth.value,
                appCount = apps.size,
                slot = slot.value,
                gap = gap.value,
                padding = padding.value,
                appIconSize = appIconSize.value,
                controlIconSize = controlIconSize.value,
                controlCount = if (toolsAvailable) 2 else 1
            )
            Row(
                modifier = Modifier
                    .width(widths.width.dp)
                    .height(height)
                    .frostedSurface()
                    .padding(horizontal = widths.padding.dp, vertical = gap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DockControl(slot, focusedIndex == APP_BAR_DRAWER_INDEX, onClick = { if (reorder == null) onOpenDrawer() }) { tint ->
                    Icon(
                        Icons.Default.Apps,
                        stringResource(R.string.dual_home_hint_app_bar_all_apps),
                        tint = tint,
                        modifier = Modifier.size(controlIconSize)
                    )
                }
                if (apps.isNotEmpty() || toolsAvailable) Spacer(Modifier.width(widths.groupGap.dp))
                if (apps.isNotEmpty()) {
                    Box(
                        Modifier.width(widths.appsWidth.dp)
                            .height(slot)
                            .semantics { contentDescription = dockLabel; customActions = reorderActions }
                            .dockDragContainer(drag)
                    ) {
                        LazyRow(
                            state = listState,
                            modifier = Modifier.requiredSize(widths.appsWidth.dp + ringOutset * 2, slot + ringOutset * 2)
                                .horizontalEdgeFade(listState, fadeWidth = padding),
                            contentPadding = PaddingValues(ringOutset),
                            horizontalArrangement = Arrangement.spacedBy(gap),
                            userScrollEnabled = reorder == null && widths.appsWidth <
                                apps.size * slot.value + (apps.size - 1) * gap.value
                        ) {
                            itemsIndexed(apps, key = { _, app -> app }) { index, app ->
                                CompanionAppItem(
                                    packageName = app,
                                    isFocused = if (reorder != null) app == reorder.heldPackage else index == focusedIndex,
                                    onClick = { if (reorder == null && drag.draggingPackage == null) onAppClick(app) },
                                    onSemanticReorder = { drag.startAccessible(app) },
                                    reorderActions = if (reorder?.heldPackage == app) reorderActions else emptyList(),
                                    modifier = Modifier
                                        .animateItem(
                                            fadeInSpec = null,
                                            placementSpec = if (drag.draggingPackage == app) snap() else
                                                tween(Motion.durationSlide, easing = Motion.argosyEase),
                                            fadeOutSpec = null
                                        )
                                        .dockDraggedItem(drag, app, reorder?.heldPackage == app)
                                )
                            }
                        }
                    }
                    if (toolsAvailable) Spacer(Modifier.width(widths.groupGap.dp))
                } else if (toolsAvailable) {
                    Spacer(Modifier.width(widths.appsWidth.dp + widths.groupGap.dp))
                }
                if (toolsAvailable) Box {
                    DockControl(
                        slot,
                        focusedIndex == apps.size || toolsOpen,
                        onClick = {
                            if (reorder != null) return@DockControl
                            showToolFocus = false
                            if (toolsOpen && focusPickerOpen) onFocusPickerToggle?.invoke()
                            onToolsToggle()
                        }
                    ) { tint ->
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            stringResource(R.string.dual_companion_app_bar_tools_description),
                            tint = tint,
                            modifier = Modifier.size(controlIconSize)
                                .graphicsLayer { rotationZ = caretRotation }
                        )
                    }
                    if (toolsOpen) {
                        DockToolsPopup(
                            slot = slot,
                            height = height,
                            gap = gap,
                            padding = padding,
                            toolIndex = toolIndex,
                            showToolFocus = controllerInputEnabled && showToolFocus,
                            onToolFocusVisibilityChange = { showToolFocus = it },
                            onToolFocus = onToolFocus,
                            onDismiss = onToolsDismiss,
                            onSwapRoles = onSwapRoles,
                            onKeyboardToggle = onKeyboardToggle,
                            displays = focusDisplays,
                            pickerOpen = focusPickerOpen,
                            pickerIndex = focusPickerIndex,
                            onPickerToggle = onFocusPickerToggle,
                            onPickerMove = onFocusPickerMove,
                            onFocusDisplay = onFocusDisplay,
                            controllerInputEnabled = controllerInputEnabled
                        )
                    }
                }
            }
        }
    }
}

internal class DockPopupPositionProvider(
    private val inset: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset = IntOffset(
        x = dockPopupLeft(anchorBounds.center.x, popupContentSize.width, windowSize.width, inset),
        y = (anchorBounds.bottom - popupContentSize.height).coerceIn(
            inset.coerceAtMost((windowSize.height - popupContentSize.height).coerceAtLeast(0)),
            (windowSize.height - popupContentSize.height - inset).coerceAtLeast(inset)
        )
    )
}

@Composable
private fun DockToolsPopup(
    slot: Dp,
    height: Dp,
    gap: Dp,
    padding: Dp,
    toolIndex: Int,
    showToolFocus: Boolean,
    onToolFocusVisibilityChange: (Boolean) -> Unit,
    onToolFocus: (Int) -> Unit,
    onDismiss: () -> Unit,
    onSwapRoles: (() -> Unit)?,
    onKeyboardToggle: (() -> Unit)?,
    displays: List<DisplayFocusTarget>,
    pickerOpen: Boolean,
    pickerIndex: Int,
    onPickerToggle: (() -> Unit)?,
    onPickerMove: (Int) -> Unit,
    onFocusDisplay: (Int) -> Unit,
    controllerInputEnabled: Boolean
) {
    val enabled = dockToolsEnabled(onSwapRoles, onKeyboardToggle, onPickerToggle, displays)
    val dismiss = {
        if (pickerOpen) onPickerToggle?.invoke()
        onDismiss()
    }
    val activate: (Int) -> Unit = { index ->
        if (enabled.getOrNull(index) == true) {
            onToolFocus(index)
            when (index) {
                0 -> { dismiss(); onSwapRoles?.invoke() }
                1 -> { dismiss(); onKeyboardToggle?.invoke() }
                2 -> onPickerToggle?.invoke()
            }
        }
    }

    if (controllerInputEnabled) {
        val move by rememberUpdatedState<(Int) -> Unit> { delta ->
            onToolFocusVisibilityChange(true)
            if (pickerOpen) onPickerMove(delta)
            else onToolFocus(dockToolFocusMove(toolIndex, delta, enabled))
        }
        val confirm by rememberUpdatedState<() -> Unit> {
            onToolFocusVisibilityChange(true)
            if (pickerOpen) {
                displays.getOrNull(pickerIndex)?.let { onFocusDisplay(it.displayId) }
                onDismiss()
            } else {
                activate(toolIndex)
            }
        }
        val close by rememberUpdatedState(dismiss)
        val up by rememberUpdatedState<() -> Unit> {
            onToolFocusVisibilityChange(true)
            if (pickerOpen) onPickerMove(-1)
        }
        val down by rememberUpdatedState<() -> Unit> {
            onToolFocusVisibilityChange(true)
            if (pickerOpen) onPickerMove(1) else dismiss()
        }
        val handler = remember {
            object : CapturingInputHandler {
                override fun onLeft(): InputResult { move(-1); return InputResult.HANDLED }
                override fun onRight(): InputResult { move(1); return InputResult.HANDLED }
                override fun onUp(): InputResult { up(); return InputResult.HANDLED }
                override fun onDown(): InputResult { down(); return InputResult.HANDLED }
                override fun onConfirm(): InputResult { confirm(); return InputResult.HANDLED }
                override fun onBack(): InputResult { close(); return InputResult.HANDLED }
            }
        }
        ModalPresenceEffect()
        ModalInputEffect(active = true, handler = handler)
    }

    val density = LocalDensity.current
    val positionProvider = remember(density, padding) {
        with(density) { DockPopupPositionProvider(padding.roundToPx()) }
    }
    val pickerState = rememberLazyListState()
    LaunchedEffect(pickerOpen, pickerIndex, displays.size) {
        if (pickerOpen && pickerIndex in displays.indices) pickerState.animateScrollToItem(pickerIndex)
    }
    val pickerMaxHeight = (LocalConfiguration.current.screenHeightDp.dp - height * 2 - padding * 2)
        .coerceAtLeast(slot)
    val pickerHeight = (slot * displays.size + gap * (displays.size - 1).coerceAtLeast(0) + padding * 2)
        .coerceAtMost(pickerMaxHeight)
    val popupWidth = slot * 3 + gap * 2 + padding * 2

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = dismiss,
        properties = PopupProperties(focusable = false, clippingEnabled = true)
    ) {
        Column(horizontalAlignment = Alignment.End) {
            Column(
                Modifier.width(popupWidth).height(pickerHeight + gap).touchOnly(dismiss),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Bottom
            ) {
                AnimatedVisibility(
                    visible = pickerOpen,
                    enter = fadeIn(tween(Motion.durationMicro, easing = Motion.argosyEase)),
                    exit = fadeOut(tween(Motion.durationMicro, easing = Motion.argosyEase))
                ) {
                    Column {
                        LazyColumn(
                            state = pickerState,
                            modifier = Modifier.width(slot + padding * 2)
                                .heightIn(max = pickerMaxHeight)
                                .frostedSurface(RoundedCornerShape(Dimens.radiusLg))
                                .padding(padding),
                            verticalArrangement = Arrangement.spacedBy(gap),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            itemsIndexed(displays, key = { _, target -> target.displayId }) { index, target ->
                                DockControl(
                                    slot,
                                    focused = false,
                                    enabled = pickerOpen,
                                    onClick = {
                                        onToolFocusVisibilityChange(false)
                                        onFocusDisplay(target.displayId)
                                        onDismiss()
                                    },
                                    showFocusRing = showToolFocus && index == pickerIndex
                                ) { tint ->
                                    Text(target.number.toString(), color = tint,
                                        style = MaterialTheme.typography.titleLarge)
                                }
                            }
                        }
                        Spacer(Modifier.height(gap))
                    }
                }
            }
            Row(
                modifier = Modifier.width(popupWidth)
                    .height(height).frostedSurface()
                    .padding(horizontal = padding, vertical = gap),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DockControl(
                    slot, false, enabled[0],
                    onClick = { onToolFocusVisibilityChange(false); activate(0) },
                    showFocusRing = showToolFocus && !pickerOpen && toolIndex == 0
                ) { tint ->
                    Icon(painterResource(R.drawable.ic_swap_screens),
                        stringResource(R.string.dual_companion_app_bar_swap_description),
                        tint = tint, modifier = Modifier.size(Dimens.iconMd))
                }
                DockControl(
                    slot, false, enabled[1],
                    onClick = { onToolFocusVisibilityChange(false); activate(1) },
                    showFocusRing = showToolFocus && !pickerOpen && toolIndex == 1
                ) { tint ->
                    Icon(Icons.Default.Keyboard,
                        stringResource(R.string.dual_companion_app_bar_keyboard_description),
                        tint = tint, modifier = Modifier.size(Dimens.iconMd))
                }
                DockControl(
                    slot, false, enabled[2],
                    onClick = { onToolFocusVisibilityChange(false); activate(2) },
                    showFocusRing = showToolFocus && !pickerOpen && toolIndex == 2
                ) { tint ->
                    Icon(Icons.Default.Tv,
                        stringResource(R.string.dual_companion_app_bar_focus_description),
                        tint = tint, modifier = Modifier.size(Dimens.iconMd))
                }
            }
            Spacer(
                Modifier.width(popupWidth)
                    .height(height)
                    .touchOnly(dismiss)
            )
        }
    }
}

@Composable
private fun DockControl(
    slot: Dp,
    focused: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    showFocusRing: Boolean = false,
    content: @Composable (Color) -> Unit
) {
    val tint = if (focused) LocalArgosyTheme.current.focusAccent else MaterialTheme.colorScheme.onSurface
    Box(
        Modifier.size(slot)
            .argosyFocusIndicators(
                focused = showFocusRing && enabled,
                indicators = FocusIndicators.Ring,
                shape = RoundedCornerShape(Dimens.radiusSm)
            )
            .then(if (enabled) Modifier.touchOnly(onClick) else Modifier)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (enabled) semanticClick { onClick(); true } else disabled()
            },
        contentAlignment = Alignment.Center
    ) {
        content(if (enabled) tint else tint.copy(alpha = ComponentDefaults.QuickPanel.disabledContentAlpha))
    }
}

@Composable
internal fun CompanionAppItem(
    packageName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFocused: Boolean = false,
    onSemanticReorder: () -> Boolean,
    reorderActions: List<CustomAccessibilityAction> = emptyList()
) {
    val reorderLabel = stringResource(R.string.dock_reorder_action)
    val context = LocalContext.current
    var label by remember(packageName) { mutableStateOf<String?>(null) }
    LaunchedEffect(packageName) {
        label = withContext(Dispatchers.IO) {
            runCatching {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            }.getOrNull()
        }
    }
    Box(
        modifier = modifier.size(maxOf(minimumTouchTarget, Dimens.iconXl))
            .touchOnly(onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                semanticClick { onClick(); true }
                semanticLongClick(label = reorderLabel) { onSemanticReorder() }
                customActions = reorderActions
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .requiredSize(Dimens.iconXl + Dimens.spacingXs * 2)
                .argosyFocusIndicators(
                    focused = isFocused,
                    indicators = FocusIndicators.Ring,
                    ringColor = MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = AppIconData(packageName),
                contentDescription = label ?: packageName,
                modifier = Modifier
                    .size(Dimens.iconXl)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentScale = ContentScale.Fit
            )
        }
    }
}
