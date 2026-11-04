package com.nendo.argosy.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ThreeDsBannerRendererTest {

    @Test
    fun `renders bannertool image CGFX pixels in tiled order`() {
        val banner = requireNotNull(
            javaClass.getResourceAsStream(
                "/com/nendo/argosy/data/media/argosy-banner-fixture.bnr"
            )
        ).use { it.readBytes() }
        val parseResult = ThreeDsBannerParser.parse(banner, Locale.US)

        assertTrue(parseResult.toString(), parseResult is ThreeDsBannerResult.Success)
        val cgfx = (parseResult as ThreeDsBannerResult.Success).cgfx
        assertEquals(0x11580, cgfx.size)

        val renderResult = ThreeDsBannerRenderer.render(cgfx)

        assertTrue(renderResult.toString(), renderResult is ThreeDsBannerRenderResult.Success)
        val image = renderResult as ThreeDsBannerRenderResult.Success
        assertEquals(256, image.width)
        assertEquals(128, image.height)
        assertEquals(0xFFFFBB44.toInt(), image.argb[70 * image.width + 80])
        assertEquals(0xFFEEEEFF.toInt(), image.argb[105 * image.width + 32])
    }

    @Test
    fun `rejects CGFX outside the supported image-banner template`() {
        val result = ThreeDsBannerRenderer.render(ByteArray(0x100).also {
            "CGFX".toByteArray(Charsets.US_ASCII).copyInto(it)
        })

        assertTrue(result is ThreeDsBannerRenderResult.Failure)
        assertEquals(
            "CGFX does not match the supported 3DS image-banner template",
            (result as ThreeDsBannerRenderResult.Failure).reason
        )
    }
}
