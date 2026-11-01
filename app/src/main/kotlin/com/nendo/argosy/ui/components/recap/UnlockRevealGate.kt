package com.nendo.argosy.ui.components.recap

import com.nendo.argosy.ui.theme.generated.ComponentDefaults

/**
 * Whether the unlock row's reveal may run. A gate that is not [started] opens the first time at
 * least [threshold] of the row's height shows in its scroll viewport, and stays open after that.
 */
data class UnlockRevealGate(
    val started: Boolean,
    val threshold: Float = ComponentDefaults.UnlockRecap.revealVisibleFraction
) {
    fun observed(visibleHeightPx: Float, heightPx: Float): UnlockRevealGate {
        if (started || heightPx <= 0f) return this
        return if (visibleHeightPx / heightPx >= threshold) copy(started = true) else this
    }

    companion object {
        val Open = UnlockRevealGate(started = true)
        val OnVisible = UnlockRevealGate(started = false)
    }
}
