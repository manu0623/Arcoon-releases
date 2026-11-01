package com.nendo.argosy

import org.junit.Assert.assertEquals
import org.junit.Test

class ArgosyActivityNonDefaultDisplayTest {

    @Test
    fun `dex keeps the launcher on its display in every arrangement`() {
        for (dualScreenEnabled in listOf(true, false)) {
            for (docked in listOf(true, false)) {
                assertEquals(
                    "dualScreen=$dualScreenEnabled docked=$docked",
                    NonDefaultDisplayLaunch.RUN_HERE,
                    nonDefaultDisplayLaunch(
                        desktopMode = true,
                        dualScreenEnabled = dualScreenEnabled,
                        docked = docked
                    )
                )
            }
        }
    }

    @Test
    fun `outside dex with dual screen on the companion takes the display`() {
        assertEquals(
            NonDefaultDisplayLaunch.HAND_TO_COMPANION,
            nonDefaultDisplayLaunch(desktopMode = false, dualScreenEnabled = true, docked = false)
        )
        assertEquals(
            NonDefaultDisplayLaunch.HAND_TO_COMPANION,
            nonDefaultDisplayLaunch(desktopMode = false, dualScreenEnabled = true, docked = true)
        )
    }

    @Test
    fun `outside dex a docked device hands the display to the companion with dual screen off`() {
        assertEquals(
            NonDefaultDisplayLaunch.HAND_TO_COMPANION,
            nonDefaultDisplayLaunch(desktopMode = false, dualScreenEnabled = false, docked = true)
        )
    }

    @Test
    fun `outside dex with dual screen off and no dock the launcher yields the display`() {
        assertEquals(
            NonDefaultDisplayLaunch.YIELD,
            nonDefaultDisplayLaunch(desktopMode = false, dualScreenEnabled = false, docked = false)
        )
    }
}
