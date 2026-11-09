package com.nendo.argosy.ui.cocoon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Small, ready-to-draw copies of game covers (512 px on the short side), made once by the
 * optimizer and kept on the device. A folder decodes these instead of the full-size covers, so
 * covers appear at once and changing the row count never reloads them.
 *
 * They are saved already trimmed ([CocoonCoverTrim]), so showing one is only reading a small
 * finished file: no trimming each time, and the picture can go straight to the graphics chip.
 */
object CocoonThumbs {
    const val SIZE = 512
    private val known = ConcurrentHashMap<String, File>()
    private val missing = ConcurrentHashMap.newKeySet<String>()

    fun dir(context: Context): File = File(context.applicationContext.filesDir, "cocoon_thumbs").apply { mkdirs() }

    /** The cover's identity: its path, size and date, so a changed cover gets a new thumbnail. */
    fun keyFor(path: String): String {
        val f = File(path)
        return md5(path + "|" + f.length() + "|" + f.lastModified())
    }

    /** Names carry the trim's version: thumbnails made before the trim was baked in are remade. */
    private const val BAKED_SUFFIX = "-t3.webp"

    fun fileFor(context: Context, path: String): File = File(dir(context), keyFor(path) + BAKED_SUFFIX)

    /** Whether [data] is one of these finished thumbnails (already trimmed). */
    fun isBaked(data: Any?): Boolean = data is File && data.name.endsWith(BAKED_SUFFIX)

    /**
     * The one request every cover tile, prefetch and warm-up makes, so they all share the same
     * picture in memory: a finished thumbnail as it is, anything else trimmed as it is decoded.
     */
    fun coverRequest(
        context: Context,
        data: Any,
        sizePx: Int,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher? = null,
    ): coil.request.ImageRequest = coil.request.ImageRequest.Builder(context)
        .data(data)
        .size(sizePx)
        .apply { if (!isBaked(data)) transformations(CocoonCoverTrim) }
        .apply { if (dispatcher != null) dispatcher(dispatcher) }
        .build()

    /** The ready thumbnail for a cover path, or null when it hasn't been made (or the cover changed). */
    fun ready(context: Context, path: String): File? {
        known[path]?.let { return it }
        if (path in missing) return null
        val thumb = fileFor(context, path)
        return if (thumb.isFile && thumb.length() > 0) {
            known[path] = thumb
            thumb
        } else {
            missing += path
            null
        }
    }

    /** What to load for a cover: its thumbnail when ready, otherwise the cover itself. */
    fun model(context: Context, path: String?): Any? = when {
        path == null -> null
        path.startsWith("/") -> ready(context, path) ?: File(path)
        else -> path
    }

    /** Makes the thumbnail for one cover; true when it exists afterwards. */
    fun make(context: Context, path: String): Boolean {
        val original = File(path)
        if (!original.isFile) return false
        val out = fileFor(context, path)
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            val shortSide = minOf(bounds.outWidth, bounds.outHeight)
            if (shortSide <= 0) return false
            var sample = 1
            while (shortSide / (sample * 2) >= SIZE) sample *= 2
            val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return false
            val decodedShort = minOf(bitmap.width, bitmap.height)
            val resized = if (decodedShort > SIZE * 1.2f) {
                val f = SIZE.toFloat() / decodedShort
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * f).toInt(), (bitmap.height * f).toInt(), true)
                    .also { if (it !== bitmap) bitmap.recycle() }
            } else bitmap
            // the side-band trim, done once here instead of every time the cover is shown
            val scaled = CocoonCoverTrim.trim(resized).also { if (it !== resized) resized.recycle() }
            val tmp = File(out.parentFile, out.name + ".tmp")
            tmp.outputStream().use { stream ->
                @Suppress("DEPRECATION")
                val format = if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
                scaled.compress(format, 88, stream)
            }
            scaled.recycle()
            tmp.renameTo(out)
            missing -= path
            known[path] = out
            true
        }.getOrDefault(false)
    }

    /** Removes thumbnails whose covers are gone. */
    fun tidy(context: Context, keepPaths: Collection<String>) {
        val keep = keepPaths.mapTo(HashSet()) { keyFor(it) + BAKED_SUFFIX }
        dir(context).listFiles()?.forEach { file ->
            if (file.name !in keep) file.delete()
        }
    }

    private fun md5(text: String): String =
        MessageDigest.getInstance("MD5").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
