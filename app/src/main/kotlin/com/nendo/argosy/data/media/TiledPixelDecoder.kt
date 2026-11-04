package com.nendo.argosy.data.media

internal object TiledPixelDecoder {

    fun decodeRgb565(bytes: ByteArray, width: Int, height: Int): IntArray? =
        decode(bytes, width, height) { color ->
            val red5 = (color shr 11) and 0x1F
            val green6 = (color shr 5) and 0x3F
            val blue5 = color and 0x1F
            val red = (red5 shl 3) or (red5 shr 2)
            val green = (green6 shl 2) or (green6 shr 4)
            val blue = (blue5 shl 3) or (blue5 shr 2)
            (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
        }

    fun decodeRgba4444(bytes: ByteArray, width: Int, height: Int): IntArray? =
        decode(bytes, width, height) { color ->
            val red = ((color shr 12) and 0xF) * 17
            val green = ((color shr 8) and 0xF) * 17
            val blue = ((color shr 4) and 0xF) * 17
            val alpha = (color and 0xF) * 17
            (alpha shl 24) or (red shl 16) or (green shl 8) or blue
        }

    private inline fun decode(
        bytes: ByteArray,
        width: Int,
        height: Int,
        colorToArgb: (Int) -> Int
    ): IntArray? {
        if (width <= 0 || height <= 0 || width % 8 != 0 || height % 8 != 0) return null
        val expectedBytes = width.toLong() * height * 2
        if (expectedBytes != bytes.size.toLong()) return null

        return IntArray(width * height) { pixelIndex ->
            val x = pixelIndex % width
            val y = pixelIndex / width
            val tileIndex = (((y shr 3) * (width shr 3) + (x shr 3)) shl 6) +
                mortonOffset(x, y)
            val byteOffset = tileIndex * 2
            val color = (bytes[byteOffset].toInt() and 0xFF) or
                ((bytes[byteOffset + 1].toInt() and 0xFF) shl 8)
            colorToArgb(color)
        }
    }

    /** Slot of pixel (x, y) inside an 8x8 CTR tile; shared with the CGFX texture decoder. */
    internal fun mortonOffset(x: Int, y: Int): Int =
        (x and 1) or
            ((y and 1) shl 1) or
            ((x and 2) shl 1) or
            ((y and 2) shl 2) or
            ((x and 4) shl 2) or
            ((y and 4) shl 3)
}
