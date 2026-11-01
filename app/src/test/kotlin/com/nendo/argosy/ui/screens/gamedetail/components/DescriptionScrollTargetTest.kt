package com.nendo.argosy.ui.screens.gamedetail.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DescriptionScrollTargetTest {

    private val viewport = 1000
    private val top = 400

    @Test
    fun `a description that fits on screen moves straight to the next section`() {
        assertNull(descriptionScrollTarget(scroll = top, viewport = viewport, top = top, height = 600, direction = 1))
        assertNull(descriptionScrollTarget(scroll = top, viewport = viewport, top = top, height = 600, direction = -1))
    }

    @Test
    fun `a long description pages down until its end is in view, then moves on`() {
        val height = 2500
        val end = top + height - viewport

        val first = descriptionScrollTarget(top, viewport, top, height, 1)
        assertEquals(top + 750, first)
        val second = descriptionScrollTarget(first!!, viewport, top, height, 1)
        assertEquals(end, second)
        assertNull(descriptionScrollTarget(second!!, viewport, top, height, 1))
    }

    @Test
    fun `paging up stops at the description's start, then moves on`() {
        val height = 2500

        assertEquals(top + 50, descriptionScrollTarget(top + 800, viewport, top, height, -1))
        assertEquals(top, descriptionScrollTarget(top + 500, viewport, top, height, -1))
        assertNull(descriptionScrollTarget(top, viewport, top, height, -1))
    }

    @Test
    fun `nothing is paged before the page has been measured`() {
        assertNull(descriptionScrollTarget(0, viewport = 0, top = top, height = 2500, direction = 1))
        assertNull(descriptionScrollTarget(0, viewport = viewport, top = top, height = 0, direction = 1))
    }
}
