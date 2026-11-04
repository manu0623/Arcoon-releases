package com.nendo.argosy.data.media

import java.io.File
import java.io.RandomAccessFile
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val MEDIA_UNIT_SIZE = 0x200L
private const val NCSD_HEADER_OFFSET = 0x100L
private const val NCSD_PARTITION_TABLE_OFFSET = 0x120L
private const val NCCH_HEADER_SIZE = 0x200L
private const val NCCH_MAGIC_OFFSET = 0x100L
private const val NCCH_FLAGS_OFFSET = 0x188L
private const val NCCH_NO_CRYPTO_FLAG = 0x04
private const val NCCH_EXEFS_OFFSET = 0x1A0L
private const val NCCH_EXEFS_SIZE = 0x1A4L
private const val EXEFS_HEADER_SIZE = 0x200L
private const val MAX_3DS_BANNER_BYTES = 4L * 1024 * 1024
private const val SMDH_SIZE = 0x36C0

@Singleton
class RomContainerReader @Inject constructor() {

    enum class RomPlatform {
        NDS,
        THREEDS,
        UNKNOWN
    }

    data class InspectionResult(
        val platform: RomPlatform,
        val isValid: Boolean,
        val isEncrypted: Boolean,
        val bannerOffset: Long = -1L,
        val titleId: String? = null,
        val exefsOffset: Long? = null,
        val exefsSize: Long? = null,
        val failureReason: String? = null
    )

    fun inspect(file: File): InspectionResult {
        if (!file.isFile || file.length() < 0x104L) {
            return invalid(RomPlatform.UNKNOWN, "File is missing or too small")
        }

        return RandomAccessFile(file, "r").use { raf ->
            val magic = readBytes(raf, NCSD_HEADER_OFFSET, 4)
            when {
                magic.contentEquals("NCSD".toByteArray(Charsets.US_ASCII)) -> inspectThreeDs(raf)
                file.length() >= 512L -> inspectNds(raf)
                else -> invalid(RomPlatform.UNKNOWN, "Unrecognized ROM container")
            }
        }
    }

    fun readThreeDsSmdh(file: File, inspection: InspectionResult): SmdhParseResult {
        if (inspection.platform != RomPlatform.THREEDS || !inspection.isValid) {
            return SmdhParseResult.Failure(inspection.failureReason ?: "Invalid 3DS container")
        }
        if (inspection.isEncrypted) {
            return SmdhParseResult.Failure("Encrypted NCCH content is unsupported")
        }
        val exefsOffset = inspection.exefsOffset
            ?: return SmdhParseResult.Failure("NCCH does not contain ExeFS")
        val exefsSize = inspection.exefsSize
            ?: return SmdhParseResult.Failure("NCCH does not contain ExeFS")

        return RandomAccessFile(file, "r").use { raf ->
            if (exefsSize < EXEFS_HEADER_SIZE || !rangeFits(exefsOffset, exefsSize, raf.length())) {
                return@use SmdhParseResult.Failure("ExeFS is outside the ROM bounds")
            }
            val exefsHeader = readBytes(raf, exefsOffset, EXEFS_HEADER_SIZE.toInt())
            val iconEntry = findSmdhEntry(exefsHeader)
                ?: return@use SmdhParseResult.Failure("ExeFS does not contain an SMDH icon")
            val (relativeOffset, size) = iconEntry
            val dataOffset = exefsOffset + EXEFS_HEADER_SIZE + relativeOffset
            if (size != SMDH_SIZE.toLong() ||
                !rangeFits(EXEFS_HEADER_SIZE + relativeOffset, size, exefsSize) ||
                !rangeFits(dataOffset, size, raf.length())
            ) {
                return@use SmdhParseResult.Failure("SMDH icon has an invalid size or offset")
            }

            val smdh = readBytes(raf, dataOffset, SMDH_SIZE)
            SmdhParser.parse(smdh)?.let(SmdhParseResult::Success)
                ?: SmdhParseResult.Failure("ExeFS icon is not a valid SMDH file")
        }
    }

    fun readThreeDsBannerCgfx(
        file: File,
        inspection: InspectionResult,
        locale: Locale = Locale.getDefault()
    ): ThreeDsBannerResult {
        if (inspection.platform != RomPlatform.THREEDS || !inspection.isValid) {
            return ThreeDsBannerResult.Failure(inspection.failureReason ?: "Invalid 3DS container")
        }
        if (inspection.isEncrypted) {
            return ThreeDsBannerResult.Failure("Encrypted NCCH content is unsupported")
        }
        val exefsOffset = inspection.exefsOffset
            ?: return ThreeDsBannerResult.Failure("NCCH does not contain ExeFS")
        val exefsSize = inspection.exefsSize
            ?: return ThreeDsBannerResult.Failure("NCCH does not contain ExeFS")

        return RandomAccessFile(file, "r").use { raf ->
            if (exefsSize < EXEFS_HEADER_SIZE || !rangeFits(exefsOffset, exefsSize, raf.length())) {
                return@use ThreeDsBannerResult.Failure("ExeFS is outside the ROM bounds")
            }
            val exefsHeader = readBytes(raf, exefsOffset, EXEFS_HEADER_SIZE.toInt())
            val bannerEntry = findExeFsEntry(exefsHeader, "banner")
                ?: return@use ThreeDsBannerResult.Failure("ExeFS does not contain a banner resource")
            val (relativeOffset, size) = bannerEntry
            val dataOffset = exefsOffset + EXEFS_HEADER_SIZE + relativeOffset
            if (!rangeFits(EXEFS_HEADER_SIZE + relativeOffset, size, exefsSize) ||
                !rangeFits(dataOffset, size, raf.length())
            ) {
                return@use ThreeDsBannerResult.Failure("3DS banner resource is outside the ROM bounds")
            }
            ThreeDsBannerParser.parse(size, locale) { offset, readSize ->
                if (!rangeFits(offset, readSize.toLong(), size)) {
                    null
                } else {
                    readBytes(raf, dataOffset + offset, readSize)
                }
            }
        }
    }

    /**
     * Reads the ExeFS banner and decodes its CWAV jingle to PCM WAV.
     *
     * Returns null when the ROM carries no banner, no jingle, or a jingle the decoder cannot read,
     * so a banner without audio never blocks the icon and art paths.
     */
    fun readThreeDsBannerJingle(file: File, inspection: InspectionResult): BcwavResult? {
        if (inspection.platform != RomPlatform.THREEDS || !inspection.isValid) return null
        if (inspection.isEncrypted) return null
        val exefsOffset = inspection.exefsOffset ?: return null
        val exefsSize = inspection.exefsSize ?: return null

        return RandomAccessFile(file, "r").use { raf ->
            if (exefsSize < EXEFS_HEADER_SIZE || !rangeFits(exefsOffset, exefsSize, raf.length())) {
                return@use null
            }
            val exefsHeader = readBytes(raf, exefsOffset, EXEFS_HEADER_SIZE.toInt())
            val bannerEntry = findExeFsEntry(exefsHeader, "banner") ?: return@use null
            val (relativeOffset, size) = bannerEntry
            val dataOffset = exefsOffset + EXEFS_HEADER_SIZE + relativeOffset
            if (size <= 0L || size > MAX_3DS_BANNER_BYTES) return@use null
            if (!rangeFits(EXEFS_HEADER_SIZE + relativeOffset, size, exefsSize)) return@use null
            if (!rangeFits(dataOffset, size, raf.length())) return@use null
            ThreeDsBannerParser.extractJingle(readBytes(raf, dataOffset, size.toInt()))
        }
    }

    private fun inspectThreeDs(raf: RandomAccessFile): InspectionResult {
        val fileLength = raf.length()
        if (!rangeFits(NCSD_HEADER_OFFSET, 0x60L, fileLength)) {
            return invalid(RomPlatform.THREEDS, "NCSD header is truncated")
        }
        val imageSize = readU32(raf, 0x104L) * MEDIA_UNIT_SIZE
        if (imageSize < 0x200L) {
            return invalid(RomPlatform.THREEDS, "NCSD image size is invalid")
        }
        val imageBounds = minOf(imageSize, fileLength)

        val partitionOffsetUnits = readU32(raf, NCSD_PARTITION_TABLE_OFFSET)
        val partitionSizeUnits = readU32(raf, NCSD_PARTITION_TABLE_OFFSET + 4)
        val partitionOffset = partitionOffsetUnits * MEDIA_UNIT_SIZE
        val partitionSize = partitionSizeUnits * MEDIA_UNIT_SIZE
        if (partitionSize < NCCH_HEADER_SIZE ||
            !rangeFits(partitionOffset, partitionSize, imageBounds)
        ) {
            return invalid(RomPlatform.THREEDS, "NCSD application partition is invalid")
        }

        val ncchMagic = readBytes(raf, partitionOffset + NCCH_MAGIC_OFFSET, 4)
        if (!ncchMagic.contentEquals("NCCH".toByteArray(Charsets.US_ASCII))) {
            return invalid(RomPlatform.THREEDS, "NCSD application partition is not NCCH")
        }

        val ncchSize = readU32(raf, partitionOffset + 0x104L) * MEDIA_UNIT_SIZE
        if (ncchSize < NCCH_HEADER_SIZE || ncchSize > partitionSize) {
            return invalid(RomPlatform.THREEDS, "NCCH content size is invalid")
        }

        val flags = readBytes(raf, partitionOffset + NCCH_FLAGS_OFFSET, 8)
        val isEncrypted = flags[7].toInt() and NCCH_NO_CRYPTO_FLAG == 0
        if (isEncrypted) {
            return InspectionResult(
                platform = RomPlatform.THREEDS,
                isValid = true,
                isEncrypted = true,
                failureReason = "Encrypted NCCH content is unsupported"
            )
        }

        val exefsOffsetUnits = readU32(raf, partitionOffset + NCCH_EXEFS_OFFSET)
        val exefsSizeUnits = readU32(raf, partitionOffset + NCCH_EXEFS_SIZE)
        val exefsOffsetInNcch = exefsOffsetUnits * MEDIA_UNIT_SIZE
        val exefsSize = exefsSizeUnits * MEDIA_UNIT_SIZE
        if (exefsSize == 0L) {
            return InspectionResult(
                platform = RomPlatform.THREEDS,
                isValid = true,
                isEncrypted = false,
                failureReason = "NCCH does not contain ExeFS"
            )
        }
        if (exefsSize < EXEFS_HEADER_SIZE ||
            exefsOffsetInNcch < NCCH_HEADER_SIZE ||
            !rangeFits(exefsOffsetInNcch, exefsSize, ncchSize)
        ) {
            return invalid(RomPlatform.THREEDS, "NCCH ExeFS range is invalid")
        }

        return InspectionResult(
            platform = RomPlatform.THREEDS,
            isValid = true,
            isEncrypted = false,
            exefsOffset = partitionOffset + exefsOffsetInNcch,
            exefsSize = exefsSize
        )
    }

    private fun inspectNds(raf: RandomAccessFile): InspectionResult {
        val bannerOffset = readU32(raf, 0x68L)
        if (bannerOffset in 0x200L..raf.length() - 2112L) {
            return InspectionResult(
                platform = RomPlatform.NDS,
                isValid = true,
                isEncrypted = false,
                bannerOffset = bannerOffset
            )
        }
        return invalid(RomPlatform.UNKNOWN, "Unrecognized ROM container")
    }

    private fun findSmdhEntry(header: ByteArray): Pair<Long, Long>? {
        return findExeFsEntry(header, "icon")
    }

    private fun findExeFsEntry(header: ByteArray, targetName: String): Pair<Long, Long>? {
        var result: Pair<Long, Long>? = null
        for (index in 0 until 10) {
            val entryOffset = index * 16
            val nameBytes = header.copyOfRange(entryOffset, entryOffset + 8)
            val name = nameBytes.takeWhile { it != 0.toByte() }
                .toByteArray()
                .toString(Charsets.US_ASCII)
            if (name.isEmpty()) continue
            if (name == targetName) {
                if (result != null) return null
                val offset = readU32(header, entryOffset + 8)
                val size = readU32(header, entryOffset + 12)
                result = offset to size
            }
        }
        return result
    }

    private fun readBytes(raf: RandomAccessFile, offset: Long, size: Int): ByteArray =
        ByteArray(size).also {
            raf.seek(offset)
            raf.readFully(it)
        }

    private fun readU32(raf: RandomAccessFile, offset: Long): Long {
        val bytes = readBytes(raf, offset, 4)
        return readU32(bytes, 0)
    }

    private fun readU32(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xFF) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFF) shl 24)

    private fun rangeFits(offset: Long, size: Long, limit: Long): Boolean =
        offset >= 0L && size >= 0L && offset <= limit && size <= limit - offset

    private fun invalid(platform: RomPlatform, reason: String) =
        InspectionResult(platform, isValid = false, isEncrypted = false, failureReason = reason)
}

sealed interface SmdhParseResult {
    data class Success(val media: SmdhMedia) : SmdhParseResult
    data class Failure(val reason: String) : SmdhParseResult
}
