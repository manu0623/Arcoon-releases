package com.nendo.argosy.ui.cocoon

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import com.nendo.argosy.ui.theme.LocalMotionTier
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.MotionTier

/** When the screens last swapped, for windows that are rebuilt by the swap. */
object CocoonSwap {
    @Volatile var lastSwapAt = 0L
    /** One duration for both windows: the two pictures meet in the middle only if they share it. */
    const val SLIDE_MS = 360L
}

/** True for a moment after X / the quick toggle swapped the screens. */
fun cocoonSwapRecent(windowMs: Long = 1500): Boolean = System.currentTimeMillis() - CocoonSwap.lastSwapAt < windowMs

/**
 * Screen swap motion. Both windows run the same duration from the same easing, so the two
 * pictures travel toward each other and reach their places together: the top panel's leaves
 * downward while the bottom panel's arrives from below, and the bottom panel's leaves upward
 * while the top panel's arrives from above.
 *
 * [content] is handed the key its copy stands for, so the copy leaving keeps the picture it held
 * instead of recomposing to the arriving one while it is still on screen.
 */
@Composable
fun CocoonSwapSlide(
    key: Any?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(Any?) -> Unit,
) {
    val displayId = LocalView.current.display?.displayId
    // The panel's physical position, not its role: roles swap, the screens do not.
    val isTop = displayId == null || displayId == android.view.Display.DEFAULT_DISPLAY
    val reduced = LocalMotionTier.current == MotionTier.Reduced
    val slideMs = CocoonSwap.SLIDE_MS.toInt()
    val slide = if (reduced) {
        snap<IntOffset>(delayMillis = 0)
    } else {
        tween<IntOffset>(durationMillis = slideMs, easing = Motion.argosyEase)
    }
    /*
     * One line per composed instance, which is what tells a genuine second composition apart from
     * Compose asking transitionSpec for its spec more than once per transition. Both used to look
     * like "two slides started" in the trace.
     */
    LaunchedEffect(Unit) {
        com.nendo.argosy.util.ArcoonTrace.log("swap slide instance display=$displayId isTop=$isTop startKey=$key")
    }
    AnimatedContent(
        targetState = key,
        modifier = modifier.fillMaxSize().clipToBounds(),
        transitionSpec = {
            val away = if (isTop) 1 else -1
            /*
             * Logged with the target because AnimatedContent asks for a spec again whenever the
             * content it is animating changes, so several "start" lines for one press are several
             * specs for one transition rather than several touches.
             */
            com.nendo.argosy.util.ArcoonTrace.log(
                "swap slide start display=$displayId isTop=$isTop key=$key target=$targetState " +
                    "away=$away ms=$slideMs"
            )
            slideInVertically(animationSpec = slide, initialOffsetY = { away * it })
                .togetherWith(slideOutVertically(animationSpec = slide, targetOffsetY = { away * it }))
        },
        label = "cocoon-screen-swap"
    ) { frameKey ->
        // One id per composed copy: a copy that is rebuilt takes a new id, so the log shows how
        // many pictures actually existed during one slide and how long each one lived.
        val copyId = androidx.compose.runtime.remember {
            com.nendo.argosy.util.RebuildTrace.id("swapcopy")
        }
        androidx.compose.runtime.LaunchedEffect(Unit) {
            com.nendo.argosy.util.ArcoonTrace.log(
                "swap copy born id=$copyId display=$displayId frameKey=$frameKey"
            )
        }
        androidx.compose.runtime.DisposableEffect(Unit) {
            onDispose {
                com.nendo.argosy.util.ArcoonTrace.log(
                    "swap copy left id=$copyId display=$displayId frameKey=$frameKey"
                )
            }
        }
        Box(modifier = Modifier.fillMaxSize()) { content(frameKey) }
    }
}
