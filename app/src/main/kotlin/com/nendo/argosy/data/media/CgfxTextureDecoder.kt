package com.nendo.argosy.data.media

/**
 * Pixel decoding for the CTR ("CGFX" TXOB) texture formats, per 3dbrew's CGFX texture format table.
 *
 * Every CTR texture is stored as 8x8 tiles whose pixels run in Morton order; the 4x4-block
 * compressed formats pack four blocks into an 8x8 tile. Sizes are derived from the format rather
 * than assumed, which is what lets [CgfxTextureWalker] accept a texture only when the container's
 * declared byte count agrees with its declared dimensions. That agreement is the difference
 * between a real banner and a run of bytes that merely happens to be opaque.
 */
internal object CgfxTextureDecoder {

    const val FORMAT_RGBA8 = 0x0
    const val FORMAT_RGB8 = 0x1
    const val FORMAT_RGBA5551 = 0x2
    const val FORMAT_RGB565 = 0x3
    const val FORMAT_RGBA4 = 0x4
    const val FORMAT_LA8 = 0x5
    const val FORMAT_HILO8 = 0x6
    const val FORMAT_L8 = 0x7
    const val FORMAT_A8 = 0x8
    const val FORMAT_LA4 = 0x9
    const val FORMAT_L4 = 0xA
    const val FORMAT_A4 = 0xB
    const val FORMAT_ETC1 = 0xC
    const val FORMAT_ETC1A4 = 0xD

    /** ETC1 modifier table: row = table codeword, column = the pixel's 2-bit selector. */
    private val MODIFIERS: Array<IntArray> = arrayOf(
        intArrayOf(2, 8, -2, -8),
        intArrayOf(5, 17, -5, -17),
        intArrayOf(9, 29, -9, -29),
        intArrayOf(13, 42, -13, -42),
        intArrayOf(18, 60, -18, -60),
        intArrayOf(24, 80, -24, -80),
        intArrayOf(33, 106, -33, -106),
        intArrayOf(47, 183, -47, -183),
    )

    fun bitsPerPixel(format: Int): Int? = when (format) {
        FORMAT_RGBA8 -> 32
        FORMAT_RGB8 -> 24
        FORMAT_RGBA5551, FORMAT_RGB565, FORMAT_RGBA4, FORMAT_LA8, FORMAT_HILO8 -> 16
        FORMAT_L8, FORMAT_A8, FORMAT_LA4 -> 8
        FORMAT_L4, FORMAT_A4, FORMAT_ETC1 -> 4
        FORMAT_ETC1A4 -> 8
        else -> null
    }

    fun formatName(format: Int): String = when (format) {
        FORMAT_RGBA8 -> "RGBA8"
        FORMAT_RGB8 -> "RGB8"
        FORMAT_RGBA5551 -> "RGBA5551"
        FORMAT_RGB565 -> "RGB565"
        FORMAT_RGBA4 -> "RGBA4"
        FORMAT_LA8 -> "LA8"
        FORMAT_HILO8 -> "HILO8"
        FORMAT_L8 -> "L8"
        FORMAT_A8 -> "A8"
        FORMAT_LA4 -> "LA4"
        FORMAT_L4 -> "L4"
        FORMAT_A4 -> "A4"
        FORMAT_ETC1 -> "ETC1"
        FORMAT_ETC1A4 -> "ETC1A4"
        else -> "UNKNOWN(0x${format.toString(16)})"
    }

    /** HILO8 is two opaque channels, not a colour; there is nothing to show for it. */
    fun isDecodable(format: Int): Boolean = when (format) {
        FORMAT_HILO8 -> false
        else -> bitsPerPixel(format) != null
    }

    fun expectedByteSize(format: Int, width: Int, height: Int): Int? {
        val bitsPerPixel = bitsPerPixel(format) ?: return null
        if (width <= 0 || height <= 0) return null
        val bits = width.toLong() * height.toLong() * bitsPerPixel
        if (bits % 8L != 0L) return null
        val bytes = bits / 8L
        return if (bytes in 1..Int.MAX_VALUE.toLong()) bytes.toInt() else null
    }

    fun decode(source: ByteArray, texture: CgfxTexture): IntArray? {
        if (!isDecodable(texture.format)) return null
        val expected = expectedByteSize(texture.format, texture.width, texture.height) ?: return null
        if (texture.dataOffset < 0 || texture.dataOffset > source.size - expected) return null
        if (texture.dataSize < expected) return null
        val bytes = source.copyOfRange(texture.dataOffset, texture.dataOffset + expected)
        return decodePixels(bytes, texture.width, texture.height, texture.format)
    }

    fun decodePixels(bytes: ByteArray, width: Int, height: Int, format: Int): IntArray? {
        if (!isDecodable(format) || width <= 0 || height <= 0) return null
        if (width % 8 != 0 || height % 8 != 0) return null
        val expected = expectedByteSize(format, width, height) ?: return null
        if (bytes.size < expected) return null
        return when (format) {
            FORMAT_ETC1 -> decodeEtc(bytes, width, height, withAlpha = false)
            FORMAT_ETC1A4 -> decodeEtc(bytes, width, height, withAlpha = true)
            else -> decodeTiled(bytes, width, height, format)
        }
    }

    private fun decodeTiled(bytes: ByteArray, width: Int, height: Int, format: Int): IntArray {
        val bitsPerPixel = requireNotNull(bitsPerPixel(format))
        val bytesPerPixel = bitsPerPixel / 8
        val out = IntArray(width * height)
        val tilesPerRow = width / 8
        val tileByteSize = 64 * bitsPerPixel / 8
        for (tileY in 0 until height / 8) {
            for (tileX in 0 until tilesPerRow) {
                val tileBase = (tileY * tilesPerRow + tileX) * tileByteSize
                for (y in 0 until 8) {
                    for (x in 0 until 8) {
                        val slot = TiledPixelDecoder.mortonOffset(x, y)
                        val pixelIndex = (tileY * 8 + y) * width + tileX * 8 + x
                        out[pixelIndex] = when (bytesPerPixel) {
                            4 -> rgba8(bytes, tileBase + slot * 4)
                            3 -> rgb8(bytes, tileBase + slot * 3)
                            2 -> packed16(bytes, tileBase + slot * 2, format)
                            1 -> packed8(bytes, tileBase + slot, format)
                            else -> packed4(bytes, tileBase + slot / 2, slot and 1, format)
                        }
                    }
                }
            }
        }
        return out
    }

    /**
     * ETC1 blocks, and ETC1A4 where each block is the colour block followed by a 4-bit alpha plane.
     * Both are stored as 8x8 tiles holding four 4x4 blocks, left to right then top to bottom.
     */
    private fun decodeEtc(bytes: ByteArray, width: Int, height: Int, withAlpha: Boolean): IntArray {
        val out = IntArray(width * height)
        val tilesPerRow = width / 8
        val blockBytes = if (withAlpha) 16 else 8
        val tileBytes = blockBytes * 4
        for (tileY in 0 until height / 8) {
            for (tileX in 0 until tilesPerRow) {
                val tileBase = (tileY * tilesPerRow + tileX) * tileBytes
                for (block in 0 until 4) {
                    val blockX = tileX * 8 + (block and 1) * 4
                    val blockY = tileY * 8 + (block shr 1) * 4
                    decodeEtcBlock(
                        bytes = bytes,
                        offset = tileBase + block * blockBytes,
                        out = out,
                        width = width,
                        destX = blockX,
                        destY = blockY,
                        withAlpha = withAlpha,
                    )
                }
            }
        }
        return out
    }

    private fun decodeEtcBlock(
        bytes: ByteArray,
        offset: Int,
        out: IntArray,
        width: Int,
        destX: Int,
        destY: Int,
        withAlpha: Boolean,
    ) {
        if (offset + (if (withAlpha) 16 else 8) > bytes.size) return
        // CTR stores the two 32-bit halves of an ETC1 block index-half first: bytes 0..3 are the
        // per-pixel selector bits, bytes 4..7 the colour word. Upstream ETC1 - the Khronos layout as
        // implemented in Android's etc1.cpp - reads that colour word big-endian, takes diff from bit
        // 1, the two table codewords from bits 5..7 and 2..4, flip from bit 0, and pulls each pixel's
        // 2-bit selector from bit k and bit k + 15 of the index half where k = y + 4x. Reading the
        // selectors MSB-first instead leaves a block's base colour right and randomises its sixteen
        // pixels, which is the confetti this used to draw. The colour word itself is little-endian
        // in CTR data, and an ETC1A4 block carries its 4-bit alpha plane ahead of the colour half.
        // CTR stores each ETC1 block as one little-endian 64-bit word (both halves little-endian):
        // the low half holds the per-pixel selectors, the high half the colours and control bits.
        // An ETC1A4 block carries its 4-bit alpha plane ahead of the colour block.
        val blockBase = offset + if (withAlpha) 8 else 0
        val low = readU32Le(bytes, blockBase)
        val high = readU32Le(bytes, blockBase + 4)

        val tableIndexA = (high ushr 5) and 7
        val tableIndexB = (high ushr 2) and 7
        val flipped = (high and 1) != 0
        val first: IntArray
        val second: IntArray
        if (((high ushr 1) and 1) != 0) {
            // Differential mode: 5-bit base colour plus a signed 3-bit delta for the second half.
            val red1 = (high ushr 27) and 0x1F
            val green1 = (high ushr 19) and 0x1F
            val blue1 = (high ushr 11) and 0x1F
            val red2 = (red1 + signExtend3((high ushr 24) and 7)).coerceIn(0, 31)
            val green2 = (green1 + signExtend3((high ushr 16) and 7)).coerceIn(0, 31)
            val blue2 = (blue1 + signExtend3((high ushr 8) and 7)).coerceIn(0, 31)
            first = intArrayOf(expand5(red1), expand5(green1), expand5(blue1))
            second = intArrayOf(expand5(red2), expand5(green2), expand5(blue2))
        } else {
            // Individual mode: two 4-bit colours.
            first = intArrayOf(((high ushr 28) and 0xF) * 17, ((high ushr 20) and 0xF) * 17, ((high ushr 12) and 0xF) * 17)
            second = intArrayOf(((high ushr 24) and 0xF) * 17, ((high ushr 16) and 0xF) * 17, ((high ushr 8) and 0xF) * 17)
        }

        val tableA = MODIFIERS[tableIndexA]
        val tableB = MODIFIERS[tableIndexB]
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                val secondSubblock = if (flipped) y >= 2 else x >= 2
                // Upstream writes `(low >> (k + 15)) & 2`, whose mask takes the second bit of the
                // shifted word: the low bit of each selector lives in bits 0..15 and its high bit
                // in bits 16..31, one per pixel.
                val bitIndex = y + 4 * x
                val selector = ((low ushr bitIndex) and 1) or (((low ushr (bitIndex + 16)) and 1) shl 1)
                val base = if (secondSubblock) second else first
                val modifier = (if (secondSubblock) tableB else tableA)[selector]
                // The alpha plane runs column-major like the selector bits: index = x * 4 + y.
                val pixelIndex = x * 4 + y
                val alpha = if (withAlpha) {
                    val packed = bytes[offset + pixelIndex / 2].toInt() and 0xFF
                    val nibble = if (pixelIndex and 1 == 0) packed and 0xF else (packed shr 4) and 0xF
                    nibble * 17
                } else {
                    255
                }
                out[(destY + y) * width + destX + x] = argb(
                    alpha,
                    base[0] + modifier,
                    base[1] + modifier,
                    base[2] + modifier,
                )
            }
        }
    }

    /** CTR stores RGBA8 texels as A, B, G, R bytes. */
    private fun rgba8(bytes: ByteArray, offset: Int) = argb(
        bytes[offset].toInt() and 0xFF,
        bytes[offset + 3].toInt() and 0xFF,
        bytes[offset + 2].toInt() and 0xFF,
        bytes[offset + 1].toInt() and 0xFF,
    )

    /** CTR stores RGB8 texels as B, G, R bytes. */
    private fun rgb8(bytes: ByteArray, offset: Int) = argb(
        255,
        bytes[offset + 2].toInt() and 0xFF,
        bytes[offset + 1].toInt() and 0xFF,
        bytes[offset].toInt() and 0xFF,
    )

    private fun packed16(bytes: ByteArray, offset: Int, format: Int): Int {
        val raw = (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
        return when (format) {
            FORMAT_RGBA5551 -> argb(
                if (raw and 0x1 != 0) 255 else 0,
                expand5((raw shr 11) and 0x1F),
                expand5((raw shr 6) and 0x1F),
                expand5((raw shr 1) and 0x1F),
            )
            FORMAT_RGB565 -> argb(
                255,
                expand5((raw shr 11) and 0x1F),
                expand6((raw shr 5) and 0x3F),
                expand5(raw and 0x1F),
            )
            FORMAT_RGBA4 -> argb(
                expand4(raw and 0xF),
                expand4((raw shr 12) and 0xF),
                expand4((raw shr 8) and 0xF),
                expand4((raw shr 4) and 0xF),
            )
            FORMAT_LA8 -> argb(
                bytes[offset + 1].toInt() and 0xFF,
                bytes[offset].toInt() and 0xFF,
                bytes[offset].toInt() and 0xFF,
                bytes[offset].toInt() and 0xFF,
            )
            else -> argb(255, bytes[offset].toInt() and 0xFF, bytes[offset].toInt() and 0xFF, bytes[offset].toInt() and 0xFF)
        }
    }

    private fun packed8(bytes: ByteArray, offset: Int, format: Int): Int {
        val value = bytes[offset].toInt() and 0xFF
        return when (format) {
            FORMAT_A8 -> argb(value, 255, 255, 255)
            FORMAT_LA4 -> argb(
                expand4(value and 0xF),
                expand4((value shr 4) and 0xF),
                expand4((value shr 4) and 0xF),
                expand4((value shr 4) and 0xF),
            )
            else -> argb(255, value, value, value)
        }
    }

    private fun packed4(bytes: ByteArray, offset: Int, slot: Int, format: Int): Int {
        val value = bytes[offset].toInt() and 0xFF
        val nibble = if (slot and 1 == 0) value and 0xF else (value shr 4) and 0xF
        val grey = expand4(nibble)
        return when (format) {
            FORMAT_A4 -> argb(grey, 255, 255, 255)
            else -> argb(255, grey, grey, grey)
        }
    }

    private fun signExtend3(value: Int): Int = if (value >= 4) value - 8 else value

    private fun expand4(value: Int): Int = (value and 0xF) * 17

    private fun expand5(value: Int): Int {
        val clamped = value and 0x1F
        return (clamped shl 3) or (clamped shr 2)
    }

    private fun expand6(value: Int): Int {
        val clamped = value and 0x3F
        return (clamped shl 2) or (clamped shr 4)
    }

    private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
        ((alpha.coerceIn(0, 255)) shl 24) or
            ((red.coerceIn(0, 255)) shl 16) or
            ((green.coerceIn(0, 255)) shl 8) or
            blue.coerceIn(0, 255)

    /** The selector half is read big-endian: its first pixel's bits are the most significant. */
    private fun readU32Be(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun readU32Le(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
}
