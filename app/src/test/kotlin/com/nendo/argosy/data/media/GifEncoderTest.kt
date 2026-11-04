package com.nendo.argosy.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GifEncoderTest {

    private fun Frame(width: Int, height: Int, argb: Int): IntArray = IntArray(width * height) { argb }

    @Test
    fun `writes a looping gif89a with one control block per frame`() {
        val frames = listOf(
            Frame(16, 16, 0xFFFF0000.toInt()),
            Frame(16, 16, 0xFF00FF00.toInt()),
            Frame(16, 16, 0xFF0000FF.toInt()),
        )

        val gif = requireNotNull(GifEncoder.encode(frames, 16, 16, delayCentiseconds = 7))

        assertEquals("GIF89a", String(gif, 0, 6, Charsets.US_ASCII))
        assertEquals(16, (gif[6].toInt() and 0xFF) or ((gif[7].toInt() and 0xFF) shl 8))
        assertEquals(16, (gif[8].toInt() and 0xFF) or ((gif[9].toInt() and 0xFF) shl 8))
        assertTrue("global colour table flag", (gif[10].toInt() and 0x80) != 0)
        assertTrue(
            "NETSCAPE loop extension",
            String(gif, 0, gif.size, Charsets.ISO_8859_1).contains("NETSCAPE2.0")
        )

        var controlBlocks = 0
        var descriptors = 0
        for (index in 0 until gif.size - 1) {
            if ((gif[index].toInt() and 0xFF) == 0x21 && (gif[index + 1].toInt() and 0xFF) == 0xF9) {
                controlBlocks++
            }
            if ((gif[index].toInt() and 0xFF) == 0x2C) descriptors++
        }
        assertEquals(frames.size, controlBlocks)
        assertTrue("image separators present", descriptors >= frames.size)
        assertEquals(0x3B, gif.last().toInt() and 0xFF)
    }

    @Test
    fun `rejects frames that do not match the declared size`() {
        val gif = GifEncoder.encode(listOf(Frame(8, 8, 0xFF112233.toInt())), width = 16, height = 16, delayCentiseconds = 7)
        assertEquals(null, gif)
    }

    @Test
    fun `encodes a spinning banner as a gif`() {
        // A synthetic two-tone field stands in for the scene: the encoder is what is under test here,
        // and a real CGFX scene is verified against the device.
        val frames = (0 until 4).map { index ->
            IntArray(32 * 24) { pixel -> if ((pixel + index) % 7 == 0) 0xFFFFFFFF.toInt() else 0x80204060.toInt() }
        }
        val gif = GifEncoder.encode(frames, 32, 24, delayCentiseconds = 7)
        assertNotNull(gif)
        requireNotNull(gif)
        var controlBlocks = 0
        for (index in 0 until gif.size - 1) {
            if ((gif[index].toInt() and 0xFF) == 0x21 && (gif[index + 1].toInt() and 0xFF) == 0xF9) {
                controlBlocks++
            }
        }
        assertEquals(frames.size, controlBlocks)
        assertEquals(0x3B, gif.last().toInt() and 0xFF)
    }
}
