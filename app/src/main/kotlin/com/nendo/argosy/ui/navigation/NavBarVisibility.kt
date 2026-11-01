package com.nendo.argosy.ui.navigation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Raised state of the floating nav bar and its auto-hide timer.
 *
 * With auto-hide on, [show] raises the bar for [hideAfterMs] and [hide] lowers it at once. With
 * auto-hide off the bar is visible whenever Quick Navigation is on, so neither call changes what
 * [isVisible] reports; overlays that cover the bar gate it where it is drawn.
 */
class NavBarVisibility(
    private val scope: CoroutineScope,
    private val hideAfterMs: Long = AUTO_HIDE_MS
) {
    private val _raised = MutableStateFlow(false)
    val raised: StateFlow<Boolean> = _raised.asStateFlow()

    private var hideJob: Job? = null

    fun show(autoHide: Boolean) {
        hideJob?.cancel()
        hideJob = null
        if (!autoHide) return
        _raised.value = true
        hideJob = scope.launch {
            delay(hideAfterMs)
            _raised.value = false
        }
    }

    fun hide() {
        hideJob?.cancel()
        hideJob = null
        _raised.value = false
    }

    companion object {
        const val AUTO_HIDE_MS = 5_000L

        fun isAlwaysVisible(quickNavigation: Boolean, autoHide: Boolean): Boolean =
            quickNavigation && !autoHide

        fun isVisible(quickNavigation: Boolean, autoHide: Boolean, raised: Boolean): Boolean =
            isAlwaysVisible(quickNavigation, autoHide) || (quickNavigation && raised)

        /**
         * Whether a press on the bottom reveal edge raises the bar. A press on a tappable guide-bar
         * hint belongs to that hint and never raises it.
         */
        fun revealRaisesBar(navBarAllowed: Boolean, pressOnFooterHint: Boolean): Boolean =
            navBarAllowed && !pressOnFooterHint

        /**
         * Whether a press on the bottom reveal edge raises a hidden bar. A press that does belongs
         * to the reveal alone and reaches nothing drawn underneath the bar.
         */
        fun revealClaimsPress(
            navBarAllowed: Boolean,
            barVisible: Boolean,
            destinationCount: Int,
            pressOnFooterHint: Boolean
        ): Boolean = revealRaisesBar(navBarAllowed, pressOnFooterHint) && !barVisible && destinationCount > 1
    }
}
