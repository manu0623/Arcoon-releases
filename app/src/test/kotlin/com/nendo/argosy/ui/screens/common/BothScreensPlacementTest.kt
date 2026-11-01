package com.nendo.argosy.ui.screens.common

import com.nendo.argosy.data.emulator.BothScreensHost
import com.nendo.argosy.util.DisplayAffinityHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BothScreensPlacementTest {

    @Test
    fun `every Azahar build opens its game on the default display`() {
        for (pkg in listOf("org.azahar_emu.azahar", "io.github.lime3ds.android", "io.github.azahar_emu.nightly")) {
            assertEquals(pkg, BothScreensHost.DEFAULT_DISPLAY, EmulatorLaunchTargetResolver.bothScreensHostOf(pkg))
        }
    }

    @Test
    fun `3ds emulators without a second screen of their own keep the normal placement`() {
        for (pkg in listOf("io.github.borked3ds.android", "org.citra.emu", "org.citra.citra_emu")) {
            assertNull(pkg, EmulatorLaunchTargetResolver.bothScreensHostOf(pkg))
        }
    }

    @Test
    fun `a default display host opens on display zero while it is lit`() {
        assertEquals(
            0,
            DisplayAffinityHelper.resolveBothScreensDisplayId(
                BothScreensHost.DEFAULT_DISPLAY,
                largestDisplayId = 3,
                defaultDisplayLit = true
            )
        )
        assertNull(
            DisplayAffinityHelper.resolveBothScreensDisplayId(
                BothScreensHost.DEFAULT_DISPLAY,
                largestDisplayId = 3,
                defaultDisplayLit = false
            )
        )
    }

    @Test
    fun `a largest display host follows the largest display`() {
        assertEquals(
            3,
            DisplayAffinityHelper.resolveBothScreensDisplayId(
                BothScreensHost.LARGEST_DISPLAY,
                largestDisplayId = 3,
                defaultDisplayLit = true
            )
        )
    }
}
