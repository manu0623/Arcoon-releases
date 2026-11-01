package com.nendo.argosy.ui.common

import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StackedScreenshotTest {

    private val wide = 16f / 9f
    private val fourThree = 4f / 3f

    @Test
    fun `ds and 3ds frames are stacked`() {
        assertTrue(StackedScreenshot.isStacked(256f, 384f))
        assertTrue(StackedScreenshot.isStacked(400f, 480f))
    }

    @Test
    fun `a scaled-down stacked frame keeps its shape`() {
        assertTrue(StackedScreenshot.isStacked(640f, 960f))
        assertTrue(StackedScreenshot.isStacked(640f, 768f))
    }

    @Test
    fun `landscape and square frames are not stacked`() {
        assertFalse(StackedScreenshot.isStacked(1920f, 1080f))
        assertFalse(StackedScreenshot.isStacked(256f, 256f))
        assertFalse(StackedScreenshot.isStacked(0f, 384f))
    }

    @Test
    fun `tall single-screen frames are not stacked`() {
        assertFalse(StackedScreenshot.isStacked(224f, 288f))
        assertFalse(StackedScreenshot.isStacked(240f, 320f))
        assertFalse(StackedScreenshot.isStacked(102f, 160f))
        assertFalse(StackedScreenshot.isStacked(144f, 224f))
        assertFalse(StackedScreenshot.isStacked(1080f, 2400f))
    }

    @Test
    fun `a landscape slot shows only the top screen of a stacked frame`() {
        assertEquals(IntRect(0, 0, 256, 192), StackedScreenshot.sourceRect(256, 384, fourThree))
        assertEquals(IntRect(0, 0, 400, 240), StackedScreenshot.sourceRect(400, 480, wide))
    }

    @Test
    fun `a landscape slot leaves other images whole for its center crop`() {
        assertNull(StackedScreenshot.sourceRect(1920, 1080, wide))
        assertNull(StackedScreenshot.sourceRect(224, 288, wide))
    }

    @Test
    fun `a portrait or square slot keeps a stacked frame whole`() {
        assertNull(StackedScreenshot.sourceRect(256, 384, 1f))
        assertNull(StackedScreenshot.sourceRect(256, 384, 9f / 16f))
    }

    @Test
    fun `a square slot center-crops a landscape image to a square`() {
        assertEquals(IntRect(420, 0, 1500, 1080), StackedScreenshot.sourceRect(1920, 1080, 1f))
    }

    @Test
    fun `a square slot center-crops a tall single-screen image to a square`() {
        assertEquals(IntRect(0, 32, 224, 256), StackedScreenshot.sourceRect(224, 288, 1f))
    }

    @Test
    fun `an image already the slot's shape is left whole`() {
        assertNull(StackedScreenshot.sourceRect(256, 256, 1f))
    }
}
