package com.nendo.argosy.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ThreeDsBannerParserTest {

    @Test
    fun `uses matching regional CGFX and ignores trailing sound data`() {
        val banner = buildBanner(
            cgfxOffsets = mapOf(0 to 0x88, 9 to 0xA0),
            resources = mapOf(
                0x88 to byteArrayOf(0x11, 0x04, 0, 0, 0, 0, 0, 0, 0, 0),
                0xA0 to compressedCgfx()
            ),
            cwavOffset = 0xB0
        )

        val result = ThreeDsBannerParser.parse(banner, Locale.US)

        assertTrue(result.toString(), result is ThreeDsBannerResult.Success)
        assertEquals(
            "CGFX",
            String((result as ThreeDsBannerResult.Success).cgfx, 0, 4, Charsets.US_ASCII)
        )
    }

    @Test
    fun `falls back to common CGFX when localized resource is absent`() {
        val banner = buildBanner(
            cgfxOffsets = emptyMap(),
            resources = mapOf(0x88 to compressedCgfx())
        )

        val result = ThreeDsBannerParser.parse(banner, Locale.FRANCE)

        assertTrue(result is ThreeDsBannerResult.Success)
    }

    @Test
    fun `decompresses LZ11 backward references`() {
        val banner = buildBanner(
            cgfxOffsets = emptyMap(),
            resources = mapOf(
                0x88 to byteArrayOf(
                    0x11, 0x08, 0, 0, 0x08,
                    0x43, 0x47, 0x46, 0x58,
                    0x30, 0x03
                )
            )
        )

        val result = ThreeDsBannerParser.parse(banner)

        assertTrue(result.toString(), result is ThreeDsBannerResult.Success)
        assertEquals(
            "CGFXCGFX",
            String((result as ThreeDsBannerResult.Success).cgfx, Charsets.US_ASCII)
        )
    }

    @Test
    fun `rejects invalid backward reference`() {
        val banner = buildBanner(
            cgfxOffsets = emptyMap(),
            resources = mapOf(0x88 to byteArrayOf(0x11, 0x04, 0, 0, 0x80.toByte(), 0x20, 0))
        )

        val result = ThreeDsBannerParser.parse(banner)

        assertTrue(result is ThreeDsBannerResult.Failure)
        assertEquals(
            "3DS banner CGFX has invalid LZ11 data",
            (result as ThreeDsBannerResult.Failure).reason
        )
    }

    @Test
    fun `rejects CGFX output larger than supported bound`() {
        val banner = buildBanner(
            cgfxOffsets = emptyMap(),
            resources = mapOf(0x88 to byteArrayOf(0x11, 0x01, 0, 0x08))
        )

        val result = ThreeDsBannerParser.parse(banner)

        assertTrue(result is ThreeDsBannerResult.Failure)
        assertEquals(
            "3DS banner CGFX exceeds the supported size",
            (result as ThreeDsBannerResult.Failure).reason
        )
    }

    @Test
    fun `rejects non-CBMD input`() {
        val result = ThreeDsBannerParser.parse(ByteArray(0x100))

        assertEquals(
            ThreeDsBannerResult.Failure("ExeFS banner is not a CBMD resource"),
            result
        )
    }

    @Test
    fun `parses bannertool generated CBMD and CGFX fixture`() {
        val banner = requireNotNull(
            javaClass.getResourceAsStream(
                "/com/nendo/argosy/data/media/argosy-banner-fixture.bnr"
            )
        ).use { it.readBytes() }

        val result = ThreeDsBannerParser.parse(banner, Locale.US)

        assertTrue(result.toString(), result is ThreeDsBannerResult.Success)
        val cgfx = (result as ThreeDsBannerResult.Success).cgfx
        assertTrue(cgfx.size > 0x14)
        assertEquals("CGFX", String(cgfx, 0, 4, Charsets.US_ASCII))
    }

    private fun compressedCgfx(): ByteArray =
        byteArrayOf(0x11, 0x04, 0, 0, 0, 0x43, 0x47, 0x46, 0x58)

    private fun buildBanner(
        cgfxOffsets: Map<Int, Int>,
        resources: Map<Int, ByteArray>,
        cwavOffset: Int = 0
    ): ByteArray {
        val banner = ByteArray(0x100)
        putAscii(banner, 0, "CBMD")
        putU32(banner, 0x08, 0x88)
        cgfxOffsets.forEach { (index, offset) ->
            putU32(banner, 0x0C + index * 4, offset)
        }
        putU32(banner, 0x84, cwavOffset)
        resources.forEach { (offset, bytes) -> bytes.copyInto(banner, offset) }
        return banner
    }

    private fun putAscii(bytes: ByteArray, offset: Int, value: String) {
        value.toByteArray(Charsets.US_ASCII).copyInto(bytes, offset)
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Int) {
        for (byteIndex in 0 until 4) {
            bytes[offset + byteIndex] = (value ushr (byteIndex * 8)).toByte()
        }
    }
}
