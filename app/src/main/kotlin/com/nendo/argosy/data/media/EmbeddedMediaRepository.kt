package com.nendo.argosy.data.media

import kotlinx.coroutines.sync.withLock
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "EmbeddedMediaRepository"
private const val MEDIA_DIRECTORY = "extracted_media"
private const val METADATA_FILENAME = "metadata.properties"
private const val ICON_FILENAME = "icon.webp"
private const val BANNER_CGFX_FILENAME = "banner.cgfx"
// "scene" names: banners drawn by the full CGFX scene renderer. Renders cached under the old
// names came from the earlier compositor and are redrawn the next time the game is opened.
private const val BANNER_COMMON_CGFX_FILENAME = "banner-common.cgfx"
private const val BANNER_IMAGE_FILENAME = "banner-scene4.png"
// Animated WebP (full colour and alpha); the constant keeps its old name.
private const val BANNER_GIF_FILENAME = "banner-scene4.webp"
private const val JINGLE_FILENAME = "jingle.wav"

@Singleton
class EmbeddedMediaRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val mediaManager: EmbeddedMediaManager
) {

    private val mediaRoot: File
        get() = File(context.filesDir, MEDIA_DIRECTORY).apply { mkdirs() }

    fun getCachedIconFile(gameId: String): File? = cachedFile(gameId, ICON_FILENAME)

    fun getCachedBannerCgfxFile(gameId: String): File? = cachedFile(gameId, BANNER_CGFX_FILENAME)

    fun getCachedBannerImageFile(gameId: String): File? = cachedFile(gameId, BANNER_IMAGE_FILENAME)

    fun getCachedBannerGifFile(gameId: String): File? = cachedFile(gameId, BANNER_GIF_FILENAME)

    /**
     * The banner art to draw: the spinning render when the banner has one, else the still. One
     * rule, so every surface that asks for banner art gets the animated file.
     */
    fun getCachedBannerArtFile(gameId: String): File? =
        getCachedBannerGifFile(gameId) ?: getCachedBannerImageFile(gameId)

    /** The jingle a 3DS banner carries, decoded to PCM WAV; null for any other platform. */
    fun getCachedJingleFile(gameId: String): File? = cachedFile(gameId, JINGLE_FILENAME)

    fun getCachedMedia(gameId: String): CachedEmbeddedMedia? {
        val iconFile = getCachedIconFile(gameId) ?: return null
        val metadataFile = File(iconFile.parentFile, METADATA_FILENAME)
        val metadata = if (metadataFile.isFile) {
            try {
                FileInputStream(metadataFile).use { EmbeddedMediaMetadataCodec.decode(it.readBytes()) }
            } catch (error: IOException) {
                Log.w(TAG, "Failed to read cached media metadata for game $gameId", error)
                null
            } catch (error: IllegalArgumentException) {
                Log.w(TAG, "Cached media metadata is invalid for game $gameId", error)
                null
            }
        } else {
            null
        }
        return CachedEmbeddedMedia(iconFile, metadata?.title, metadata?.publisher)
    }

    /**
     * Deletes temporary files left in the cache by work that never finished (the app was killed,
     * or banner drawing was stopped part way). Finished files are written whole and renamed into
     * place, so only these "*.tmp" leftovers can ever be incomplete.
     */
    fun cleanupUnfinished() {
        runCatching {
            mediaRoot.listFiles()?.forEach { dir ->
                dir.listFiles()?.forEach { file -> if (file.name.endsWith(".tmp")) file.delete() }
            }
        }
    }

    /** One extraction per game at a time; a second caller waits and reuses the first one's result. */
    private val gameLocks = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()

    /**
     * Extracts and caches a game's icon, title and (with [renderBanner]) its 3DS banner. The game
     * page and the background banner queue can ask for the same game at once; they used to draw
     * the same banner twice side by side. Now the second waits, and skips whatever the first made.
     */
    suspend fun extractAndCacheForGame(
        gameId: String,
        romPath: String,
        renderBanner: Boolean = true
    ): File? {
        val lock = gameLocks.getOrPut(gameId) { kotlinx.coroutines.sync.Mutex() }
        return lock.withLock {
            val icon = getCachedIconFile(gameId)
            val needsBanner = renderBanner && getCachedBannerImageFile(gameId) == null
            if (icon != null && !needsBanner) icon
            else extractAndCacheLocked(gameId, romPath, needsBanner)
        }
    }

    private suspend fun extractAndCacheLocked(
        gameId: String,
        romPath: String,
        renderBanner: Boolean
    ): File? = withContext(Dispatchers.IO) {
        if (!isSafeGameId(gameId)) {
            Log.e(TAG, "Refusing to cache embedded media for invalid game id")
            return@withContext null
        }
        val result = mediaManager.extractForGame(romPath, renderBanner)
        if (!result.success || result.iconBitmap == null) {
            Log.w(TAG, "Extraction failed for game $gameId (${File(romPath).name}): ${result.message}")
            return@withContext null
        }

        try {
            val gameDir = File(mediaRoot, gameId)
            if (!gameDir.isDirectory && !gameDir.mkdirs()) {
                throw IOException("Could not create extracted-media cache directory")
            }
            val iconFile = File(gameDir, ICON_FILENAME)
            val iconTempFile = File.createTempFile("icon-", ".tmp", gameDir)
            val metadata = EmbeddedMediaMetadataCodec.encode(result.title, result.publisher)
            val metadataTempFile = metadata?.let { File.createTempFile("metadata-", ".tmp", gameDir) }
            val bannerTempFile = result.bannerCgfx?.let { File.createTempFile("banner-", ".tmp", gameDir) }
            val bannerCommonTempFile = result.bannerCommonCgfx?.let { File.createTempFile("banner-common-", ".tmp", gameDir) }
            val bannerImageTempFile = result.bannerBitmap?.let { File.createTempFile("banner-image-", ".tmp", gameDir) }
            val bannerGifTempFile = result.bannerGif?.let { File.createTempFile("banner-gif-", ".tmp", gameDir) }
            val jingleTempFile = result.bannerJingleWav?.let { File.createTempFile("jingle-", ".tmp", gameDir) }
            try {
                FileOutputStream(iconTempFile).use { out ->
                    val format = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSLESS
                    } else {
                        @Suppress("DEPRECATION")
                        Bitmap.CompressFormat.WEBP
                    }
                    if (!result.iconBitmap.compress(format, 100, out)) {
                        throw IOException("Could not encode extracted icon")
                    }
                    out.fd.sync()
                }
                if (metadata != null && metadataTempFile != null) {
                    FileOutputStream(metadataTempFile).use { out ->
                        out.write(metadata)
                        out.fd.sync()
                    }
                }
                if (result.bannerCgfx != null && bannerTempFile != null) {
                    FileOutputStream(bannerTempFile).use { out ->
                        out.write(result.bannerCgfx)
                        out.fd.sync()
                    }
                }
                if (result.bannerCommonCgfx != null && bannerCommonTempFile != null) {
                    FileOutputStream(bannerCommonTempFile).use { out -> out.write(result.bannerCommonCgfx) }
                }
                if (result.bannerBitmap != null && bannerImageTempFile != null) {
                    FileOutputStream(bannerImageTempFile).use { out ->
                        if (!result.bannerBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                            throw IOException("Could not encode extracted 3DS banner image")
                        }
                        out.fd.sync()
                    }
                }
                if (result.bannerGif != null && bannerGifTempFile != null) {
                    FileOutputStream(bannerGifTempFile).use { out ->
                        out.write(result.bannerGif)
                        out.fd.sync()
                    }
                }
                if (result.bannerJingleWav != null && jingleTempFile != null) {
                    FileOutputStream(jingleTempFile).use { out ->
                        out.write(result.bannerJingleWav)
                        out.fd.sync()
                    }
                }
                replaceFile(iconTempFile, iconFile)
                val metadataFile = File(gameDir, METADATA_FILENAME)
                if (metadataTempFile != null) {
                    replaceFile(metadataTempFile, metadataFile)
                } else if (metadataFile.exists() && !metadataFile.delete()) {
                    throw IOException("Could not remove stale extracted-media metadata")
                }
                // A run that did not draw the banner leaves the cached banner files as they are.
                if (result.bannerRendered) {
                    val bannerFile = File(gameDir, BANNER_CGFX_FILENAME)
                    if (bannerTempFile != null) {
                        replaceFile(bannerTempFile, bannerFile)
                    } else if (bannerFile.exists() && !bannerFile.delete()) {
                        throw IOException("Could not remove stale extracted-media banner")
                    }
                    val bannerCommonFile = File(gameDir, BANNER_COMMON_CGFX_FILENAME)
                    if (bannerCommonTempFile != null) {
                        replaceFile(bannerCommonTempFile, bannerCommonFile)
                    } else if (bannerCommonFile.exists()) {
                        bannerCommonFile.delete()
                    }
                    val bannerImageFile = File(gameDir, BANNER_IMAGE_FILENAME)
                    if (bannerImageTempFile != null) {
                        replaceFile(bannerImageTempFile, bannerImageFile)
                    } else if (bannerImageFile.exists() && !bannerImageFile.delete()) {
                        throw IOException("Could not remove stale extracted 3DS banner image")
                    }
                    val bannerGifFile = File(gameDir, BANNER_GIF_FILENAME)
                    if (bannerGifTempFile != null) {
                        replaceFile(bannerGifTempFile, bannerGifFile)
                    } else if (bannerGifFile.exists() && !bannerGifFile.delete()) {
                        throw IOException("Could not remove stale extracted 3DS banner animation")
                    }
                }
                val jingleFile = File(gameDir, JINGLE_FILENAME)
                if (jingleTempFile != null) {
                    replaceFile(jingleTempFile, jingleFile)
                } else if (jingleFile.exists() && !jingleFile.delete()) {
                    throw IOException("Could not remove stale extracted 3DS jingle")
                }
                result.bannerFailureReason?.let { reason ->
                    Log.w(TAG, "Banner extraction incomplete for game $gameId: $reason")
                }
                Log.d(TAG, "Cached extracted icon to ${iconFile.absolutePath}")
                iconFile
            } finally {
                iconTempFile.delete()
                metadataTempFile?.delete()
                bannerTempFile?.delete()
                bannerImageTempFile?.delete()
                bannerGifTempFile?.delete()
                bannerCommonTempFile?.delete()
                jingleTempFile?.delete()
                result.bannerBitmap?.recycle()
            }
        } catch (error: IOException) {
            Log.e(TAG, "Failed to cache extracted media for game $gameId", error)
            null
        } catch (error: SecurityException) {
            Log.e(TAG, "Permission denied while caching extracted media for game $gameId", error)
            null
        }
    }

    private fun cachedFile(gameId: String, name: String): File? {
        if (!isSafeGameId(gameId)) return null
        val file = File(File(mediaRoot, gameId), name)
        return if (file.isFile) file else null
    }

    private fun replaceFile(source: File, destination: File) {
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (error: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun isSafeGameId(gameId: String): Boolean =
        gameId.isNotBlank() &&
            gameId != "." &&
            gameId != ".." &&
            '/' !in gameId &&
            '\\' !in gameId &&
            gameId.none(Char::isISOControl)

    companion object {
        /**
         * The extracted jingle for [gameId], for callers that have a Context but no repository
         * instance, such as the jingle player. The directory layout lives here so it has one home.
         */
        fun cachedJingleFile(context: Context, gameId: String): File? {
            if (gameId.isBlank() || '/' in gameId || '\\' in gameId) return null
            if (gameId == "." || gameId == ".." || gameId.any(Char::isISOControl)) return null
            val file = File(File(File(context.filesDir, MEDIA_DIRECTORY), gameId), JINGLE_FILENAME)
            return if (file.isFile) file else null
        }
    }
}

data class CachedEmbeddedMedia(
    val iconFile: File,
    val title: String?,
    val publisher: String?
)

data class EmbeddedMediaMetadata(
    val title: String?,
    val publisher: String?
)

internal object EmbeddedMediaMetadataCodec {

    private const val VERSION = "1"
    private const val VERSION_KEY = "version"
    private const val TITLE_KEY = "title"
    private const val PUBLISHER_KEY = "publisher"

    fun encode(title: String?, publisher: String?): ByteArray? {
        val normalizedTitle = title?.takeIf(String::isNotBlank)
        val normalizedPublisher = publisher?.takeIf(String::isNotBlank)
        if (normalizedTitle == null && normalizedPublisher == null) return null

        val properties = Properties().apply {
            setProperty(VERSION_KEY, VERSION)
            normalizedTitle?.let { setProperty(TITLE_KEY, it) }
            normalizedPublisher?.let { setProperty(PUBLISHER_KEY, it) }
        }
        return ByteArrayOutputStream().use { output ->
            properties.store(output, null)
            output.toByteArray()
        }
    }

    fun decode(bytes: ByteArray): EmbeddedMediaMetadata? {
        val properties = Properties()
        ByteArrayInputStream(bytes).use(properties::load)
        if (properties.getProperty(VERSION_KEY) != VERSION) return null
        return EmbeddedMediaMetadata(
            title = properties.getProperty(TITLE_KEY)?.takeIf(String::isNotBlank),
            publisher = properties.getProperty(PUBLISHER_KEY)?.takeIf(String::isNotBlank)
        )
    }
}
