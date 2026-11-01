package com.nendo.argosy.ui.dualscreen

import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ShowcaseMotionPhase { Settling, Entering, AtRest }

data class ShowcaseMotionState(
    val backgroundKey: Long? = null,
    val contentKey: Long? = null,
    val phase: ShowcaseMotionPhase = ShowcaseMotionPhase.AtRest
)

const val UNIDENTIFIED_GAME_KEY = -1L

/**
 * The two keys the showcase animates on for the focused game: the background key moves on every
 * [focus], the content key only once the id has held still for the settle period.
 */
class ShowcaseMotionCoordinator(
    private val scope: CoroutineScope,
    private val settleMs: Long
) {
    private val _state = MutableStateFlow(ShowcaseMotionState())
    val state: StateFlow<ShowcaseMotionState> = _state.asStateFlow()
    private var settle: Job? = null

    fun focus(gameId: Long?) {
        if (gameId == _state.value.backgroundKey) return
        settle?.cancel()
        settle = null
        if (gameId == null) {
            _state.value = ShowcaseMotionState()
            return
        }
        _state.update { it.copy(backgroundKey = gameId, phase = ShowcaseMotionPhase.Settling) }
        settle = scope.launch {
            delay(settleMs)
            _state.update { it.copy(contentKey = gameId, phase = ShowcaseMotionPhase.Entering) }
        }
    }

    fun entranceFinished(contentKey: Long?) {
        _state.update { current ->
            if (current.phase == ShowcaseMotionPhase.Entering && current.contentKey == contentKey) {
                current.copy(phase = ShowcaseMotionPhase.AtRest)
            } else {
                current
            }
        }
    }
}

data class ShowcaseMotionDurations(
    val backdropCrossfadeMs: Int,
    val enterMs: Int,
    val coverDelayMs: Int,
    val textDelayMs: Int,
    val exitMs: Int,
    val coverDriftDp: Int,
    val textRiseDp: Int,
    val floatTravelDp: Int,
    val floatSlideMs: Int,
    val floatHoldMs: Int,
    val floatEnabled: Boolean
)

fun showcaseMotionDurations(reduced: Boolean): ShowcaseMotionDurations = if (reduced) {
    ShowcaseMotionDurations(
        backdropCrossfadeMs = 0,
        enterMs = 0,
        coverDelayMs = 0,
        textDelayMs = 0,
        exitMs = 0,
        coverDriftDp = 0,
        textRiseDp = 0,
        floatTravelDp = 0,
        floatSlideMs = 0,
        floatHoldMs = 0,
        floatEnabled = false
    )
} else {
    ShowcaseMotionDurations(
        backdropCrossfadeMs = ComponentDefaults.GameShowcase.backdropCrossfadeMs,
        enterMs = ComponentDefaults.GameShowcase.enterMs,
        coverDelayMs = ComponentDefaults.GameShowcase.coverDelayMs,
        textDelayMs = ComponentDefaults.GameShowcase.textDelayMs,
        exitMs = ComponentDefaults.GameShowcase.exitMs,
        coverDriftDp = ComponentDefaults.GameShowcase.coverDriftDp,
        textRiseDp = ComponentDefaults.GameShowcase.textRiseDp,
        floatTravelDp = ComponentDefaults.GameShowcase.floatTravelDp,
        floatSlideMs = ComponentDefaults.GameShowcase.floatSlideMs,
        floatHoldMs = ComponentDefaults.GameShowcase.floatHoldMs,
        floatEnabled = true
    )
}
