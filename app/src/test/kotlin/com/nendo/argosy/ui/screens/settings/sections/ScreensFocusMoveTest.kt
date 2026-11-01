package com.nendo.argosy.ui.screens.settings.sections

import com.nendo.argosy.domain.model.ScreenRole
import com.nendo.argosy.ui.screens.settings.ScreenAssignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The map draws internal screens stacked on the left and attached ones on the right, so up and
 * down belong inside a column and left and right belong between them.
 */
class ScreensFocusMoveTest {

    private fun screen(key: String, builtIn: Boolean) = ScreenAssignment(
        key = key,
        displayId = key.hashCode(),
        number = 1,
        widthPx = 1920,
        heightPx = 1080,
        builtIn = builtIn,
        role = ScreenRole.PRESENTATION
    )

    private val twoInternalsOneExternal = listOf(
        screen("top", builtIn = true),
        screen("bottom", builtIn = true),
        screen("monitor", builtIn = false)
    )

    @Test
    fun `down moves to the next screen in the same column`() {
        assertEquals(1, screensFocusMove(twoInternalsOneExternal, current = 0, dx = 0, dy = 1))
    }

    @Test
    fun `down off the end of a column stops rather than crossing to the other one`() {
        assertNull(screensFocusMove(twoInternalsOneExternal, current = 1, dx = 0, dy = 1))
    }

    @Test
    fun `up off the top of a column stops`() {
        assertNull(screensFocusMove(twoInternalsOneExternal, current = 0, dx = 0, dy = -1))
    }

    @Test
    fun `right crosses from the internal column to the attached one`() {
        assertEquals(2, screensFocusMove(twoInternalsOneExternal, current = 0, dx = 1, dy = 0))
    }

    @Test
    fun `left crosses back to the internal column at the same row`() {
        assertEquals(0, screensFocusMove(twoInternalsOneExternal, current = 2, dx = -1, dy = 0))
    }

    @Test
    fun `crossing to a shorter column and back does not return to the starting screen`() {
        val right = screensFocusMove(twoInternalsOneExternal, current = 1, dx = 1, dy = 0)
        assertEquals(2, right)
        assertEquals(0, screensFocusMove(twoInternalsOneExternal, current = right!!, dx = -1, dy = 0))
    }

    @Test
    fun `right from the attached column stops`() {
        assertNull(screensFocusMove(twoInternalsOneExternal, current = 2, dx = 1, dy = 0))
    }

    @Test
    fun `left from the internal column stops`() {
        assertNull(screensFocusMove(twoInternalsOneExternal, current = 0, dx = -1, dy = 0))
    }

    @Test
    fun `right with nothing attached stops`() {
        val internalsOnly = twoInternalsOneExternal.filter { it.builtIn }
        assertNull(screensFocusMove(internalsOnly, current = 0, dx = 1, dy = 0))
    }

    @Test
    fun `crossing to a shorter column lands on its last screen`() {
        val screens = listOf(
            screen("top", builtIn = true),
            screen("bottom", builtIn = true),
            screen("monitor", builtIn = false)
        )
        assertEquals(2, screensFocusMove(screens, current = 1, dx = 1, dy = 0))
    }

    @Test
    fun `an empty map has nowhere to move`() {
        assertNull(screensFocusMove(emptyList(), current = 0, dx = 0, dy = 1))
    }
}
