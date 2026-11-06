package com.nendo.argosy.ui.input

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun rightStickStepFor(axisValue: Float): GamepadEvent? = when {
    axisValue < -RightStickStepper.DEADZONE -> GamepadEvent.RightStickUp
    axisValue > RightStickStepper.DEADZONE -> GamepadEvent.RightStickDown
    else -> null
}

internal fun rightStickHorizontalStepFor(axisValue: Float): GamepadEvent? = when {
    axisValue < -RightStickStepper.DEADZONE -> GamepadEvent.RightStickLeft
    axisValue > RightStickStepper.DEADZONE -> GamepadEvent.RightStickRight
    else -> null
}

/**
 * Steps from one axis of the right stick, the vertical one unless [stepFor] maps another. Crossing
 * [DEADZONE] emits one step at once, and a hold past it repeats on the shared [DirectionRepeat]
 * schedule: nothing for the hold pause, then the ramp, fast enough to travel a grid without
 * feeling like a crawl. Returning inside the deadzone stops the repeats.
 */
class RightStickStepper(
    private val scope: CoroutineScope,
    private val stepFor: (Float) -> GamepadEvent? = ::rightStickStepFor,
    private val emit: (event: GamepadEvent, isRepeat: Boolean) -> Unit
) {
    private var held: GamepadEvent? = null
    private var repeatJob: Job? = null

    fun onAxis(value: Float) {
        val step = stepFor(value)
        if (step == held) return
        held = step
        repeatJob?.cancel()
        repeatJob = null
        if (step == null) return
        emit(step, false)
        repeatJob = scope.launch {
            // Crossing the deadzone already stepped once. Wait out the hold pause before repeating
            // so a flick lands a single step and does not run past the item that was meant.
            delay(DirectionRepeat.START_DELAY_MS)
            var repeatIndex = 0
            while (true) {
                emit(step, true)
                delay(DirectionRepeat.intervalMs(repeatIndex))
                repeatIndex++
            }
        }
    }

    fun release() {
        onAxis(0f)
    }

    companion object {
        const val DEADZONE = 0.5f
    }
}
