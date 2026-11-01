package com.nendo.argosy.ui.input

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RightStickStepperTest {

    private val steps = mutableListOf<Pair<GamepadEvent, Boolean>>()

    private fun TestScope.stepper() = RightStickStepper(backgroundScope) { event, isRepeat ->
        steps += event to isRepeat
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
        advanceTimeBy(RightStickStepper.STEP_INTERVAL_MS)
        runCurrent()
        stepper.release()
        advanceTimeBy(RightStickStepper.STEP_INTERVAL_MS * 3)
        runCurrent()

        assertEquals(
            listOf(GamepadEvent.RightStickRight to false, GamepadEvent.RightStickRight to true),
            steps
        )
    }

    @Test
    fun `a tilt inside the deadzone emits nothing`() = runTest {
        val stepper = stepper()

        stepper.onAxis(0.3f)
        advanceTimeBy(RightStickStepper.STEP_INTERVAL_MS * 3)
        runCurrent()

        assertEquals(emptyList<Pair<GamepadEvent, Boolean>>(), steps)
    }

    @Test
    fun `crossing the deadzone steps at once`() = runTest {
        val stepper = stepper()

        stepper.onAxis(0.8f)

        assertEquals(listOf(GamepadEvent.RightStickDown to false), steps)
    }

    @Test
    fun `holding repeats at a constant interval`() = runTest {
        val stepper = stepper()

        stepper.onAxis(-0.8f)
        advanceTimeBy(RightStickStepper.STEP_INTERVAL_MS - 1)
        runCurrent()
        assertEquals(1, steps.size)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, steps.size)

        advanceTimeBy(RightStickStepper.STEP_INTERVAL_MS * 3)
        runCurrent()
        assertEquals(
            listOf(GamepadEvent.RightStickUp to false) +
                List(4) { GamepadEvent.RightStickUp to true },
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
        advanceTimeBy(RightStickStepper.STEP_INTERVAL_MS)
        runCurrent()
        stepper.onAxis(0.1f)
        advanceTimeBy(RightStickStepper.STEP_INTERVAL_MS * 5)
        runCurrent()

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
        advanceTimeBy(RightStickStepper.STEP_INTERVAL_MS * 5)
        runCurrent()

        assertEquals(listOf(GamepadEvent.RightStickUp to false), steps)
    }

    @Test
    fun `flipping across the center steps the new way at once`() = runTest {
        val stepper = stepper()

        stepper.onAxis(-0.8f)
        stepper.onAxis(0.8f)
        advanceTimeBy(RightStickStepper.STEP_INTERVAL_MS)
        runCurrent()

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
