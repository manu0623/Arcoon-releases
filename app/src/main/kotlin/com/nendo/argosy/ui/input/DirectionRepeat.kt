package com.nendo.argosy.ui.input

/**
 * Timing for one held direction. The press itself lands at once; the repeats that follow start
 * only after the [START_DELAY_MS] hold pause and then speed up, each gap [SHRINK] times the one
 * before until it reaches [FLOOR_INTERVAL_MS].
 *
 * The pause is what separates a press from a hold. Repeating the instant a direction engages
 * trades an accidental extra step for a shorter hold, and an extra step is the more expensive
 * mistake on a grid: it moves the selection somewhere the person did not mean to go. The pause is
 * long enough to read as deliberate, and past it the ramp takes over so travel stays fluid - a
 * flat gap instead reads as a slow crawl a moment after the hold starts and stays slow however long
 * it is held.
 *
 * Shared by the left-stick/hat direction repeat and the right-stick stepper so both axes travel at
 * the same pace.
 */
object DirectionRepeat {
    const val START_DELAY_MS = 450L
    const val FIRST_INTERVAL_MS = 130L
    const val FLOOR_INTERVAL_MS = 55L
    const val SHRINK = 0.82

    /** Gap to wait before repeat number [index], the first repeat being index 0. */
    fun intervalMs(index: Int): Long {
        if (index <= 0) return FIRST_INTERVAL_MS
        var gap = FIRST_INTERVAL_MS.toDouble()
        var remaining = index
        while (remaining > 0 && gap > FLOOR_INTERVAL_MS) {
            gap *= SHRINK
            remaining--
        }
        return gap.toLong().coerceAtLeast(FLOOR_INTERVAL_MS)
    }

    /** The first [repeats] gaps, in order. */
    fun schedule(repeats: Int): List<Long> = List(repeats.coerceAtLeast(0)) { intervalMs(it) }

    /** Elapsed time from the hold to the moment repeat [index] fires. */
    fun elapsedBefore(index: Int): Long {
        var total = START_DELAY_MS
        for (step in 0 until index.coerceAtLeast(0)) total += intervalMs(step)
        return total
    }
}

/**
 * Duplicate filter for one stream of [GamepadEvent]s whose repeats carry travel.
 *
 * A fresh press is a duplicate when the same event arrives again inside [duplicateWindowMs], which
 * is how two dispatch paths (primary and companion) reporting one physical press is caught. A
 * repeat is not a duplicate - it is the person travelling - so it is gated on the accelerating
 * [DirectionRepeat] gap instead, and a faster stream (a d-pad repeating every ~50 ms, or the stick
 * repeat generated here) passes as soon as the ramp allows it. The first repeat must also clear the
 * hold pause, so a controller whose own repeat starts sooner than ours cannot cut the pause short.
 * [slackMs] keeps clock jitter from dropping a repeat generated exactly on the schedule.
 */
class DirectionRepeatGate(
    private val duplicateWindowMs: Long = 140L,
    private val slackMs: Long = 12L
) {
    private val lastAcceptedAt = mutableMapOf<GamepadEvent, Long>()
    private val runs = mutableMapOf<GamepadEvent, Int>()

    fun accepts(event: GamepadEvent, isRepeat: Boolean, nowMs: Long): Boolean {
        val last = lastAcceptedAt[event]
        if (isRepeat) {
            if (last != null) {
                val run = runs[event] ?: 0
                val gap = if (run == 0) DirectionRepeat.START_DELAY_MS else DirectionRepeat.intervalMs(run)
                if (nowMs - last < gap - slackMs) return false
            }
            runs[event] = (runs[event] ?: 0) + 1
        } else {
            if (last != null && nowMs - last < duplicateWindowMs) return false
            runs[event] = 0
        }
        lastAcceptedAt[event] = nowMs
        return true
    }

    fun clear() {
        lastAcceptedAt.clear()
        runs.clear()
    }
}
