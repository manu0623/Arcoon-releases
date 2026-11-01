package com.nendo.argosy.data.repository

import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DocumentContent {
    data class Text(val body: String) : DocumentContent
    data class Pages(val pages: List<android.graphics.Bitmap>) : DocumentContent
    data class Unavailable(val reason: String?) : DocumentContent
}

/**
 * The stable name a document is cached and annotated under: its RomM file id, else a hash of its
 * url. Null for a document with neither.
 */
fun documentKey(rommFileId: Long?, remoteUrl: String?): String? =
    rommFileId?.let { "romm_$it" } ?: remoteUrl?.let { "url_${it.hashCode().toUInt()}" }

@Singleton
class GameDocumentLoader @Inject constructor(
    private val romMRepository: RomMRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) {
    /**
     * A PDF rendered to one bitmap per page, up to [MAX_PDF_PAGES]. A remote document is kept in
     * the document cache after its first open, so later opens read it from disk.
     */
    suspend fun readPdf(
        localPath: String?,
        rommFileId: Long?,
        remoteUrl: String?,
        fileName: String,
        pageWidthPx: Int
    ): DocumentContent = withContext(Dispatchers.IO) {
        val source = localPath?.let { File(it) }?.takeIf { it.isFile }
            ?: cachedOrFetched(rommFileId, remoteUrl, fileName)
            ?: return@withContext DocumentContent.Unavailable("unreachable")
        try {
            renderPages(source, pageWidthPx)
        } catch (e: Exception) {
            cacheFileFor(rommFileId, remoteUrl, fileName)?.takeIf { it == source }?.delete()
            DocumentContent.Unavailable(e.message)
        }
    }

    /**
     * Downloads a remote document into the document cache unless it is already there. Returns the
     * cached file, or null when the document has no remote source or cannot be fetched.
     */
    suspend fun cacheDocument(rommFileId: Long?, remoteUrl: String?, fileName: String): File? =
        withContext(Dispatchers.IO) { cachedOrFetched(rommFileId, remoteUrl, fileName) }

    fun isCached(rommFileId: Long?, remoteUrl: String?, fileName: String): Boolean =
        cacheFileFor(rommFileId, remoteUrl, fileName)?.isFile == true

    private suspend fun cachedOrFetched(rommFileId: Long?, remoteUrl: String?, fileName: String): File? {
        val target = cacheFileFor(rommFileId, remoteUrl, fileName) ?: return null
        if (target.isFile && target.length() > 0) return target
        return fetchInto(target, rommFileId, remoteUrl, fileName)
    }

    private fun cacheFileFor(rommFileId: Long?, remoteUrl: String?, fileName: String): File? {
        val key = documentKey(rommFileId, remoteUrl) ?: return null
        val extension = fileName.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
        return File(File(context.cacheDir, DOCUMENT_CACHE_DIR).apply { mkdirs() }, "$key$extension")
    }

    private fun renderPages(file: File, pageWidthPx: Int): DocumentContent {
        android.os.ParcelFileDescriptor.open(
            file,
            android.os.ParcelFileDescriptor.MODE_READ_ONLY
        ).use { descriptor ->
            android.graphics.pdf.PdfRenderer(descriptor).use { renderer ->
                val pages = (0 until minOf(renderer.pageCount, MAX_PDF_PAGES)).map { index ->
                    renderer.openPage(index).use { page ->
                        val scale = pageWidthPx.toFloat() / page.width
                        val bitmap = android.graphics.Bitmap.createBitmap(
                            pageWidthPx,
                            (page.height * scale).toInt().coerceAtLeast(1),
                            android.graphics.Bitmap.Config.ARGB_8888
                        )
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(
                            bitmap,
                            null,
                            null,
                            android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )
                        bitmap
                    }
                }
                return DocumentContent.Pages(pages)
            }
        }
    }

    private suspend fun fetchInto(
        target: File,
        rommFileId: Long?,
        remoteUrl: String?,
        fileName: String
    ): File? {
        val partial = File(target.parentFile, "${target.name}.part")
        val body = when {
            rommFileId != null -> when (val r = romMRepository.downloadRomFile(rommFileId, fileName)) {
                is RomMResult.Success -> r.data.body
                is RomMResult.Error -> return null
            }
            remoteUrl != null -> romMRepository.openResource(remoteUrl) ?: return null
            else -> return null
        }
        return runCatching {
            body.use { source ->
                partial.outputStream().use { out -> source.byteStream().copyTo(out) }
            }
            if (!partial.renameTo(target)) error("could not move ${partial.name} into the cache")
            target
        }.onFailure { partial.delete() }.getOrNull()
    }

    /**
     * A document's text, from the downloaded game's folder when present, else from the document
     * cache, which a first open fills from the server.
     */
    suspend fun readText(
        localPath: String?,
        rommFileId: Long?,
        remoteUrl: String? = null,
        fileName: String,
        maxBytes: Int = MAX_TEXT_BYTES
    ): DocumentContent = withContext(Dispatchers.IO) {
        localPath?.let { path ->
            val file = File(path)
            if (file.isFile) {
                return@withContext runCatching { DocumentContent.Text(file.readText()) }
                    .getOrElse { DocumentContent.Unavailable(it.message) }
            }
        }

        if (rommFileId == null && remoteUrl == null) {
            return@withContext DocumentContent.Unavailable("no source")
        }
        val cached = cachedOrFetched(rommFileId, remoteUrl, fileName)
            ?: return@withContext DocumentContent.Unavailable("unreachable")
        runCatching {
            cached.inputStream().use { source ->
                DocumentContent.Text(String(source.readNBytes(maxBytes), Charsets.UTF_8))
            }
        }.getOrElse { DocumentContent.Unavailable(it.message) }
    }

    private companion object {
        const val MAX_TEXT_BYTES = 4 * 1024 * 1024
        const val MAX_PDF_PAGES = 200
        const val DOCUMENT_CACHE_DIR = "documents"
    }
}
