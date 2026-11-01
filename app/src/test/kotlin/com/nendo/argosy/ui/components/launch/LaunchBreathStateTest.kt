package com.nendo.argosy.ui.components.launch

import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchBreathStateTest {

    private val period = 4000
    private val floor = 0.1f
    private val peak = 0.3f
    private val midpoint = 0.2f
    private val tolerance = 1e-3f

    private fun alpha(elapsedMs: Long, reduced: Boolean = false) =
        breathAlpha(elapsedMs, period, floor, peak, reduced)

    @Test
    fun breathStartsAtTheFloor() {
        assertEquals(floor, alpha(0L), tolerance)
    }

    @Test
    fun breathPeaksHalfwayThroughThePeriod() {
        assertEquals(peak, alpha(period / 2L), tolerance)
    }

    @Test
    fun breathReturnsToTheFloorAfterOnePeriod() {
        assertEquals(floor, alpha(period.toLong()), tolerance)
    }

    @Test
    fun breathIsAtTheMidpointAQuarterThrough() {
        assertEquals(midpoint, alpha(period / 4L), tolerance)
    }

    @Test
    fun breathRepeatsEveryPeriod() {
        assertEquals(alpha(period / 4L), alpha(period * 3L + period / 4L), tolerance)
    }

    @Test
    fun reducedMotionHoldsTheMidpointAtAnyTime() {
        listOf(0L, period / 4L, period / 2L, period.toLong(), 12_345L).forEach { t ->
            assertEquals("$t", midpoint, alpha(t, reduced = true), tolerance)
        }
    }

    @Test
    fun equalFloorAndPeakHoldConstant() {
        listOf(0L, period / 4L, period / 2L, period.toLong()).forEach { t ->
            assertEquals("$t", peak, breathAlpha(t, period, peak, peak, reduced = false), tolerance)
        }
    }

    @Test
    fun nonPositivePeriodHoldsTheMidpoint() {
        assertEquals(midpoint, breathAlpha(1_000L, 0, floor, peak, reduced = false), tolerance)
    }
}
