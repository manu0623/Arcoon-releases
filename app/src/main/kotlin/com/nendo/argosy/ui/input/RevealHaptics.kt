package com.nendo.argosy.ui.input

import kotlinx.coroutines.delay

/**
 * Delay before each beat of a reveal cue, in order; the first is always 0.
 *
 * The beats are spread across the caller's own animation length, so the cue travels with the
 * surface as it comes in rather than being a single tap at either end. No animation (zero
 * duration, which is what reduced motion uses) means no beats.
 */
internal fun revealBeatDelays(durationMs: Int, beats: Int): List<Long> {
    if (durationMs <= 0 || beats <= 0) return emptyList()
    val step = (durationMs.toLong() / beats).coerceAtLeast(1L)
    return List(beats) { it * step }
}

/**
 * Ticks a light cue along a reveal: one as the surface starts to move, then evenly spaced beats
 * until it has settled. [durationMs] is the animation's own length. A null manager (a surface
 * without feedback) does nothing.
 */
suspend fun HapticFeedbackManager?.revealTicks(
    durationMs: Int,
    beats: Int = 3,
    pattern: HapticPattern = HapticPattern.FOCUS_CHANGE
) {
    val manager = this ?: return
    var elapsed = 0L
    for (beatAt in revealBeatDelays(durationMs, beats)) {
        if (beatAt > elapsed) {
            delay(beatAt - elapsed)
            elapsed = beatAt
        }
        manager.vibrate(pattern)
    }
}
