package com.nendo.argosy.hardware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackHoldGateTest {

    private var filtering = true
    private var scheduled: Runnable? = null
    private var taps = 0
    private var holds = 0

    private val gate = BackHoldGate(
        filtering = { filtering },
        scheduleHold = { scheduled = it },
        cancelHold = { if (scheduled === it) scheduled = null },
        onTap = { taps++ },
        onHold = { holds++ }
    )

    private fun elapseHold() {
        scheduled?.run()
        scheduled = null
    }

    @Test
    fun `a short press is swallowed and replayed as one tap on release`() {
        assertTrue(gate.onDown(isRepeat = false))
        assertTrue(gate.onUp())

        assertEquals(1, taps)
        assertEquals(0, holds)
        assertEquals(null, scheduled)
    }

    @Test
    fun `a held press opens the panel once and its release sends no tap`() {
        gate.onDown(isRepeat = false)
        elapseHold()
        assertTrue(gate.onDown(isRepeat = true))
        assertTrue(gate.onUp())

        assertEquals(1, holds)
        assertEquals(0, taps)
    }

    @Test
    fun `a press outside a filtered session passes through untouched`() {
        filtering = false

        assertFalse(gate.onDown(isRepeat = false))
        assertFalse(gate.onDown(isRepeat = true))
        assertFalse(gate.onUp())
        assertEquals(0, taps + holds)
    }

    @Test
    fun `a press that started filtered stays swallowed even if filtering stops before release`() {
        gate.onDown(isRepeat = false)
        filtering = false

        assertTrue(gate.onUp())
        assertEquals(1, taps)
    }

    @Test
    fun `reset drops a pending hold`() {
        gate.onDown(isRepeat = false)
        gate.reset()

        assertEquals(null, scheduled)
        assertFalse(gate.onUp())
        assertEquals(0, taps + holds)
    }
}
