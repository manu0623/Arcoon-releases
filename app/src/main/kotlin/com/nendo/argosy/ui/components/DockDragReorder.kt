package com.nendo.argosy.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.zIndex
import com.nendo.argosy.R
import com.nendo.argosy.ui.theme.Motion
import kotlinx.coroutines.CoroutineScope

@Stable
internal class DockDragReorderState(
    private val listState: LazyListState,
    scope: CoroutineScope,
    private val edge: Float,
    private val isRtl: Boolean,
    private val onStart: (String) -> Long?,
    private val onTarget: (Long, Int) -> Unit,
    private val onSave: (Long) -> Unit,
    private val onCancel: (Long) -> Unit,
    private val onHold: (String) -> Unit
) {
    var draggingPackage by mutableStateOf<String?>(null)
        private set
    private var moved = false
    private var heldPackage: String? = null
    val isHolding: Boolean
        get() = heldPackage != null
    var accessiblePackage by mutableStateOf<String?>(null)
        private set
    private var center by mutableFloatStateOf(0f)
    private var token: Long? = null
    private var target = 0
    private var lastCenter = 0f
    private val autoscroller = EdgeAutoscroller(listState, scope) { resolveTarget(it) }

    fun start(position: Offset) {
        if (token != null) return
        val layout = listState.layoutInfo
        val x = if (isRtl) layout.viewportStartOffset + layout.viewportEndOffset - position.x else position.x
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            x >= it.offset && x < it.offset + it.size
        } ?: return
        val packageName = item.key as? String ?: return
        heldPackage = packageName
        moved = false
        val started = onStart(packageName) ?: return
        token = started
        draggingPackage = packageName
        target = item.index
        center = item.offset + item.size / 2f
        lastCenter = center
    }

    fun startAccessible(packageName: String): Boolean {
        if (token != null) return false
        token = onStart(packageName) ?: return false
        accessiblePackage = packageName
        return true
    }

    fun drag(delta: Float) {
        if (token == null) return
        center += if (isRtl) -delta else delta
        resolveTarget()
        updateScroll()
    }

    fun reconcile(session: DockReorderSession?) {
        if (token != null && session?.token != token) finish(false)
    }

    fun release(save: Boolean) {
        val held = heldPackage
        heldPackage = null
        if (held != null && !moved) {
            finish(false)
            onHold(held)
        } else {
            finish(save)
        }
    }

    fun finish(save: Boolean) {
        autoscroller.stop()
        heldPackage = null
        val finished = token ?: return
        token = null
        draggingPackage = null
        accessiblePackage = null
        if (save) onSave(finished) else onCancel(finished)
    }

    fun translation(packageName: String): Float {
        if (packageName != draggingPackage) return 0f
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == packageName } ?: return 0f
        val offset = center - item.offset - item.size / 2f
        return if (isRtl) -offset else offset
    }

    private fun resolveTarget(scrolled: Float = 0f) {
        val active = token ?: return
        val centers = listState.layoutInfo.visibleItemsInfo.map { it.index to (it.offset + it.size / 2f) }
        val next = dockDragTarget(target, lastCenter - scrolled, center, centers)
        if (next != target) {
            target = next
            moved = true
            listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            onTarget(active, next)
        }
        lastCenter = center
    }

    private fun updateScroll() {
        autoscroller.update(
            edgeScrollSpeed(listState.layoutInfo, center, center, edge).coerceIn(-MAX_SCROLL_STEP, MAX_SCROLL_STEP)
        )
    }
}

@Composable
internal fun DockReorderEffects(
    listState: LazyListState,
    drag: DockDragReorderState,
    apps: List<String>,
    reorder: DockReorderSession?
) {
    LaunchedEffect(reorder?.token) { drag.reconcile(reorder) }
    var previousApps by remember { mutableStateOf(apps) }
    var wasEditing by remember { mutableStateOf(reorder != null) }
    SideEffect {
        if (previousApps != apps && (reorder != null || wasEditing)) {
            listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }
        previousApps = apps
        wasEditing = reorder != null
    }
    LaunchedEffect(reorder?.targetIndex, drag.accessiblePackage) {
        if (drag.accessiblePackage != null && reorder != null) {
            val layout = listState.layoutInfo
            val item = layout.visibleItemsInfo.firstOrNull { it.index == reorder.targetIndex }
            if (item == null || item.offset < layout.viewportStartOffset || item.offset + item.size > layout.viewportEndOffset) {
                listState.animateScrollToItem(reorder.targetIndex)
            }
        }
    }
}

@Composable
internal fun dockReorderActions(
    listState: LazyListState,
    reorder: DockReorderSession?,
    onTarget: (Long, Int) -> Unit,
    onSave: (Long) -> Unit,
    onCancel: (Long) -> Unit
): List<CustomAccessibilityAction> {
    val leftLabel = stringResource(R.string.dock_reorder_move_left)
    val rightLabel = stringResource(R.string.dock_reorder_move_right)
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
    val saveLabel = stringResource(R.string.reorder_hint_done)
    val cancelLabel = stringResource(R.string.dock_hint_cancel)
    return if (reorder != null && !reorder.saving) {
        buildList {
            for ((label, delta) in listOf(leftLabel to -direction, rightLabel to direction)) {
                val target = reorder.targetIndex + delta
                if (target in reorder.visiblePackages.indices) add(CustomAccessibilityAction(label) {
                    listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                    onTarget(reorder.token, target)
                    true
                })
            }
            add(CustomAccessibilityAction(saveLabel) { onSave(reorder.token); true })
            add(CustomAccessibilityAction(cancelLabel) { onCancel(reorder.token); true })
        }
    } else emptyList()
}

@Composable
internal fun rememberDockDragReorderState(
    listState: LazyListState,
    edge: Float,
    onStart: (String) -> Long?,
    onTarget: (Long, Int) -> Unit,
    onSave: (Long) -> Unit,
    onCancel: (Long) -> Unit,
    onHold: (String) -> Unit
): DockDragReorderState {
    val scope = rememberCoroutineScope()
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val start by rememberUpdatedState(onStart)
    val target by rememberUpdatedState(onTarget)
    val save by rememberUpdatedState(onSave)
    val cancel by rememberUpdatedState(onCancel)
    val hold by rememberUpdatedState(onHold)
    val state = remember(listState, scope, edge, isRtl) {
        DockDragReorderState(listState, scope, edge, isRtl, { start(it) }, { token, index -> target(token, index) },
            { save(it) }, { cancel(it) }, { hold(it) })
    }
    DisposableEffect(state) { onDispose { state.finish(false) } }
    return state
}

internal fun Modifier.dockDragContainer(state: DockDragReorderState): Modifier = pointerInput(state) {
    detectDragGesturesAfterLongPress(
        onDragStart = state::start,
        onDrag = { change, amount ->
            if (state.draggingPackage != null) {
                change.consume()
                state.drag(amount.x)
            }
        },
        onDragEnd = { if (state.isHolding) state.release(save = true) },
        onDragCancel = { if (state.isHolding) state.release(save = false) }
    )
}

@Composable
internal fun Modifier.dockDraggedItem(
    state: DockDragReorderState,
    packageName: String,
    lifted: Boolean
): Modifier {
    val dragging = state.draggingPackage == packageName
    val translation by animateFloatAsState(
        targetValue = state.translation(packageName),
        animationSpec = if (dragging) snap() else tween(Motion.durationSlide, easing = Motion.argosyEase),
        label = "dock-drop"
    )
    return zIndex(if (lifted || dragging || translation != 0f) 1f else 0f)
        .graphicsLayer { translationX = if (dragging) state.translation(packageName) else translation }
}
