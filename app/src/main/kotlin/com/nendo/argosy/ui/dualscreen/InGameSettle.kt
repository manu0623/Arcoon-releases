package com.nendo.argosy.ui.dualscreen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.nendo.argosy.ui.theme.LocalMotionTier
import com.nendo.argosy.ui.theme.MotionTier
import com.nendo.argosy.ui.util.touchOnly
import kotlinx.coroutines.delay

/** How long into the game the in-game screen stays lit before it settles. */
internal const val IN_GAME_REST_DELAY_MS = 5_000L

/** Saturation and opacity the logo keeps once the screen has settled, the rest of the light gone. */
internal const val REST_LOGO_SATURATION = 0f
internal const val REST_LOGO_ALPHA = 0.28f

private const val REST_FADE_MS = 1_200
private const val WAKE_FADE_MS = 260

/**
 * How long to wait before settling, counted from the session's start rather than from when this
 * screen last drew the game: a screen that appears late, such as after the launcher restarts under
 * a running game, settles at once instead of starting the count again.
 */
internal fun inGameRestDelayMs(
    sessionStartTimeMillis: Long,
    nowMs: Long,
    delayMs: Long = IN_GAME_REST_DELAY_MS
): Long {
    if (sessionStartTimeMillis <= 0L) return delayMs
    return (delayMs - (nowMs - sessionStartTimeMillis)).coerceIn(0L, delayMs)
}

/** What a settled screen keeps of its brightness: 1 at full light, [REST_LOGO_ALPHA] at rest. */
internal fun inGameRestFade(rest: Float): Float =
    1f - (1f - REST_LOGO_ALPHA) * rest.coerceIn(0f, 1f)

/** The grey the logo comes to rest at, or null while the screen is still at full light. */
internal fun inGameRestLogoFilter(rest: Float): ColorFilter? {
    val level = rest.coerceIn(0f, 1f)
    if (level <= 0f) return null
    return ColorFilter.colorMatrix(
        ColorMatrix().apply { setToSaturation(1f - (1f - REST_LOGO_SATURATION) * level) }
    )
}

/**
 * Runs the in-game screen's settle stage: [content] draws at rest 0 for the first few seconds of
 * the session, then at 1 - the art black, the logo a grey ghost - so the screen stops competing
 * with the game being played on the other one.
 *
 * A tap wakes it and starts the count again. A pad driving the game is not movement on this
 * screen, which is why the settle is a timed stage of the session rather than an idle timeout:
 * dimming between presses would flicker. [content] receives the rest level so each surface fades
 * its own layers in its own order.
 */
@Composable
fun InGameSettle(
    gameId: Long,
    sessionStartTimeMillis: Long,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(rest: Float) -> Unit
) {
    val reduced = LocalMotionTier.current == MotionTier.Reduced
    val rest = remember(gameId) { Animatable(0f) }
    var wakes by remember(gameId) { mutableIntStateOf(0) }
    LaunchedEffect(gameId, wakes) {
        if (rest.value > 0f) {
            rest.animateTo(0f, tween(if (reduced) 0 else WAKE_FADE_MS))
        }
        delay(
            if (wakes == 0) {
                inGameRestDelayMs(sessionStartTimeMillis, System.currentTimeMillis())
            } else {
                // A wake starts its own clock: the full delay again, not the session's remainder.
                IN_GAME_REST_DELAY_MS
            }
        )
        rest.animateTo(1f, tween(if (reduced) 0 else REST_FADE_MS))
    }
    Box(modifier = modifier.fillMaxSize().touchOnly { wakes += 1 }) {
        content(rest.value)
    }
}
