package com.nendo.argosy.ui.input

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RightStickStepperTest {

    private val steps = mutableListOf<Pair<GamepadEvent, Boolean>>()

    /** [advance] runs tasks due exactly on the boundary, so the pause end is the first repeat. */
    private val firstRepeatAt = DirectionRepeat.START_DELAY_MS

    private fun TestScope.stepper() = RightStickStepper(backgroundScope) { event, isRepeat ->
        steps += event to isRepeat
    }

    private fun TestScope.advance(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun `axis maps to up and down only past the deadzone`() {
        assertEquals(GamepadEvent.RightStickUp, rightStickStepFor(-0.9f))
        assertEquals(GamepadEvent.RightStickDown, rightStickStepFor(0.9f))
        assertNull(rightStickStepFor(0f))
        assertNull(rightStickStepFor(-RightStickStepper.DEADZONE))
        assertNull(rightStickStepFor(RightStickStepper.DEADZONE))
        assertNull(rightStickStepFor(0.3f))
    }

    @Test
    fun `horizontal axis maps to left and right only past the deadzone`() {
        assertEquals(GamepadEvent.RightStickLeft, rightStickHorizontalStepFor(-0.9f))
        assertEquals(GamepadEvent.RightStickRight, rightStickHorizontalStepFor(0.9f))
        assertNull(rightStickHorizontalStepFor(0f))
        assertNull(rightStickHorizontalStepFor(-RightStickStepper.DEADZONE))
        assertNull(rightStickHorizontalStepFor(RightStickStepper.DEADZONE))
    }

    @Test
    fun `a horizontal stepper holds and repeats like the vertical one`() = runTest {
        val stepper = RightStickStepper(backgroundScope, ::rightStickHorizontalStepFor) { event, isRepeat ->
            steps += event to isRepeat
        }

        stepper.onAxis(0.8f)
        advance(firstRepeatAt)
        stepper.release()
        advance(1_000)

        assertEquals(
            listOf(GamepadEvent.RightStickRight to false, GamepadEvent.RightStickRight to true),
            steps
        )
    }

    @Test
    fun `a flick inside the hold pause steps once and stops`() = runTest {
        val stepper = stepper()

        stepper.onAxis(0.8f)
        advance(DirectionRepeat.START_DELAY_MS - 1)
        assertEquals(1, steps.size)

        stepper.release()
        advance(1_000)

        assertEquals(listOf(GamepadEvent.RightStickDown to false), steps)
    }

    @Test
    fun `a tilt inside the deadzone emits nothing`() = runTest {
        val stepper = stepper()

        stepper.onAxis(0.3f)
        advance(1_000)

        assertEquals(emptyList<Pair<GamepadEvent, Boolean>>(), steps)
    }

    @Test
    fun `crossing the deadzone steps at once`() = runTest {
        val stepper = stepper()

        stepper.onAxis(0.8f)

        assertEquals(listOf(GamepadEvent.RightStickDown to false), steps)
    }

    @Test
    fun `holding pauses and then speeds up past the first gap`() = runTest {
        val stepper = stepper()

        stepper.onAxis(-0.8f)
        advance(DirectionRepeat.START_DELAY_MS - 1)
        assertEquals(1, steps.size)

        advance(1)
        assertEquals(2, steps.size)

        assertTrue(DirectionRepeat.intervalMs(1) < DirectionRepeat.intervalMs(0))
        advance(DirectionRepeat.intervalMs(0))
        assertEquals(3, steps.size)

        // a second of holding now travels far further than a flat 350 ms per step
        advance(1_000)
        assertTrue("steps=${steps.size}", steps.size >= 15)
        assertEquals(
            listOf(GamepadEvent.RightStickUp to false) + List(steps.size - 1) { GamepadEvent.RightStickUp to true },
            steps
        )
    }

    @Test
    fun `further motion in the same direction does not restart the step`() = runTest {
        val stepper = stepper()

        stepper.onAxis(0.6f)
        stepper.onAxis(0.9f)
        stepper.onAxis(1f)

        assertEquals(listOf(GamepadEvent.RightStickDown to false), steps)
    }

    @Test
    fun `returning inside the deadzone stops the repeats`() = runTest {
        val stepper = stepper()

        stepper.onAxis(0.8f)
        advance(firstRepeatAt)
        stepper.onAxis(0.1f)
        advance(1_000)

        assertEquals(
            listOf(GamepadEvent.RightStickDown to false, GamepadEvent.RightStickDown to true),
            steps
        )
    }

    @Test
    fun `release stops the repeats`() = runTest {
        val stepper = stepper()

        stepper.onAxis(-0.8f)
        stepper.release()
        advance(1_000)

        assertEquals(listOf(GamepadEvent.RightStickUp to false), steps)
    }

    @Test
    fun `flipping across the center steps the new way at once`() = runTest {
        val stepper = stepper()

        stepper.onAxis(-0.8f)
        stepper.onAxis(0.8f)
        advance(firstRepeatAt)

        assertEquals(
            listOf(
                GamepadEvent.RightStickUp to false,
                GamepadEvent.RightStickDown to false,
                GamepadEvent.RightStickDown to true
            ),
            steps
        )
    }
}
