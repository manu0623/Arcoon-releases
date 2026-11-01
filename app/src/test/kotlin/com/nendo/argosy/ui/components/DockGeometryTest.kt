package com.nendo.argosy.ui.components

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DockGeometryTest {
    @Test
    fun `popup total touch window remains bounded when it nearly fills a tiny window`() {
        for (direction in LayoutDirection.entries) {
            val position = DockPopupPositionProvider(16).calculatePosition(
                IntRect(168, 248, 216, 296), IntSize(240, 320),
                direction, IntSize(192, 312)
            )
            assertTrue(position.y >= 0)
            assertTrue(position.y + 312 <= 320)
            assertTrue(position.x >= 0)
            assertTrue(position.x + 192 <= 240)
        }
    }

    private fun widths(maximum: Float, preferredMaximum: Float, appCount: Int) = dockWidths(
        maximum = maximum, preferredMaximum = preferredMaximum, appCount = appCount,
        slot = 48f, gap = 8f, padding = 16f, appIconSize = 48f, controlIconSize = 24f
    )

    @Test
    fun `empty dock reserves the same app area as three pinned apps`() {
        val empty = widths(maximum = 1000f, preferredMaximum = 104f, appCount = 0)
        val threeApps = widths(maximum = 1000f, preferredMaximum = 104f, appCount = 3)

        assertEquals(48f * 3 + 8f * 2, empty.appsWidth)
        assertEquals(threeApps, empty)
    }

    @Test
    fun `empty dock shrinks the reserved app area on narrow screens`() {
        val dock = widths(maximum = 200f, preferredMaximum = 104f, appCount = 0)

        assertTrue(dock.appsWidth > 0f)
        assertTrue(dock.width <= 200f)
    }

    @Test
    fun `empty dock without tools keeps only the apps button`() {
        val dock = dockWidths(
            maximum = 1000f, preferredMaximum = 104f, appCount = 0,
            slot = 48f, gap = 8f, padding = 16f, appIconSize = 48f, controlIconSize = 24f,
            controlCount = 1
        )

        assertEquals(0f, dock.appsWidth)
        assertEquals(48f + 16f * 2, dock.width)
    }

    @Test
    fun `narrow preferred width still fits three pinned apps`() {
        val dock = widths(maximum = 1000f, preferredMaximum = 104f, appCount = 5)

        assertEquals(48f * 3 + 8f * 2, dock.appsWidth)
    }

    @Test
    fun `fewer pinned apps than the minimum size the dock to those apps`() {
        val dock = widths(maximum = 1000f, preferredMaximum = 104f, appCount = 1)

        assertEquals(48f, dock.appsWidth)
    }

    @Test
    fun `screen narrower than the minimum shrinks the app area instead of overflowing`() {
        val dock = widths(maximum = 200f, preferredMaximum = 104f, appCount = 5)

        assertTrue(dock.width <= 200f)
        assertTrue(dock.appsWidth >= 0f)
    }

    @Test
    fun `dock without tools drops the caret slot and its gap`() {
        val withTools = widths(maximum = 1000f, preferredMaximum = 104f, appCount = 1)
        val withoutTools = dockWidths(
            maximum = 1000f, preferredMaximum = 104f, appCount = 1,
            slot = 48f, gap = 8f, padding = 16f, appIconSize = 48f, controlIconSize = 24f,
            controlCount = 1
        )

        assertEquals(withTools.width - 48f - withTools.groupGap, withoutTools.width)
    }

    @Test
    fun `tools are unavailable when no action is wired and one display exists`() {
        val oneDisplay = listOf(DisplayFocusTarget(0, 1))

        assertEquals(listOf(false, false, false), dockToolsEnabled(null, null, {}, oneDisplay))
        assertEquals(listOf(false, true, false), dockToolsEnabled(null, {}, null, oneDisplay))
    }

    @Test
    fun `tool focus wraps and skips unavailable actions`() {
        assertEquals(2, dockToolFocusMove(0, -1, listOf(true, true, true)))
        assertEquals(0, dockToolFocusMove(2, 1, listOf(true, true, true)))
        assertEquals(2, dockToolFocusMove(0, 1, listOf(true, false, true)))
        assertEquals(1, dockToolFocusMove(1, 1, listOf(false, true, false)))
        assertEquals(1, dockToolFocusMove(1, 1, listOf(false, false, false)))
        assertEquals(1, dockToolFocusMove(1, 1, emptyList()))
    }
}
