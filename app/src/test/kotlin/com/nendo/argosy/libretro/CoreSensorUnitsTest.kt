package com.nendo.argosy.libretro

import org.junit.Assert.assertEquals
import org.junit.Test

class CoreSensorUnitsTest {
    @Test
    fun vbamReceivesMetresPerSecondSquared() {
        assertEquals(9.80665f, CoreSensorUnits.accelerometerScale("vbam"), 0f)
    }

    @Test
    fun everyOtherCoreReceivesG() {
        for (core in listOf("mgba", "melondsds", "azahar", "dolphin", null)) {
            assertEquals("$core", 1f, CoreSensorUnits.accelerometerScale(core), 0f)
        }
    }
}
