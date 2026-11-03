package com.nendo.argosy.data.media

import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "IconExtractor"

@Singleton
class IconExtractor @Inject constructor() {

    /**
     * Extracts a 32x32 NDS icon from the ROM given the banner offset.
     */
    fun extractNdsIcon(romFile: File, bannerOffset: Long): Bitmap? {
        if (bannerOffset < 0 || !romFile.isFile) return null
        return runCatching {
            RandomAccessFile(romFile, "r").use { raf ->
                raf.seek(bannerOffset + 0x20) // NDS icon graphic data (512 bytes: 16 tiles of 8x8)
                val tileData = ByteArray(512)
                raf.read(tileData)

                raf.seek(bannerOffset + 0x120) // 16-color palette (32 bytes: 16 x u16 RGB555)
                val paletteData = ByteArray(32)
                raf.read(paletteData)

                val palette = IntArray(16)
                for (i in 0 until 16) {
                    val color16 = (paletteData[i * 2].toInt() and 0xFF) or ((paletteData[i * 2 + 1].toInt() and 0xFF) shl 8)
                    val r = ((color16 and 0x1F) shl 3).coerceIn(0, 255)
                    val g = (((color16 shr 5) and 0x1F) shl 3).coerceIn(0, 255)
                    val b = (((color16 shr 10) and 0x1F) shl 3).coerceIn(0, 255)
                    palette[i] = android.graphics.Color.rgb(r, g, b)
                }

                val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
                // 4bpp tile rendering (4x4 grid of 8x8 tiles)
                var tileIdx = 0
                for (ty in 0 until 4) {
                    for (tx in 0 until 4) {
                        for (y in 0 until 8) {
                            for (x in 0 until 4) {
                                val byteVal = tileData[tileIdx++].toInt() and 0xFF
                                val colorIdx1 = byteVal and 0x0F
                                val colorIdx2 = (byteVal shr 4) and 0x0F

                                val px1 = tx * 8 + x * 2
                                val px2 = tx * 8 + x * 2 + 1
                                val py = ty * 8 + y

                                if (px1 < 32 && py < 32) {
                                    bitmap.setPixel(px1, py, palette[colorIdx1])
                                }
                                if (px2 < 32 && py < 32) {
                                    bitmap.setPixel(px2, py, palette[colorIdx2])
                                }
                            }
                        }
                    }
                }
                Log.d(TAG, "Successfully extracted NDS icon for ${romFile.name}")
                bitmap
            }
        }.getOrNull()
    }
}
