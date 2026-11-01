package com.nendo.argosy.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A highlighted run of document lines and the palette slot its bookmark is drawn in.
 */
data class DocumentHighlight(val lines: IntRange, val colorIndex: Int = 0)

/**
 * Highlighted passages of text documents, kept on this device only. Each document's highlights
 * live in one file named by [documentKey], one `first last colour` triple per line.
 */
@Singleton
class DocumentHighlightStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun load(key: String): List<DocumentHighlight> = withContext(Dispatchers.IO) {
        val file = fileFor(key)
        if (!file.isFile) return@withContext emptyList()
        decodeHighlights(file.readText())
    }

    suspend fun save(key: String, highlights: List<DocumentHighlight>) = withContext(Dispatchers.IO) {
        val file = fileFor(key)
        if (highlights.isEmpty()) {
            file.delete()
        } else {
            file.parentFile?.mkdirs()
            file.writeText(encodeHighlights(highlights))
        }
    }

    private fun fileFor(key: String): File =
        File(File(context.filesDir, HIGHLIGHT_DIR), "$key.txt")

    private companion object {
        const val HIGHLIGHT_DIR = "document_highlights"
    }
}

internal fun encodeHighlights(highlights: List<DocumentHighlight>): String =
    highlights.sortedBy { it.lines.first }
        .joinToString("\n") { "${it.lines.first} ${it.lines.last} ${it.colorIndex}" }

internal fun decodeHighlights(text: String): List<DocumentHighlight> =
    text.lineSequence().mapNotNull { line ->
        val parts = line.trim().split(' ')
        val first = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
        val last = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
        val color = parts.getOrNull(2)?.toIntOrNull() ?: 0
        if (last < first) null else DocumentHighlight(first..last, color)
    }.toList()
