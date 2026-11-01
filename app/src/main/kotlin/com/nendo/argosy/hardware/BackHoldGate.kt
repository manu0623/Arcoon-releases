package com.nendo.argosy.hardware

/**
 * Splits a Back press into a tap or a hold for a key filter that has to swallow the press before
 * knowing which it is. A tap is replayed through [onTap] on release; a hold past the scheduled
 * delay fires [onHold] and its release is swallowed. Presses that start while [filtering] is
 * false pass through untouched, down and up alike.
 */
class BackHoldGate(
    private val filtering: () -> Boolean,
    private val scheduleHold: (Runnable) -> Unit,
    private val cancelHold: (Runnable) -> Unit,
    private val onTap: () -> Unit,
    private val onHold: () -> Unit
) {
    private var downConsumed = false
    private var holdFired = false
    private val hold = Runnable {
        holdFired = true
        onHold()
    }

    fun onDown(isRepeat: Boolean): Boolean {
        if (isRepeat) return downConsumed
        if (!filtering()) {
            downConsumed = false
            return false
        }
        downConsumed = true
        holdFired = false
        scheduleHold(hold)
        return true
    }

    fun onUp(): Boolean {
        if (!downConsumed) return false
        downConsumed = false
        cancelHold(hold)
        if (!holdFired) onTap()
        holdFired = false
        return true
    }

    fun reset() {
        cancelHold(hold)
        downConsumed = false
        holdFired = false
    }
}
