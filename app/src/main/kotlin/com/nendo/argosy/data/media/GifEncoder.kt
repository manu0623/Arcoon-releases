package com.nendo.argosy.data.media

import java.io.ByteArrayOutputStream

/**
 * A minimal GIF89a encoder for the banner rotation.
 *
 * The launcher's image loader already decodes animated GIFs, so a spin can be delivered as one
 * file with no playback code in the UI. Frames are indexed against a shared palette built by median
 * cut, alpha below [TRANSPARENT_ALPHA] becomes the GIF transparency index, and every frame is fully
 * repainted (disposal "restore to background") so a spinning banner leaves no trails.
 */
internal object GifEncoder {

    private const val TRANSPARENT_ALPHA = 128
    private const val MAX_PALETTE_ENTRIES = 255
    private const val MAX_SAMPLES = 200_000
    private const val MAX_DICTIONARY = 4096

    fun encode(frames: List<IntArray>, width: Int, height: Int, delayCentiseconds: Int): ByteArray? =
        encode(frames.size, width, height, delayCentiseconds, frames) { frames[it] }

    /**
     * Encodes [frameCount] frames produced one at a time by [frameAt], so a long animation never
     * holds more than one frame in memory. The shared palette is built from [paletteFrames], a
     * sample spread across the animation.
     */
    fun encode(
        frameCount: Int,
        width: Int,
        height: Int,
        delayCentiseconds: Int,
        paletteFrames: List<IntArray>,
        frameAt: (Int) -> IntArray,
    ): ByteArray? {
        if (frameCount <= 0 || paletteFrames.isEmpty() || width <= 0 || height <= 0) return null
        if (paletteFrames.any { it.size != width * height }) return null

        val palette = buildPalette(paletteFrames)
        val tableSize = tableSizeFor(palette.size + 1)
        val tableBits = Integer.numberOfTrailingZeros(tableSize)
        val indexOf = nearestColorLookup(palette)

        val out = ByteArrayOutputStream()
        writeHeader(out, width, height, tableBits)
        writeGlobalColorTable(out, palette, tableSize)
        writeLoopExtension(out)
        for (frameIndex in 0 until frameCount) {
            val frame = frameAt(frameIndex)
            if (frame.size != width * height) return null
            writeGraphicControl(out, delayCentiseconds)
            writeImageDescriptor(out, width, height)
            val indices = ByteArray(frame.size)
            for (index in frame.indices) {
                val pixel = frame[index]
                indices[index] = if (((pixel ushr 24) and 0xFF) < TRANSPARENT_ALPHA) {
                    0
                } else {
                    // Palette entry 0 is the transparency slot, so colours start at 1.
                    (indexOf(pixel) + 1).toByte()
                }
            }
            writeLzw(out, indices, maxOf(2, tableBits))
        }
        out.write(0x3B)
        return out.toByteArray()
    }

    private fun writeHeader(out: ByteArrayOutputStream, width: Int, height: Int, tableBits: Int) {
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        writeShort(out, width)
        writeShort(out, height)
        // Global colour table present, 8 bits of colour resolution, size = 2^(bits+1).
        out.write(0xF0 or (tableBits - 1))
        out.write(0)
        out.write(0)
    }

    private fun writeGlobalColorTable(out: ByteArrayOutputStream, palette: IntArray, tableSize: Int) {
        // Entry 0 is the transparent slot; the rest are the quantised colours.
        out.write(0)
        out.write(0)
        out.write(0)
        for (entry in palette) {
            out.write((entry shr 16) and 0xFF)
            out.write((entry shr 8) and 0xFF)
            out.write(entry and 0xFF)
        }
        repeat(tableSize - palette.size - 1) {
            out.write(0)
            out.write(0)
            out.write(0)
        }
    }

    private fun writeLoopExtension(out: ByteArrayOutputStream) {
        out.write(0x21)
        out.write(0xFF)
        out.write(0x0B)
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(0x03)
        out.write(0x01)
        writeShort(out, 0)
        out.write(0)
    }

    private fun writeGraphicControl(out: ByteArrayOutputStream, delayCentiseconds: Int) {
        out.write(0x21)
        out.write(0xF9)
        out.write(0x04)
        // Disposal 2 (restore to background) with the transparency flag set.
        out.write(0x09)
        writeShort(out, delayCentiseconds)
        out.write(0)
        out.write(0)
    }

    private fun writeImageDescriptor(out: ByteArrayOutputStream, width: Int, height: Int) {
        out.write(0x2C)
        writeShort(out, 0)
        writeShort(out, 0)
        writeShort(out, width)
        writeShort(out, height)
        out.write(0)
    }

    private fun writeShort(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
    }

    /** GIF LZW with the standard clear/end codes and 9..12 bit code widths. */
    private fun writeLzw(out: ByteArrayOutputStream, indices: ByteArray, minCodeSize: Int) {
        out.write(minCodeSize)
        val clearCode = 1 shl minCodeSize
        val endCode = clearCode + 1
        var nextCode = endCode + 1
        var codeSize = minCodeSize + 1
        val dictionary = HashMap<Int, Int>()
        val block = ByteArrayOutputStream()
        var bitBuffer = 0
        var bitCount = 0

        fun flushBlock() {
            val bytes = block.toByteArray()
            if (bytes.isEmpty()) return
            out.write(bytes.size)
            out.write(bytes, 0, bytes.size)
            block.reset()
        }

        fun emit(code: Int) {
            bitBuffer = bitBuffer or (code shl bitCount)
            bitCount += codeSize
            while (bitCount >= 8) {
                block.write(bitBuffer and 0xFF)
                bitBuffer = bitBuffer ushr 8
                bitCount -= 8
                if (block.size() == 255) flushBlock()
            }
        }

        emit(clearCode)
        var prefix = indices[0].toInt() and 0xFF
        for (index in 1 until indices.size) {
            val next = indices[index].toInt() and 0xFF
            val key = (prefix shl 8) or next
            val existing = dictionary[key]
            if (existing != null) {
                prefix = existing
                continue
            }
            emit(prefix)
            if (nextCode < MAX_DICTIONARY) {
                dictionary[key] = nextCode
                nextCode++
                // Widen once the next assignable code no longer fits. Bumping at the boundary
                // itself (nextCode == 1 shl codeSize) desynchronises the decoder's table.
                if (nextCode > (1 shl codeSize) && codeSize < 12) codeSize++
            } else {
                emit(clearCode)
                dictionary.clear()
                nextCode = endCode + 1
                codeSize = minCodeSize + 1
            }
            prefix = next
        }
        emit(prefix)
        emit(endCode)
        if (bitCount > 0) {
            block.write(bitBuffer and 0xFF)
            if (block.size() == 255) flushBlock()
        }
        flushBlock()
        out.write(0)
    }

    private fun tableSizeFor(entries: Int): Int {
        var size = 2
        while (size < entries) size = size shl 1
        return size.coerceIn(2, 256)
    }

    /**
     * Median cut over a sampled histogram: repeatedly split the widest box at the median of its
     * widest channel. The banner art is a limited palette (green ramp, red and gold logo), so this
     * recovers it closely without dithering noise.
     */
    private fun buildPalette(frames: List<IntArray>): IntArray {
        val histogram = HashMap<Int, Int>()
        val totalPixels = frames.sumOf { it.size }
        val step = maxOf(1, totalPixels / MAX_SAMPLES)
        var counter = 0
        for (frame in frames) {
            for (pixel in frame) {
                counter++
                if (counter % step != 0) continue
                if (((pixel ushr 24) and 0xFF) < TRANSPARENT_ALPHA) continue
                val rgb = pixel and 0xFFFFFF
                histogram[rgb] = (histogram[rgb] ?: 0) + 1
            }
        }
        if (histogram.isEmpty()) return intArrayOf(0)

        var boxes = listOf(histogram.keys.toIntArray() to histogram)
        while (boxes.size < MAX_PALETTE_ENTRIES) {
            var widest = -1
            var widestScore = 0
            var widestChannel = 0
            boxes.forEachIndexed { index, (colors, counts) ->
                for (channel in 0 until 3) {
                    val shift = 16 - channel * 8
                    var low = 255
                    var high = 0
                    for (color in colors) {
                        val value = (color shr shift) and 0xFF
                        if (value < low) low = value
                        if (value > high) high = value
                    }
                    val score = (high - low) * colors.sumOf { counts[it] ?: 0 }
                    if (score > widestScore) {
                        widestScore = score
                        widest = index
                        widestChannel = channel
                    }
                }
            }
            if (widest < 0) break
            val (colors, counts) = boxes[widest]
            if (colors.size < 2) break
            val shift = 16 - widestChannel * 8
            val sorted = colors.sortedBy { (it shr shift) and 0xFF }
            val midpoint = sorted.size / 2
            val first = sorted.subList(0, midpoint).toIntArray()
            val second = sorted.subList(midpoint, sorted.size).toIntArray()
            if (first.isEmpty() || second.isEmpty()) break
            boxes = boxes.toMutableList().apply {
                removeAt(widest)
                add(first to counts)
                add(second to counts)
            }
        }

        return IntArray(boxes.size) { index ->
            val (colors, counts) = boxes[index]
            var red = 0L
            var green = 0L
            var blue = 0L
            var weight = 0L
            for (color in colors) {
                val count = (counts[color] ?: 1).toLong()
                red += ((color shr 16) and 0xFF) * count
                green += ((color shr 8) and 0xFF) * count
                blue += (color and 0xFF) * count
                weight += count
            }
            val divisor = maxOf(1L, weight)
            (((red / divisor).toInt() and 0xFF) shl 16) or
                (((green / divisor).toInt() and 0xFF) shl 8) or
                ((blue / divisor).toInt() and 0xFF)
        }
    }

    /** Nearest palette entry per distinct colour, memoised so the per-pixel search runs once. */
    private fun nearestColorLookup(palette: IntArray): (Int) -> Int {
        val cache = HashMap<Int, Int>()
        return { pixel ->
            val rgb = pixel and 0xFFFFFF
            cache.getOrPut(rgb) {
                var best = 0
                var bestDistance = Int.MAX_VALUE
                for (index in palette.indices) {
                    val entry = palette[index]
                    val dr = ((entry shr 16) and 0xFF) - ((rgb shr 16) and 0xFF)
                    val dg = ((entry shr 8) and 0xFF) - ((rgb shr 8) and 0xFF)
                    val db = (entry and 0xFF) - (rgb and 0xFF)
                    val distance = dr * dr + dg * dg + db * db
                    if (distance < bestDistance) {
                        bestDistance = distance
                        best = index
                    }
                }
                best
            }
        }
    }
}
