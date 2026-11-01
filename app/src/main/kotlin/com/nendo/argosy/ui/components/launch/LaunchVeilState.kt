package com.nendo.argosy.ui.components.launch

import com.nendo.argosy.domain.model.LaunchPhase
import com.nendo.argosy.domain.model.LaunchProgress
import com.nendo.argosy.domain.model.phase
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Whether a game is on its way in: the launch has published LAUNCHING and its window has been or
 * is about to be requested.
 */
fun veilFor(progress: LaunchProgress?): Boolean =
    progress?.step?.phase == LaunchPhase.LAUNCHING

/**
 * Whether the presentation surface on [displayId] goes black: only the display the launch placed
 * the game on, [gameDisplayId], and only while [veilFor] holds. A running session alone never
 * holds it.
 */
fun veilsDisplay(
    progress: LaunchProgress?,
    gameDisplayId: Int?,
    displayId: Int?
): Boolean = gameDisplayId != null && gameDisplayId == displayId && veilFor(progress)

/**
 * Whether the game opens on the control surface on [surfaceDisplayId]: always when there is no
 * presentation surface, and with one only when [gameDisplayId] is this display.
 */
fun opensOnSurface(hasPresentationSurface: Boolean, gameDisplayId: Int?, surfaceDisplayId: Int?): Boolean =
    !hasPresentationSurface || (gameDisplayId != null && gameDisplayId == surfaceDisplayId)

/**
 * Whether a launch overlay fades to black as the game opens: only once the launch is LAUNCHING,
 * and only on the surface the game opens on, [gameOpensHere].
 */
fun darkensForLaunch(progress: LaunchProgress?, gameOpensHere: Boolean): Boolean =
    gameOpensHere && veilFor(progress)

/**
 * The veil's opacity when it first draws: where a fade over [veilMs] begun [elapsedMs] ago would
 * stand. A veil that is not [veiled], whose start is unknown, or under [reducedMotion], starts at
 * its target.
 */
fun veilStartAlpha(veiled: Boolean, elapsedMs: Long?, veilMs: Int, reducedMotion: Boolean): Float {
    val target = if (veiled) 1f else 0f
    if (!veiled || reducedMotion || elapsedMs == null || veilMs <= 0) return target
    return (elapsedMs.toFloat() / veilMs).coerceIn(0f, 1f)
}

/**
 * How long the veil takes from [from] to [to] at the even rate of a full fade over [veilMs], zero
 * under [reducedMotion].
 */
fun veilRemainingMs(from: Float, to: Float, veilMs: Int, reducedMotion: Boolean): Int =
    if (reducedMotion) 0 else (abs(to - from) * veilMs.coerceAtLeast(0)).roundToInt()
