package com.nendo.argosy.util

import android.hardware.display.DisplayManager
import android.view.Display
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DockedDisplayDetectionTest {

    private fun display(id: Int, state: Int, presentation: Boolean = false): Display = mockk {
        every { displayId } returns id
        every { this@mockk.state } returns state
        every { flags } returns if (presentation) Display.FLAG_PRESENTATION else 0
    }

    private fun manager(vararg displays: Display): DisplayManager = mockk {
        every { this@mockk.displays } returns arrayOf(*displays)
    }

    private fun docked(
        dm: DisplayManager,
        firmwareBlanksPanels: Boolean = false,
        desktopMode: Boolean = false
    ): Int? = DisplayAffinityHelper.dockedExternalDisplayId(dm, firmwareBlanksPanels, desktopMode)

    @Test
    fun `a dex monitor beside a dark panel is not docked`() {
        val dm = manager(
            display(Display.DEFAULT_DISPLAY, Display.STATE_OFF),
            display(7, Display.STATE_ON, presentation = true)
        )

        assertEquals(7, docked(dm))
        assertNull(docked(dm, desktopMode = true))
        assertNull(docked(dm, firmwareBlanksPanels = true, desktopMode = true))
    }

    @Test
    fun `panel off beside a lit television reads as docked on the television`() {
        val dm = manager(
            display(Display.DEFAULT_DISPLAY, Display.STATE_OFF),
            display(7, Display.STATE_ON, presentation = true)
        )

        assertEquals(7, docked(dm))
    }

    @Test
    fun `a lit panel beside a television is not docked`() {
        val dm = manager(
            display(Display.DEFAULT_DISPLAY, Display.STATE_ON),
            display(7, Display.STATE_ON, presentation = true)
        )

        assertNull(docked(dm))
    }

    @Test
    fun `firmware blanking a panel android still reports on reads as docked`() {
        val dm = manager(
            display(Display.DEFAULT_DISPLAY, Display.STATE_ON),
            display(7, Display.STATE_ON, presentation = true)
        )

        assertEquals(7, docked(dm, firmwareBlanksPanels = true))
    }

    @Test
    fun `a sleeping device with everything dark is not docked`() {
        val dm = manager(
            display(Display.DEFAULT_DISPLAY, Display.STATE_OFF),
            display(7, Display.STATE_OFF, presentation = true)
        )

        assertNull(docked(dm))
        assertNull(docked(dm, firmwareBlanksPanels = true))
    }

    @Test
    fun `a dark panel with no external display is not docked`() {
        val dm = manager(display(Display.DEFAULT_DISPLAY, Display.STATE_OFF))

        assertNull(docked(dm, firmwareBlanksPanels = true))
    }
}
