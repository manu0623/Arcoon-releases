package com.nendo.argosy.hardware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LidSleepDecisionTest {

    private fun thorDump(switchValues: Int) = listOf(
        "  Device 6: fts_ts_3",
        "    Switch Input Mapper:",
        "      SwitchValues: 0",
        "  Device 5: hall_switch",
        "    EventHub Devices: [ 5 ] ",
        "    Generation: 15",
        "    IsExternal: false",
        "    Sources: 0x80000000",
        "    Switch Input Mapper:",
        "      SwitchValues: $switchValues",
        "  Device 3: pmic_pwrkey"
    )

    private fun strayWake(
        enabled: Boolean = true,
        lidClosed: Boolean = true,
        interactive: Boolean = true,
        externalDisplayInUse: Boolean = false,
        attemptsInWindow: Int = 0
    ) = LidSleepState(enabled, lidClosed, interactive, externalDisplayInUse, attemptsInWindow)

    @Test
    fun `the hall switch reports a closed lid`() {
        assertTrue(LidSleepDecision.parseLidClosed(thorDump(1))!!)
    }

    @Test
    fun `the hall switch reports an open lid`() {
        assertFalse(LidSleepDecision.parseLidClosed(thorDump(0))!!)
    }

    @Test
    fun `another device's switch values are never read as the lid`() {
        val dump = listOf(
            "  Device 5: hall_switch",
            "    Sources: 0x80000000",
            "  Device 7: headset",
            "    Switch Input Mapper:",
            "      SwitchValues: 1"
        )
        assertNull(LidSleepDecision.parseLidClosed(dump))
    }

    @Test
    fun `a dump without the hall switch gives no lid state`() {
        assertNull(LidSleepDecision.parseLidClosed(listOf("  Device 3: pmic_pwrkey")))
    }

    @Test
    fun `a screen on under a closed lid sleeps`() {
        assertEquals(LidSleepVerdict.SLEEP, LidSleepDecision.decide(strayWake()))
    }

    @Test
    fun `an open lid, a disabled setting or a screen already off does nothing`() {
        assertEquals(LidSleepVerdict.IDLE, LidSleepDecision.decide(strayWake(lidClosed = false)))
        assertEquals(LidSleepVerdict.IDLE, LidSleepDecision.decide(strayWake(enabled = false)))
        assertEquals(LidSleepVerdict.IDLE, LidSleepDecision.decide(strayWake(interactive = false)))
    }

    @Test
    fun `an external display in use keeps the device awake`() {
        assertEquals(LidSleepVerdict.EXTERNAL_DISPLAY, LidSleepDecision.decide(strayWake(externalDisplayInUse = true)))
    }

    @Test
    fun `sleep requests stop at the per-minute limit`() {
        val limit = LidSleepDecision.MAX_ATTEMPTS_PER_WINDOW
        assertEquals(LidSleepVerdict.SLEEP, LidSleepDecision.decide(strayWake(attemptsInWindow = limit - 1)))
        assertEquals(LidSleepVerdict.RATE_LIMITED, LidSleepDecision.decide(strayWake(attemptsInWindow = limit)))
    }
}
