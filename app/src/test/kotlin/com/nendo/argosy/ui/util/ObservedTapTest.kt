package com.nendo.argosy.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ObservedTapTest {

    private val slop = 8f

    @Test
    fun `a release inside slop is a tap`() {
        assertEquals(
            ObservedTapStep.TAP,
            observedTapStep(travelled = 2f, touchSlop = slop, released = true, consumedAbove = false)
        )
    }

    @Test
    fun `a held press inside slop keeps waiting`() {
        assertEquals(
            ObservedTapStep.PENDING,
            observedTapStep(travelled = 2f, touchSlop = slop, released = false, consumedAbove = false)
        )
    }

    @Test
    fun `a press past slop is a scroll and never a tap`() {
        assertEquals(
            ObservedTapStep.ABANDON,
            observedTapStep(travelled = 20f, touchSlop = slop, released = false, consumedAbove = false)
        )
        assertEquals(
            ObservedTapStep.ABANDON,
            observedTapStep(travelled = 20f, touchSlop = slop, released = true, consumedAbove = false)
        )
    }

    @Test
    fun `a release an enclosing layer consumed is never a tap`() {
        assertEquals(
            ObservedTapStep.ABANDON,
            observedTapStep(travelled = 0f, touchSlop = slop, released = true, consumedAbove = true)
        )
        assertEquals(
            ObservedTapStep.ABANDON,
            observedTapStep(travelled = 0f, touchSlop = slop, released = false, consumedAbove = true)
        )
    }
}
