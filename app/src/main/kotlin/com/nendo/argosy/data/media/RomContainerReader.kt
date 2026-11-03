package com.nendo.argosy.data.media

import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RomContainerReader"

/**
 * Inspects and reads container structures from ROM files (Nintendo DS, Nintendo 3DS, etc.)
 * for native embedded media extraction.
 */
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
        val titleId: String? = null
    )

    fun inspect(file: File): InspectionResult {
        if (!file.isFile || file.length() < 512) {
            return InspectionResult(RomPlatform.UNKNOWN, false, false)
        }
        return RandomAccessFile(file, "r").use { raf ->
            val magic = bytearrayOf(4)
            raf.seek(0x100)
            raf.read(magic)

            // Check for Nintendo 3DS NCSD magic "NCSD" at 0x100
            if (magic.contentEquals(byteArrayOf('N'.code.toByte(), 'C'.code.toByte(), 'S'.code.toByte(), 'D'.code.toByte()))) {
                Log.d(TAG, "Detected 3DS NCSD container: ${file.name}")
                return@use InspectionResult(RomPlatform.THREEDS, isValid = true, isEncrypted = false)
            }

            // Check for Nintendo DS header magic or banner offset at 0x68
            raf.seek(0x68)
            val bannerOffset = readLe32(raf)
            if (bannerOffset in 0x200..file.length() - 2112) {
                raf.seek(0xC0) // Nintendo DS game title / header check
                val dsMagic = ByteArray(4)
                raf.read(dsMagic)
                Log.d(TAG, "Detected Nintendo DS ROM: ${file.name} at bannerOffset=$bannerOffset")
                return@use InspectionResult(RomPlatform.NDS, isValid = true, isEncrypted = false, bannerOffset = bannerOffset.toLong())
            }

            InspectionResult(RomPlatform.UNKNOWN, false, false)
        }
    }

    private fun readLe32(raf: RandomAccessFile): Int {
        val b0 = raf.read()
        val b1 = raf.read()
        val b2 = raf.read()
        val b3 = raf.read()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    private fun bytearrayOf(size: Int) = ByteArray(size)
}
