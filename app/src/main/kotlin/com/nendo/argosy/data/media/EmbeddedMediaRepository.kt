package com.nendo.argosy.data.media

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "EmbeddedMediaRepository"

@Singleton
class EmbeddedMediaRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val mediaManager: EmbeddedMediaManager
) {

    private val mediaRoot: File
        get() = File(context.filesDir, "extracted_media").apply { mkdirs() }

    fun getCachedIconFile(gameId: String): File? {
        val file = File(mediaRoot, "$gameId/icon.webp")
        return if (file.isFile) file else null
    }

    suspend fun extractAndCacheForGame(gameId: String, romPath: String): File? = withContext(Dispatchers.IO) {
        val result = mediaManager.extractForGame(romPath)
        if (!result.success || result.iconBitmap == null) {
            Log.w(TAG, "Extraction failed for game $gameId: ${result.message}")
            return@withContext null
        }

        val gameDir = File(mediaRoot, gameId).apply { mkdirs() }
        val iconFile = File(gameDir, "icon.webp")

        runCatching {
            FileOutputStream(iconFile).use { out ->
                val format = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSLESS
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                result.iconBitmap.compress(format, 100, out)
            }
            Log.d(TAG, "Cached extracted icon to ${iconFile.absolutePath}")
            iconFile
        }.getOrNull()
    }
}
