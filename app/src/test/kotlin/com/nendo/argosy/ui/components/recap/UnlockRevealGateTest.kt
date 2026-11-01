package com.nendo.argosy.ui.components.recap

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockRevealGateTest {

    private val rowHeight = 100f

    @Test
    fun `a row below the fold does not start the reveal`() {
        assertFalse(UnlockRevealGate.OnVisible.observed(0f, rowHeight).started)
    }

    @Test
    fun `a row showing less than the threshold does not start the reveal`() {
        assertFalse(UnlockRevealGate.OnVisible.observed(59f, rowHeight).started)
    }

    @Test
    fun `a row scrolled into view starts the reveal`() {
        val hidden = UnlockRevealGate.OnVisible.observed(0f, rowHeight)
        val partly = hidden.observed(30f, rowHeight)
        val shown = partly.observed(60f, rowHeight)
        assertFalse(hidden.started)
        assertFalse(partly.started)
        assertTrue(shown.started)
    }

    @Test
    fun `a row already visible on open starts the reveal on the first observation`() {
        assertTrue(UnlockRevealGate.OnVisible.observed(rowHeight, rowHeight).started)
    }

    @Test
    fun `scrolling a started row away keeps the reveal started`() {
        val started = UnlockRevealGate.OnVisible.observed(rowHeight, rowHeight)
        val scrolledAway = started.observed(0f, rowHeight)
        assertTrue(scrolledAway.started)
        assertSame(started, scrolledAway)
    }

    @Test
    fun `a row not yet measured never starts the reveal`() {
        assertFalse(UnlockRevealGate.OnVisible.observed(0f, 0f).started)
    }

    @Test
    fun `an open gate starts without any observation`() {
        assertTrue(UnlockRevealGate.Open.started)
    }
}
