package com.nendo.argosy.ui.cocoon

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.input.HapticFeedbackManager
import com.nendo.argosy.ui.input.HapticPattern
import com.nendo.argosy.ui.primitives.CocoonCursor
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Cocoon's "populating" entrance for a menu: its items arrive one after another, top to bottom,
 * with a soft haptic tick as each one starts, the way a folder's covers arrive.
 *
 * A screen opens one with [rememberCocoonPopulate] and provides it through [LocalCocoonPopulate];
 * each row joins with [cocoonPopulate], in the order the rows are first drawn. Rows drawn after
 * the entrance has finished (scrolled in later, another section) simply appear.
 */
class CocoonPopulate internal constructor(val style: Style, internal val gatesCursor: Boolean) {
    enum class Style {
        /** A folder's covers: each fades in while growing from 84% and rising into place. */
        FOLDER,
        /** A drop-down menu (drawers, the Y menu): each row's content fades in where it sits. */
        DROP
    }

    /** How the cursor brackets enter once the menu is up. */
    enum class CursorEntry {
        /** Brackets stay hidden while the rows populate, then fade in on the focused row. */
        AFTER_ROWS,
        /** Brackets start as a narrow shape at the left of the focused row and stretch across it. */
        GROW_FROM_LEFT
    }

    private val clock = Animatable(0f)
    private var slots = 0

    /** True once every row has arrived. */
    var settled by mutableStateOf(false)
        private set

    internal fun claim(): Int = if (settled) -1 else slots++

    internal fun progress(slot: Int): Float {
        if (slot < 0 || settled) return 1f
        val t = clock.value - slot * step
        return FastOutSlowInEasing.transform((t / inMs).coerceIn(0f, 1f))
    }

    private val step: Float get() = if (style == Style.FOLDER) FOLDER_STEP_MS else DROP_STEP_MS
    private val inMs: Float get() = if (style == Style.FOLDER) FOLDER_IN_MS else DROP_IN_MS

    internal suspend fun play(haptics: HapticFeedbackManager?, reduced: Boolean, startDelayMs: Long = 0L) {
        if (startDelayMs > 0) delay(startDelayMs)
        // Let the rows draw (and take their places in line) before the clock starts. A screen
        // that loads its rows a moment later (Settings) is waited for, up to about a second.
        withFrameNanos { }
        var waited = 0
        while (slots == 0 && waited < MAX_WAIT_FRAMES) {
            withFrameNanos { }
            waited++
        }
        withFrameNanos { }
        val count = slots
        com.nendo.argosy.util.ArcoonTrace.log(
            "populate $style: rows=$count reduced=$reduced haptics=${haptics != null}"
        )
        if (reduced || count == 0) {
            settled = true
            return
        }
        coroutineScope {
            launch {
                val total = (count - 1) * step + inMs
                clock.animateTo(total, tween(total.toInt(), easing = LinearEasing))
                settled = true
                com.nendo.argosy.util.ArcoonTrace.log("populate $style: done after ${total.toInt()} ms")
            }
            if (haptics != null) {
                var elapsed = 0L
                for (index in 0 until count.coerceAtMost(MAX_TICKS)) {
                    val start = (index * step).toLong()
                    if (start > elapsed) {
                        delay(start - elapsed)
                        elapsed = start
                    }
                    haptics.vibrate(HapticPattern.FOCUS_CHANGE)
                }
            }
        }
    }

    companion object {
        // Same beat as a folder's covers (CocoonCoverWave): 34 ms apart, 240 ms each.
        private const val FOLDER_STEP_MS = 34f
        private const val FOLDER_IN_MS = 240f
        // Cocoon's drop-down: a row about every frame-pair, each fading in quickly.
        private const val DROP_STEP_MS = 33f
        private const val DROP_IN_MS = 110f
        /** A folder ticks once per column, eight at most; a long list does not buzz on and on. */
        private const val MAX_TICKS = 8
        private const val MAX_WAIT_FRAMES = 60
    }
}

val LocalCocoonPopulate = staticCompositionLocalOf<CocoonPopulate?> { null }

/**
 * Starts a populating entrance, played once per [key]. Provide the result through
 * [LocalCocoonPopulate] around the menu's rows.
 */
@Composable
fun rememberCocoonPopulate(
    style: CocoonPopulate.Style,
    cursorEntry: CocoonPopulate.CursorEntry = CocoonPopulate.CursorEntry.AFTER_ROWS,
    key: Any? = Unit,
    startDelayMs: Long = 0L
): CocoonPopulate {
    val haptics = com.nendo.argosy.ui.input.LocalHaptics.current
    val reduced = com.nendo.argosy.ui.theme.LocalMotionTier.current == com.nendo.argosy.ui.theme.MotionTier.Reduced
    val cursor: CocoonCursor? = com.nendo.argosy.ui.primitives.LocalCocoonCursor.current
    val wave = remember(key) {
        // Asked for while composing, so it is in place before the menu's rows take the cursor.
        cursor?.requestEntry(
            when (cursorEntry) {
                CocoonPopulate.CursorEntry.GROW_FROM_LEFT -> CocoonCursor.Entry.GROW_FROM_LEFT
                CocoonPopulate.CursorEntry.AFTER_ROWS -> CocoonCursor.Entry.FRESH
            }
        )
        CocoonPopulate(style, gatesCursor = cursorEntry == CocoonPopulate.CursorEntry.AFTER_ROWS)
    }
    LaunchedEffect(wave) { wave.play(haptics, reduced, startDelayMs) }
    androidx.compose.runtime.DisposableEffect(wave) {
        // A menu that closed before any row took the cursor leaves no entry behind for the board.
        onDispose { cursor?.clearEntry() }
    }
    return wave
}

/** Whether the cursor brackets may show yet: not while an AFTER_ROWS entrance is populating. */
@Composable
fun cocoonPopulateLetsCursorShow(): Boolean =
    LocalCocoonPopulate.current?.let { !it.gatesCursor || it.settled } ?: true

/**
 * Joins this row to the open [CocoonPopulate] entrance, if any. [focused] rows of a FOLDER
 * entrance only fade, so the cursor measures them at their final size.
 */
fun Modifier.cocoonPopulate(focused: Boolean = false): Modifier = composed {
    val wave = LocalCocoonPopulate.current ?: return@composed Modifier
    val slot = remember(wave) { wave.claim() }
    if (slot < 0) return@composed Modifier
    Modifier.graphicsLayer {
        val p = wave.progress(slot)
        alpha = p
        when (wave.style) {
            CocoonPopulate.Style.FOLDER -> if (!focused) {
                val scale = 0.84f + 0.16f * p
                scaleX = scale
                scaleY = scale
                translationY = (1f - p) * 18.dp.toPx()
            }
            CocoonPopulate.Style.DROP -> translationY = -(1f - p) * 6.dp.toPx()
        }
    }
}

/** Plays a populating entrance over [content]'s rows, once per [key]. */
@Composable
fun CocoonPopulating(
    style: CocoonPopulate.Style,
    cursorEntry: CocoonPopulate.CursorEntry = CocoonPopulate.CursorEntry.AFTER_ROWS,
    key: Any? = Unit,
    startDelayMs: Long = 0L,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        content()
        return
    }
    val wave = rememberCocoonPopulate(style, cursorEntry, key, startDelayMs)
    androidx.compose.runtime.CompositionLocalProvider(LocalCocoonPopulate provides wave) { content() }
}
