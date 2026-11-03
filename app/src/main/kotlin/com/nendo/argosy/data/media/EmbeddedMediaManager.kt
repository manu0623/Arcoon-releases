package com.nendo.argosy.data.media

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "EmbeddedMediaManager"

@Singleton
class EmbeddedMediaManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val containerReader: RomContainerReader,
    private val iconExtractor: IconExtractor
) {

    data class ExtractionResult(
        val success: Boolean,
        val iconBitmap: Bitmap? = null,
        val message: String = ""
    )

    suspend fun extractForGame(romPath: String): ExtractionResult = withContext(Dispatchers.IO) {
        val file = File(romPath)
        if (!file.isFile) {
            return@withContext ExtractionResult(false, message = "ROM file not found: $romPath")
        }

        val inspection = containerReader.inspect(file)
        if (!inspection.isValid) {
            return@withContext ExtractionResult(false, message = "Unsupported or invalid ROM container")
        }

        when (inspection.platform) {
            RomContainerReader.RomPlatform.NDS -> {
                val bitmap = iconExtractor.extractNdsIcon(file, inspection.bannerOffset)
                if (bitmap != null) {
                    ExtractionResult(true, iconBitmap = bitmap, message = "Successfully extracted NDS embedded icon")
                } else {
                    ExtractionResult(false, message = "Failed to parse NDS banner icon")
                }
            }
            RomContainerReader.RomPlatform.THREEDS -> {
                Log.d(TAG, "3DS embedded media extraction requested for ${file.name}")
                ExtractionResult(false, message = "3DS banner extraction pipeline pending CGFX parser integration")
            }
            RomContainerReader.RomPlatform.UNKNOWN -> {
                ExtractionResult(false, message = "Unknown ROM platform format")
            }
        }
    }
}
