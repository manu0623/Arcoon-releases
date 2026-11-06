package com.nendo.argosy.ui.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectionRepeatTest {

    @Test
    fun `first repeat keeps the flat first gap`() {
        assertEquals(DirectionRepeat.FIRST_INTERVAL_MS, DirectionRepeat.intervalMs(0))
    }

    @Test
    fun `gaps shrink as the direction is held`() {
        val gaps = DirectionRepeat.schedule(5)
        assertEquals(listOf(130L, 106L, 87L, 71L, 58L), gaps)
        for (index in 1 until gaps.size) {
            assertTrue("gap $index must not grow", gaps[index] <= gaps[index - 1])
        }
    }

    @Test
    fun `gaps stop shrinking at the floor`() {
        val gaps = DirectionRepeat.schedule(20)
        assertEquals(DirectionRepeat.FLOOR_INTERVAL_MS, gaps.last())
        assertTrue(gaps.drop(6).all { it == DirectionRepeat.FLOOR_INTERVAL_MS })
        assertTrue(gaps.all { it >= DirectionRepeat.FLOOR_INTERVAL_MS })
    }

    @Test
    fun `the pause before repeats is long enough to read as a hold`() {
        // A pause this short is the difference between one deliberate press and an accidental step.
        assertTrue(DirectionRepeat.START_DELAY_MS >= 400L)
        assertTrue(DirectionRepeat.START_DELAY_MS < 1_000L)
    }

    @Test
    fun `a hold reaches the floor rate soon after the pause`() {
        val toFloor = DirectionRepeat.schedule(12).indexOfFirst { it == DirectionRepeat.FLOOR_INTERVAL_MS }
        assertTrue("floor reached at gap $toFloor", toFloor in 1..8)
        assertTrue(DirectionRepeat.elapsedBefore(toFloor) < 1_500L)
    }

    @Test
    fun `a long hold travels much faster than the old flat rate`() {
        // Old behaviour: 150 ms per step once the hold engaged.
        val oldSteps = 2_000L / 150L
        var elapsed = DirectionRepeat.START_DELAY_MS
        var steps = 0
        while (elapsed <= 2_000L) {
            elapsed += DirectionRepeat.intervalMs(steps)
            steps++
        }
        assertTrue("new=$steps old=$oldSteps", steps > oldSteps * 1.8)
    }

    @Test
    fun `no repeats and no elapsed time before the first one`() {
        assertEquals(emptyList<Long>(), DirectionRepeat.schedule(0))
        assertEquals(emptyList<Long>(), DirectionRepeat.schedule(-3))
        assertEquals(DirectionRepeat.START_DELAY_MS, DirectionRepeat.elapsedBefore(0))
    }

    @Test
    fun `a repeated press inside the duplicate window is still a duplicate`() {
        val gate = DirectionRepeatGate()
        assertTrue(gate.accepts(GamepadEvent.Down, isRepeat = false, nowMs = 0))
        assertFalse(gate.accepts(GamepadEvent.Down, isRepeat = false, nowMs = 100))
        assertTrue(gate.accepts(GamepadEvent.Down, isRepeat = false, nowMs = 140))
        // the duplicate window is per event: another direction is a new press, not a duplicate
        assertTrue(gate.accepts(GamepadEvent.Up, isRepeat = false, nowMs = 150))
    }

    @Test
    fun `the first repeat waits out the hold pause`() {
        val gate = DirectionRepeatGate()
        assertTrue(gate.accepts(GamepadEvent.Down, isRepeat = false, nowMs = 0))
        // halfway through the pause is still silence, well clear of the gate's jitter slack
        assertFalse(
            gate.accepts(GamepadEvent.Down, isRepeat = true, nowMs = DirectionRepeat.START_DELAY_MS / 2)
        )
        assertTrue(gate.accepts(GamepadEvent.Down, isRepeat = true, nowMs = DirectionRepeat.START_DELAY_MS))
    }

    @Test
    fun `after the pause a fast repeat stream is let through as the ramp quickens`() {
        val gate = DirectionRepeatGate()
        assertTrue(gate.accepts(GamepadEvent.Up, isRepeat = false, nowMs = 0))
        var now = DirectionRepeat.START_DELAY_MS
        assertTrue(gate.accepts(GamepadEvent.Up, isRepeat = true, nowMs = now))

        var accepted = 0
        repeat(20) {
            now += 50
            if (gate.accepts(GamepadEvent.Up, isRepeat = true, nowMs = now)) accepted++
        }
        assertTrue("accepted=$accepted", accepted >= 12)
    }

    @Test
    fun `a fresh press restarts the pause before the next repeat`() {
        val gate = DirectionRepeatGate()
        assertTrue(gate.accepts(GamepadEvent.Right, isRepeat = false, nowMs = 0))
        assertTrue(gate.accepts(GamepadEvent.Right, isRepeat = true, nowMs = 500))
        assertTrue(gate.accepts(GamepadEvent.Right, isRepeat = true, nowMs = 640))

        assertTrue(gate.accepts(GamepadEvent.Right, isRepeat = false, nowMs = 900))
        assertFalse(gate.accepts(GamepadEvent.Right, isRepeat = true, nowMs = 1_100))
        assertTrue(gate.accepts(GamepadEvent.Right, isRepeat = true, nowMs = 1_350))
    }
}
