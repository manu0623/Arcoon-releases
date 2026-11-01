package com.nendo.argosy.libretro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DualScreenMatchScaleTest {

    private val thorTop = KnownPanels.THOR[0]
    private val thorBottom = KnownPanels.THOR[1]
    private val ds = 256f / 192f

    @Test
    fun `the Thor's lower DS screen fills its panel width`() {
        val matched = MatchedScreens.of(thorTop, thorBottom, ds, ds)

        assertEquals(PixelSize(1240, 930), matched.secondary)
    }

    @Test
    fun `the Thor's upper DS screen shrinks to the lower screen's physical size`() {
        val matched = MatchedScreens.of(thorTop, thorBottom, ds, ds)

        assertEquals(
            matched.secondary.width / thorBottom.pixelsPerInch,
            matched.primary.width / thorTop.pixelsPerInch,
            0.01f
        )
        assertEquals(
            matched.secondary.height / thorBottom.pixelsPerInch,
            matched.primary.height / thorTop.pixelsPerInch,
            0.01f
        )
        assertTrue(matched.primary.width < 1440)
    }

    @Test
    fun `the upper screen rests on the bottom edge of its view, centred across`() {
        val matched = MatchedScreens.of(thorTop, thorBottom, ds, ds)

        val padding = matched.primaryPadding(1920, 1080)

        assertEquals(1080 - matched.primary.height, padding.top)
        assertEquals(padding.left, padding.right)
        assertEquals((1920 - matched.primary.width).toDouble(), (padding.left * 2).toDouble(), 1.0)
    }

    @Test
    fun `a first panel too small for the matched size is filled instead of overflowed`() {
        val tiny = PanelSpec(widthPx = 640, heightPx = 480, diagonalInches = 2f)

        val matched = MatchedScreens.of(tiny, thorBottom, ds, ds)

        assertTrue(matched.primary.width <= 640 && matched.primary.height <= 480)
    }

    @Test
    fun `a panel is found by its pixel size in either orientation`() {
        assertEquals(thorBottom, KnownPanels.specFor(KnownPanels.THOR, 1080, 1240))
        assertEquals(thorTop, KnownPanels.specFor(KnownPanels.THOR, 1920, 1080))
        assertNull(KnownPanels.specFor(KnownPanels.THOR, 2560, 1440))
    }

    @Test
    fun `only the Thor has known panels`() {
        assertEquals(KnownPanels.THOR, KnownPanels.forModel("AYN Thor"))
        assertTrue(KnownPanels.forModel("Odin 3").isEmpty())
    }
}
