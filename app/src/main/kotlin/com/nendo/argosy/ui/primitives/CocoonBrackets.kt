package com.nendo.argosy.ui.primitives
import androidx.compose.runtime.collectAsState

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.LocalMotionTier
import com.nendo.argosy.ui.theme.MotionTier

/** Cocoon's selection cursor: four rounded corner brackets with a soft glow, the item itself untouched. */
object CocoonBracketSpec {
    val color: Color get() = com.nendo.argosy.ui.cocoon.theme.CocoonThemeEngine.colors?.cursor ?: Color(0xFFD9D9DC)
    val stroke: Dp = 4.dp
    val cornerRadius: Dp = 18.dp
    val minArm: Dp = 12.dp
    val maxArm: Dp = 44.dp
    const val armFraction = 0.31f
    const val glowAlpha = 0.55f
    /** How far the brackets travel inward while they appear ("Corners (Animated)"). */
    val settle: Dp = 6.dp
    /** How far the brackets drift outward at the top of each breath, and the half-cycle length. */
    val breath: Dp = 2.dp
    const val breathMs = 900
}

/**
 * Draws the brackets on top of the element, [outset] outside its bounds. They spring in from
 * slightly further out as focus arrives and fade out as it leaves.
 */
fun Modifier.cocoonBrackets(
    focused: Boolean,
    outset: Dp = 4.dp,
    color: Color = CocoonBracketSpec.color,
): Modifier = composed {
    // While a quick panel window is up the pad drives that window, so the ring here would
    // point at nothing: the launcher draws no highlight at all until the panel closes.
    val panelOpen = com.nendo.argosy.ui.quickpanel.QuickPanelPresence.open.collectAsState()
    // Only the surface underneath the panel goes dark: the panel window still needs its ring,
    // because the pad is driving that menu and the ring is where the focus is.
    val inPanel = com.nendo.argosy.ui.quickpanel.LocalQuickPanelSurface.current
    val lit = focused && !(panelOpen.value && !inPanel) &&
        com.nendo.argosy.ui.cocoon.cocoonPopulateLetsCursorShow()
    val cursor = LocalCocoonCursor.current
    if (cursor != null && cursor.view === androidx.compose.ui.platform.LocalView.current) {
        return@composed cocoonCursorTarget(cursor, lit, outset)
    }
    val reduced = LocalMotionTier.current == MotionTier.Reduced
    val progress = animateFloatAsState(
        targetValue = if (lit) 1f else 0f,
        animationSpec = if (reduced) spring(stiffness = 4000f) else spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "cocoon-brackets",
    )
    // "Corners (Animated)": while focused the brackets breathe gently in and out. The animated
    // values are read only while drawing, so the breathing never recomposes the element.
    val breath: androidx.compose.runtime.State<Float>? = if (lit && !reduced) {
        val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "cocoon-breath")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(
                    CocoonBracketSpec.breathMs,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                ),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "cocoon-breath-value"
        )
    } else null
    drawWithContent {
        drawContent()
        val p = progress.value
        if (p > 0.01f) {
            val settle = CocoonBracketSpec.settle.toPx() * (1f - p.coerceIn(0f, 1f))
            val b = breath?.value ?: 0f
            drawCocoonBrackets(
                alpha = p.coerceIn(0f, 1f),
                color = color,
                outsetPx = outset.toPx() + settle + b * CocoonBracketSpec.breath.toPx(),
            )
        }
    }
}

fun DrawScope.drawCocoonBrackets(
    alpha: Float,
    color: Color = CocoonBracketSpec.color,
    outsetPx: Float = 4.dp.toPx(),
) {
    drawCocoonBracketsAt(-outsetPx, -outsetPx, size.width + outsetPx, size.height + outsetPx, alpha, color)
}

/** The brackets around an explicit rectangle (used by the sliding cursor). */
fun DrawScope.drawCocoonBracketsAt(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    alpha: Float,
    color: Color = CocoonBracketSpec.color,
) {
    val themeSettings = com.nendo.argosy.ui.cocoon.theme.CocoonThemeEngine.settings
    val stroke = CocoonBracketSpec.stroke.toPx() * themeSettings.highlightThickness
    if (themeSettings.cursorOutlined) {
        // "Outlined" cursor styles: one rounded outline all the way round.
        val radius = CocoonBracketSpec.cornerRadius.toPx()
        val outline = Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(left, top, right, bottom, androidx.compose.ui.geometry.CornerRadius(radius)))
        }
        val glowOutline = CocoonBracketSpec.glowAlpha * alpha
        drawPath(outline, color.copy(alpha = glowOutline * 0.35f), style = Stroke(width = stroke * 3f))
        drawPath(outline, color.copy(alpha = alpha), style = Stroke(width = stroke))
        return
    }
    val shortSide = minOf(right - left, bottom - top)
    val arm = (shortSide * CocoonBracketSpec.armFraction)
        .coerceIn(CocoonBracketSpec.minArm.toPx(), CocoonBracketSpec.maxArm.toPx())
        .coerceAtMost(shortSide / 2f)
    val r = CocoonBracketSpec.cornerRadius.toPx().coerceAtMost(arm * 0.8f)

    val path = Path().apply {
        // top-left
        moveTo(left, top + arm)
        lineTo(left, top + r)
        arcTo(Rect(left, top, left + 2 * r, top + 2 * r), 180f, 90f, false)
        lineTo(left + arm, top)
        // top-right
        moveTo(right - arm, top)
        lineTo(right - r, top)
        arcTo(Rect(right - 2 * r, top, right, top + 2 * r), 270f, 90f, false)
        lineTo(right, top + arm)
        // bottom-right
        moveTo(right, bottom - arm)
        lineTo(right, bottom - r)
        arcTo(Rect(right - 2 * r, bottom - 2 * r, right, bottom), 0f, 90f, false)
        lineTo(right - arm, bottom)
        // bottom-left
        moveTo(left + arm, bottom)
        lineTo(left + r, bottom)
        arcTo(Rect(left, bottom - 2 * r, left + 2 * r, bottom), 90f, 90f, false)
        lineTo(left, bottom - arm)
    }
    val glow = CocoonBracketSpec.glowAlpha * alpha
    drawPath(path, color.copy(alpha = glow * 0.35f), style = Stroke(width = stroke * 3.4f, cap = StrokeCap.Round))
    drawPath(path, color.copy(alpha = glow * 0.6f), style = Stroke(width = stroke * 2f, cap = StrokeCap.Round))
    drawPath(path, color.copy(alpha = alpha), style = Stroke(width = stroke, cap = StrokeCap.Round))
}


/**
 * Cocoon's selection cursor is one set of brackets that glides from item to item rather than
 * fading out on one and in on the next. A [CocoonCursorHost] draws it; every [cocoonBrackets]
 * inside the host just reports where it is, and the focused one becomes the cursor's target.
 */
class CocoonCursor {
    /** The window the host draws in; items in other windows (dialogs) draw their own brackets. */
    internal var view: android.view.View? = null
    internal val rects = androidx.compose.runtime.mutableStateMapOf<Any, Rect>()
    internal val outsets = HashMap<Any, Float>()
    internal val coords = HashMap<Any, androidx.compose.ui.layout.LayoutCoordinates>()
    internal var host: androidx.compose.ui.layout.LayoutCoordinates? = null
    internal val order = androidx.compose.runtime.mutableStateListOf<Any>()
    /** Full-screen pages open on top of this host's items: its cursor stays hidden meanwhile. */
    internal val covered = androidx.compose.runtime.mutableIntStateOf(0)

    /** How the cursor enters the next item it lands on, instead of gliding there. */
    enum class Entry {
        /** Appears on the item, fading in. */
        FRESH,
        /** Starts as a narrow shape at the item's left edge and stretches across it. */
        GROW_FROM_LEFT
    }

    internal var pendingEntry: Entry? = null
    internal var entryAtNanos = 0L
    internal var entryFades = false

    /** The next item the cursor lands on (a newly opened menu's) is entered as [entry]. */
    fun requestEntry(entry: Entry) {
        pendingEntry = entry
    }

    fun clearEntry() {
        pendingEntry = null
    }

    /** The most recently focused target still focused, with its outset. */
    internal fun active(): Pair<Rect, Float>? {
        val key = order.lastOrNull() ?: return null
        rects[key] ?: return null
        val local = localRect(key) ?: return null
        return local to (outsets[key] ?: 0f)
    }

    /**
     * The item's bounds in the host's own space. Measured through the layers in between, so a
     * board that zooms or fades (opening a folder) carries its cursor with it exactly; measuring
     * in screen space and drawing inside the zooming board applied the zoom twice (the drift).
     */
    private fun localRect(key: Any): Rect? {
        val h = host ?: return null
        val c = coords[key] ?: return null
        if (!h.isAttached || !c.isAttached) return null
        return runCatching { h.localBoundingBoxOf(c, clipBounds = false) }.getOrNull()
    }
}

val LocalCocoonCursor = androidx.compose.runtime.staticCompositionLocalOf<CocoonCursor?> { null }

private class TargetBounds {
    var rect: Rect? = null
}

private fun Modifier.cocoonCursorTarget(cursor: CocoonCursor, focused: Boolean, outset: Dp): Modifier = composed {
    val key = androidx.compose.runtime.remember { Any() }
    val bounds = androidx.compose.runtime.remember { TargetBounds() }
    val outsetPx = with(androidx.compose.ui.platform.LocalDensity.current) { outset.toPx() }
    androidx.compose.runtime.DisposableEffect(focused) {
        if (focused) {
            cursor.outsets[key] = outsetPx
            bounds.rect?.let { cursor.rects[key] = it }
            cursor.order.remove(key)
            cursor.order.add(key)
        } else {
            cursor.order.remove(key)
        }
        onDispose { }
    }
    /*
     * The item's place is forgotten only when the item itself goes away (another screen). Losing
     * focus keeps it: the cursor glides from there. Forgetting it on every focus change made the
     * cursor think the screen had changed, so it appeared on the next item instead of travelling.
     */
    androidx.compose.runtime.DisposableEffect(cursor, key) {
        onDispose {
            cursor.order.remove(key)
            cursor.rects.remove(key)
            cursor.outsets.remove(key)
            cursor.coords.remove(key)
        }
    }
    this.onGloballyPositioned { coords ->
        // unclipped bounds, so an item half under the edge still gets full-size brackets
        val pos = coords.positionInRoot()
        val rect = Rect(pos.x, pos.y, pos.x + coords.size.width, pos.y + coords.size.height)
        bounds.rect = rect
        cursor.coords[key] = coords
        if (cursor.rects[key] != rect && (focused || key in cursor.rects)) cursor.rects[key] = rect
    }
}

/**
 * Hosts one sliding cursor for every [cocoonBrackets] inside [content].
 *
 * [covers]: this host belongs to a full-screen page drawn over another page (Personalization,
 * Themes…). The page underneath keeps its own row focused, and its cursor is drawn by an outer
 * host above everything, so it would show through as a second, mismatched cursor. While a
 * covering host is up, the host it covers draws nothing.
 */
@androidx.compose.runtime.Composable
fun CocoonCursorHost(
    modifier: Modifier = Modifier,
    covers: Boolean = false,
    content: @androidx.compose.runtime.Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val cursor = androidx.compose.runtime.remember { CocoonCursor() }
    val view = androidx.compose.ui.platform.LocalView.current
    cursor.view = view
    val parent = LocalCocoonCursor.current
    if (covers && parent != null && parent !== cursor && parent.view === view) {
        androidx.compose.runtime.DisposableEffect(parent) {
            parent.covered.intValue++
            onDispose { parent.covered.intValue-- }
        }
    }
    androidx.compose.foundation.layout.Box(
        modifier = modifier.onGloballyPositioned { cursor.host = it }
    ) {
        val scope = this
        androidx.compose.runtime.CompositionLocalProvider(LocalCocoonCursor provides cursor) {
            scope.content()
        }
        // Positions are read while drawing, in the same frame the items are laid out, so a
        // scrolling board never leaves the cursor a frame behind (that lag felt like drag).
        val follower = androidx.compose.runtime.remember { CursorFollower() }
        var tick by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
        val hasTarget = cursor.order.isNotEmpty()
        androidx.compose.runtime.LaunchedEffect(Unit) {
            var last = 0L
            var gliding = false
            while (true) {
                androidx.compose.runtime.withFrameNanos { now ->
                    val dt = if (!gliding || last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0f, 0.034f)
                    last = now
                    gliding = follower.decay(dt)
                    val fading = cursor.entryFades && now - cursor.entryAtNanos < ENTRY_FADE_NANOS
                    if (gliding || fading) tick++
                }
            }
        }
        val alpha by animateFloatAsState(if (hasTarget) 1f else 0f, label = "cocoon-cursor-alpha")
        val glides = !com.nendo.argosy.ui.quickpanel.LocalQuickPanelSurface.current
        // Cocoon's "Corners (Animated)": the brackets breathe gently in and out.
        val breath by androidx.compose.animation.core.rememberInfiniteTransition(label = "cocoon-cursor-breath").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.tween(
                    CocoonBracketSpec.breathMs,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                ),
                androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "cocoon-cursor-breath-value"
        )
        androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
            tick
            val active = cursor.active()
            val goal = active?.let { (r, o) ->
                Rect(r.left - o, r.top - o, r.right + o, r.bottom + o)
            }
            // The item the cursor was on is gone (another screen opened): appear on the new item
            // instead of gliding over from where the old screen had it.
            val activeKey = cursor.order.lastOrNull()
            if (follower.drawnKey != null && follower.drawnKey != activeKey && follower.drawnKey !in cursor.rects) {
                follower.reset()
            }
            if (cursor.covered.intValue > 0) {
                // A page is open over this one: no cursor here, and when it closes the cursor
                // appears straight on its item instead of gliding in from where it was.
                follower.reset()
                return@Canvas
            }
            // A newly opened menu asked to be entered fresh, not glided into from the board.
            val entry = cursor.pendingEntry
            if (entry != null && activeKey != null && activeKey != follower.drawnKey) {
                cursor.pendingEntry = null
                follower.reset()
                follower.growNext = entry == CocoonCursor.Entry.GROW_FROM_LEFT
                cursor.entryFades = entry == CocoonCursor.Entry.FRESH
                cursor.entryAtNanos = System.nanoTime()
            }
            val entryFade = if (cursor.entryFades) {
                ((System.nanoTime() - cursor.entryAtNanos).toFloat() / ENTRY_FADE_NANOS).coerceIn(0f, 1f)
            } else 1f
            val r = if (glides || follower.growNext) goal?.let { follower.place(it, activeKey) } else goal
            if (alpha * entryFade > 0.01f && r != null) {
                val b = if (com.nendo.argosy.ui.cocoon.theme.CocoonThemeEngine.settings.cursorAnimated) {
                    breath * CocoonBracketSpec.breath.toPx()
                } else 0f
                drawCocoonBracketsAt(r.left - b, r.top - b, r.right + b, r.bottom + b, alpha * entryFade)
            }
        }
    }
}


/**
 * One slide from item to item: 260 ms, starting at once and settling softly, with no creeping
 * tail. The first frame of a slide used to count all the idle time before it, so the cursor
 * jumped most of the way and only the end looked animated.
 */
private const val GLIDE_SECONDS = 0.26f
/** A cursor entering a newly opened menu fresh fades in over this long. */
private const val ENTRY_FADE_NANOS = 200_000_000L
private val GLIDE_EASE = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** Moves the drawn cursor toward its goal: eased glide to a new item, then locked onto it. */
private class CursorFollower {
    private var drawn: Rect? = null
    private var key: Any? = null
    // How far the cursor still sits from its item, measured from the item itself. A scrolling
    // board moves the cursor exactly with the item; only this gap shrinks.
    private var gapL = 0f
    private var gapT = 0f
    private var gapR = 0f
    private var gapB = 0f
    private var startL = 0f
    private var startT = 0f
    private var startR = 0f
    private var startB = 0f
    private var elapsed = 1f

    val drawnKey: Any? get() = key

    /** The next item is entered growing from its left edge (Cocoon's Y menu). */
    var growNext = false

    /** Forget the drawn position, so the next item is shown in place (no glide). */
    fun reset() {
        drawn = null
        key = null
    }

    /** Called while drawing: where the cursor goes this frame. */
    fun place(goal: Rect, goalKey: Any?): Rect {
        val current = drawn
        if (goalKey != key) {
            key = goalKey
            if (current == null && growNext) {
                // Starts as a square at the item's left edge; only the right side travels.
                growNext = false
                startL = 0f; startT = 0f; startB = 0f
                startR = (goal.left + goal.height) - goal.right
                gapL = 0f; gapT = 0f; gapB = 0f; gapR = startR
                elapsed = 0f
            } else if (current != null) {
                startL = current.left - goal.left
                startT = current.top - goal.top
                startR = current.right - goal.right
                startB = current.bottom - goal.bottom
                gapL = startL; gapT = startT; gapR = startR; gapB = startB
                elapsed = 0f
            }
        }
        val next = Rect(goal.left + gapL, goal.top + gapT, goal.right + gapR, goal.bottom + gapB)
        drawn = next
        return next
    }

    /** Called once a frame; true while the cursor is still gliding (so it redraws). */
    fun decay(dt: Float): Boolean {
        if (elapsed >= 1f) return false
        elapsed = (elapsed + dt / GLIDE_SECONDS).coerceAtMost(1f)
        val remaining = 1f - GLIDE_EASE.transform(elapsed)
        gapL = startL * remaining; gapT = startT * remaining
        gapR = startR * remaining; gapB = startB * remaining
        return true
    }
}
