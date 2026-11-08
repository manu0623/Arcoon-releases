package com.nendo.argosy.data.media

import java.util.Locale

private const val CBMD_HEADER_SIZE = 0x88
private const val CBMD_COMMON_CGFX_OFFSET = 0x08
private const val CBMD_REGIONAL_CGFX_OFFSET = 0x0C
private const val CBMD_REGIONAL_CGFX_COUNT = 13
private const val CBMD_CWAV_OFFSET = 0x84
private const val CGFX_MAX_DECOMPRESSED_SIZE = 0x80000
private const val CBMD_JINGLE_MAX_SIZE = 4 * 1024 * 1024
private const val CWAV_HEADER_SIZE = 0x40

sealed interface ThreeDsBannerResult {
    /**
     * [cgfx] is the container for the system language (or the common one when the banner has no
     * language-specific scene). [common] is the shared scene when the language container is only
     * an overlay on it (usually just the localised logo); the Home Menu draws both together.
     */
    data class Success(val cgfx: ByteArray, val common: ByteArray? = null) : ThreeDsBannerResult
    data class Failure(val reason: String) : ThreeDsBannerResult
}

object ThreeDsBannerParser {

    fun parse(
        banner: ByteArray,
        locale: Locale = Locale.getDefault()
    ): ThreeDsBannerResult = parse(banner.size.toLong(), locale) { offset, size ->
        if (offset < 0L || size < 0 || offset > banner.size || size.toLong() > banner.size - offset) {
            null
        } else {
            banner.copyOfRange(offset.toInt(), offset.toInt() + size)
        }
    }

    fun parse(
        bannerSize: Long,
        locale: Locale = Locale.getDefault(),
        readAt: (offset: Long, size: Int) -> ByteArray?
    ): ThreeDsBannerResult {
        if (bannerSize < CBMD_HEADER_SIZE) {
            return ThreeDsBannerResult.Failure("3DS banner header is truncated")
        }
        val header = readAt(0L, CBMD_HEADER_SIZE)
            ?.takeIf { it.size == CBMD_HEADER_SIZE }
            ?: return ThreeDsBannerResult.Failure("Could not read 3DS banner header")
        if (!header.copyOfRange(0, 4).contentEquals("CBMD".toByteArray(Charsets.US_ASCII))) {
            return ThreeDsBannerResult.Failure("ExeFS banner is not a CBMD resource")
        }

        val commonOffset = readU32(header, CBMD_COMMON_CGFX_OFFSET)
        val selectedOffset = preferredRegionIndex(locale)
            ?.let { readU32(header, CBMD_REGIONAL_CGFX_OFFSET + it * 4) }
            ?.takeIf { it > 0L }
            ?: commonOffset
        val selected = when (val read = readCgfx(header, bannerSize, selectedOffset, readAt)) {
            is ThreeDsBannerResult.Success -> read.cgfx
            else -> return read
        }
        val common = if (selectedOffset != commonOffset) {
            (readCgfx(header, bannerSize, commonOffset, readAt) as? ThreeDsBannerResult.Success)?.cgfx
        } else {
            null
        }
        return ThreeDsBannerResult.Success(selected, common)
    }

    private fun readCgfx(
        header: ByteArray,
        bannerSize: Long,
        selectedOffset: Long,
        readAt: (offset: Long, size: Int) -> ByteArray?
    ): ThreeDsBannerResult {
        if (selectedOffset < CBMD_HEADER_SIZE || selectedOffset >= bannerSize) {
            return ThreeDsBannerResult.Failure("3DS banner does not contain a valid CGFX resource")
        }

        val cwavOffset = readU32(header, CBMD_CWAV_OFFSET)
        val nextResourceOffset = buildList {
            add(readU32(header, CBMD_COMMON_CGFX_OFFSET).takeIf { it > selectedOffset && it < bannerSize } ?: bannerSize)
            for (index in 0 until CBMD_REGIONAL_CGFX_COUNT) {
                val offset = readU32(header, CBMD_REGIONAL_CGFX_OFFSET + index * 4)
                if (offset > selectedOffset && offset < bannerSize) add(offset)
            }
            if (cwavOffset > selectedOffset && cwavOffset < bannerSize) add(cwavOffset)
            add(bannerSize)
        }.minOrNull() ?: bannerSize
        val availableSize = nextResourceOffset - selectedOffset
        if (availableSize < 4L) {
            return ThreeDsBannerResult.Failure("3DS banner CGFX resource is truncated")
        }

        val sizeHeader = readAt(selectedOffset, minOf(8L, availableSize).toInt())
            ?: return ThreeDsBannerResult.Failure("Could not read compressed CGFX header")
        if (sizeHeader.size < 4 || sizeHeader[0] != 0x11.toByte()) {
            return ThreeDsBannerResult.Failure("3DS banner CGFX is not LZ11-compressed")
        }
        val decompressedSize = lz11OutputSize(sizeHeader)
            ?: return ThreeDsBannerResult.Failure("3DS banner CGFX has an invalid LZ11 header")
        if (decompressedSize !in 4..CGFX_MAX_DECOMPRESSED_SIZE) {
            return ThreeDsBannerResult.Failure("3DS banner CGFX exceeds the supported size")
        }

        val maxCompressedSize = decompressedSize + (decompressedSize + 7) / 8 + 8
        val compressedSize = minOf(availableSize, maxCompressedSize.toLong()).toInt()
        val compressed = readAt(selectedOffset, compressedSize)
            ?.takeIf { it.size == compressedSize }
            ?: return ThreeDsBannerResult.Failure("Could not read compressed 3DS banner CGFX")
        val cgfx = Lz11Decoder.decompress(compressed)
            ?: return ThreeDsBannerResult.Failure("3DS banner CGFX has invalid LZ11 data")
        if (!cgfx.copyOfRange(0, 4).contentEquals("CGFX".toByteArray(Charsets.US_ASCII))) {
            return ThreeDsBannerResult.Failure("Decompressed 3DS banner resource is not CGFX")
        }
        return ThreeDsBannerResult.Success(cgfx)
    }

    /**
     * Extracts the banner jingle as PCM WAV, or null when the banner carries no CWAV.
     *
     * CBMD stores one CWAV for every region at the offset held at 0x84, after every CGFX, which is
     * why it is bounded by the file end rather than by another resource offset.
     */
    fun extractJingle(banner: ByteArray): BcwavResult? {
        if (banner.size < CBMD_HEADER_SIZE) return null
        if (!banner.copyOfRange(0, 4).contentEquals("CBMD".toByteArray(Charsets.US_ASCII))) return null
        val cwavOffset = readU32(banner, CBMD_CWAV_OFFSET)
        if (cwavOffset < CBMD_HEADER_SIZE || cwavOffset >= banner.size) return null
        val available = banner.size - cwavOffset
        if (available < CWAV_HEADER_SIZE || available > CBMD_JINGLE_MAX_SIZE) return null
        val cwav = banner.copyOfRange(cwavOffset.toInt(), banner.size)
        return BcwavDecoder.toWav(cwav)
    }

    private fun preferredRegionIndex(locale: Locale): Int? {
        val country = locale.country.uppercase()
        val language = locale.language.lowercase()
        return when {
            country == "JP" -> if (language == "ja") 8 else null
            country in setOf("US", "CA", "MX", "BR") -> when (language) {
                "en" -> 9
                "fr" -> 10
                "es" -> 11
                "pt" -> 12
                else -> null
            }
            else -> when (language) {
                "en" -> 0
                "fr" -> 1
                "de" -> 2
                "it" -> 3
                "es" -> 4
                "nl" -> 5
                "pt" -> 6
                "ru" -> 7
                else -> null
            }
        }
    }

    private fun lz11OutputSize(header: ByteArray): Int? {
        val shortSize = readU24(header, 1)
        if (shortSize != 0) return shortSize
        if (header.size < 8) return null
        val extendedSize = readU32(header, 4)
        return extendedSize.takeIf { it <= Int.MAX_VALUE }?.toInt()
    }

    private fun readU24(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16)

    private fun readU32(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xFF) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFF) shl 24)
}

private object Lz11Decoder {

    fun decompress(input: ByteArray): ByteArray? {
        if (input.size < 4 || input[0] != 0x11.toByte()) return null
        var inputOffset = 4
        var outputSize = readU24(input, 1)
        if (outputSize == 0) {
            if (input.size < 8) return null
            val extendedSize = readU32(input, 4)
            if (extendedSize > Int.MAX_VALUE) return null
            outputSize = extendedSize.toInt()
            inputOffset = 8
        }
        if (outputSize !in 4..CGFX_MAX_DECOMPRESSED_SIZE) return null
        val output = ByteArray(outputSize)
        var outputOffset = 0

        while (outputOffset < outputSize) {
            if (inputOffset >= input.size) return null
            val flags = input[inputOffset++].toInt() and 0xFF
            for (bit in 7 downTo 0) {
                if (outputOffset == outputSize) break
                if ((flags and (1 shl bit)) == 0) {
                    if (inputOffset >= input.size) return null
                    output[outputOffset++] = input[inputOffset++]
                    continue
                }

                if (inputOffset >= input.size) return null
                val first = input[inputOffset++].toInt() and 0xFF
                val highNibble = first ushr 4
                val copyLength: Int
                val displacement: Int
                when (highNibble) {
                    0 -> {
                        if (inputOffset + 1 >= input.size) return null
                        val second = input[inputOffset++].toInt() and 0xFF
                        val third = input[inputOffset++].toInt() and 0xFF
                        copyLength = (((first and 0x0F) shl 4) or (second ushr 4)) + 0x11
                        displacement = ((second and 0x0F) shl 8) or third
                    }
                    1 -> {
                        if (inputOffset + 2 >= input.size) return null
                        val second = input[inputOffset++].toInt() and 0xFF
                        val third = input[inputOffset++].toInt() and 0xFF
                        val fourth = input[inputOffset++].toInt() and 0xFF
                        copyLength = (
                            ((first and 0x0F) shl 12) or
                                (second shl 4) or
                                (third ushr 4)
                            ) + 0x111
                        displacement = ((third and 0x0F) shl 8) or fourth
                    }
                    else -> {
                        if (inputOffset >= input.size) return null
                        val second = input[inputOffset++].toInt() and 0xFF
                        copyLength = highNibble + 1
                        displacement = ((first and 0x0F) shl 8) or second
                    }
                }
                val sourceOffset = outputOffset - displacement - 1
                if (copyLength > outputSize - outputOffset || sourceOffset < 0) return null
                repeat(copyLength) {
                    output[outputOffset] = output[sourceOffset + it]
                    outputOffset++
                }
            }
        }
        return output
    }

    private fun readU24(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16)

    private fun readU32(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xFF) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFF) shl 24)
}
