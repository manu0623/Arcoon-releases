package com.nendo.argosy.libretro

import com.nendo.argosy.data.local.entity.HotkeyAction
import com.nendo.argosy.data.local.entity.HotkeyEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cbrt

class SunLevelTest {
    private val mgbaManualLevels = listOf(22, 27, 33, 40, 49, 64, 84, 106, 131, 161, 205)

    @Test
    fun everyLevelReachesTheMatchingMgbaManualLevelThroughTheCoreConversion() {
        for (level in SunLevel.MIN..SunLevel.MAX) {
            val coreLuxLevel = (cbrt(SunLevel.lux(level)) * 8).toInt()
            assertEquals("level $level", mgbaManualLevels[level], coreLuxLevel)
        }
    }

    @Test
    fun brighterIsAlwaysMoreLux() {
        for (level in SunLevel.MIN until SunLevel.MAX) {
            assertTrue("level $level", SunLevel.lux(level + 1) > SunLevel.lux(level))
        }
    }

    @Test
    fun stepsStopAtBothEnds() {
        assertEquals(SunLevel.MIN, SunLevel.step(SunLevel.MIN, -1))
        assertEquals(SunLevel.MAX, SunLevel.step(SunLevel.MAX, 1))
        assertEquals(4, SunLevel.step(3, 1))
        assertEquals(2, SunLevel.step(3, -1))
    }

    @Test
    fun boundOnlyWhenASunActionIsPresent() {
        fun hotkey(action: HotkeyAction) = HotkeyEntity(action = action, buttonComboJson = "[]")
        assertFalse(SunLevel.isBound(listOf(hotkey(HotkeyAction.QUICK_SAVE))))
        assertTrue(SunLevel.isBound(listOf(hotkey(HotkeyAction.SUN_DIMMER))))
        assertTrue(SunLevel.isBound(listOf(hotkey(HotkeyAction.QUICK_SAVE), hotkey(HotkeyAction.SUN_BRIGHTER))))
    }
}
