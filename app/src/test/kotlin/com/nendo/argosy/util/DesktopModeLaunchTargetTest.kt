package com.nendo.argosy.util

import android.content.Context
import android.hardware.display.DisplayManager
import com.nendo.argosy.data.preferences.EmulatorDisplayTarget
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class DesktopModeLaunchTargetTest {

    private val displayManager: DisplayManager = mockk(relaxed = true) {
        every { displays } returns emptyArray()
    }

    private val helper = DisplayAffinityHelper(
        context = mockk<Context>(relaxed = true) {
            every { getSystemService(Context.DISPLAY_SERVICE) } returns displayManager
        },
        screenCatalog = mockk(relaxed = true)
    ).apply {
        dualScreenEnabled = true
        appTargetDisplayId = APP_TARGET_DISPLAY
        roleDisplayIds = 0 to APP_TARGET_DISPLAY
        desktopModeDisplayId = DEX_DISPLAY
    }

    @Test
    fun `dex reports no secondary display and no dock`() {
        assertFalse(helper.hasSecondaryDisplay)
        assertNull(helper.dockedDisplayId)
        assertNull(helper.appScreenDisplayId(rolesSwapped = false))
    }

    @Test
    fun `games land on the launcher display under dex`() {
        assertEquals(DEX_DISPLAY, helper.getEmulatorDisplayId(rolesSwapped = false))
        assertEquals(DEX_DISPLAY, helper.getEmulatorDisplayId(rolesSwapped = true))
        EmulatorDisplayTarget.entries.forEach { target ->
            assertEquals(
                target.name,
                DEX_DISPLAY,
                helper.getDisplayTargetId(target, rolesSwapped = false)
            )
        }
    }

    @Test
    fun `apps land on the launcher display under dex`() {
        assertEquals(DEX_DISPLAY, helper.appLaunchDisplayId(rolesSwapped = false))
        assertEquals(
            DEX_DISPLAY,
            helper.appLaunchDisplayId(rolesSwapped = true, occupiedDisplayId = DEX_DISPLAY)
        )
    }

    private companion object {
        const val DEX_DISPLAY = 4
        const val APP_TARGET_DISPLAY = 2
    }
}
