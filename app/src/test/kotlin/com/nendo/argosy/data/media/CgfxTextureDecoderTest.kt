package com.nendo.argosy.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Pins the ETC1 bit layout the banner decoder depends on.
 *
 * A CTR block is index-half first (bytes 0..3) and colour-half second (bytes 4..7), and upstream
 * ETC1 places pixel (x, y)'s 2-bit selector with its low bit at bit `k` and its high bit at bit
 * `k + 16` of the index half, where `k = y + 4 * x`. Reading the selectors MSB-first instead leaves every block's base colour correct
 * while scrambling its sixteen pixels, which is the confetti this shipped with.
 *
 * Pixels are asserted by 8x8 tile coordinates: index = y * 8 + x.
 */
class CgfxTextureDecoderTest {

    private val midGrey = 0x88

    /** One individual-mode block: all six base colours 0x8, both table codewords 0, flip and diff clear. */
    private fun greyBlock(selectors: IntArray): ByteArray {
        require(selectors.size == 16) { "one selector per pixel" }
        val colour = 0x88888800.toInt()
        val indexBits = IntArray(32)
        selectors.forEachIndexed { k, selector ->
            indexBits[k] = selector and 0x1
            indexBits[k + 16] = (selector shr 1) and 0x1
        }
        var low = 0
        for (bit in 0 until 32) low = low or (indexBits[bit] shl bit)
        return ByteArray(8) { index ->
            if (index < 4) {
                ((low ushr (24 - index * 8)) and 0xFF).toByte()
            } else {
                ((colour ushr ((index - 4) * 8)) and 0xFF).toByte()
            }
        }
    }

    /** The decoder works on whole 8x8 tiles, so the block under test sits at the tile's origin. */
    private fun tile(block: ByteArray): ByteArray {
        require(block.size == 8) { "one block" }
        val filler = greyBlock(IntArray(16))
        return block + filler + filler + filler
    }

    private fun decode(selectors: IntArray): IntArray {
        val pixels = CgfxTextureDecoder.decodePixels(
            tile(greyBlock(selectors)),
            8,
            8,
            CgfxTextureDecoder.FORMAT_ETC1,
        )
        assertNotNull("an 8x8 ETC1 tile should decode", pixels)
        return requireNotNull(pixels)
    }

    private fun greyAt(pixels: IntArray, x: Int, y: Int): Int = pixels[y * 8 + x] and 0xFF

    @Test
    fun `selector 0 applies the small positive modifier`() {
        val pixels = decode(IntArray(16))
        // 4-bit 0x8 expands to 0x88; table 0 selector 0 is +2.
        assertEquals(midGrey + 2, greyAt(pixels, 0, 0))
    }

    @Test
    fun `selector 3 applies the large negative modifier`() {
        val pixels = decode(IntArray(16).also { it[0] = 3 })
        assertEquals(midGrey - 8, greyAt(pixels, 0, 0))
        assertEquals(midGrey + 2, greyAt(pixels, 1, 0))
    }

    @Test
    fun `selectors land on the pixel the upstream bit layout names`() {
        // k = y + 4x, so index 1 is pixel (0, 1). Reading the selectors in the transposed order
        // would have painted (1, 0) instead, which is the mistake that shipped as confetti.
        val pixels = decode(IntArray(16).also { it[1] = 3 })
        assertEquals(midGrey - 8, greyAt(pixels, 0, 1))
        assertEquals(midGrey + 2, greyAt(pixels, 1, 0))
    }

    @Test
    fun `selector 1 applies the large positive modifier`() {
        // k = 15 is pixel (3, 3), which with flip clear sits in the second subblock (x >= 2).
        val pixels = decode(IntArray(16).also { it[15] = 1 })
        assertEquals(midGrey + 8, greyAt(pixels, 3, 3))
    }
}
