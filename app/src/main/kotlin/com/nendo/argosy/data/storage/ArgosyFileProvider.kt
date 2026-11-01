package com.nendo.argosy.data.storage

import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

/**
 * A FileProvider that answers DocumentsContract columns (mime_type, _size, last_modified,
 * document_id) as well as OpenableColumns, which emulators such as NetherSX2 read on launch.
 */
class ArgosyFileProvider : FileProvider() {

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        if (projection == null || projection.all { it in OPENABLE_COLUMNS }) {
            return super.query(uri, projection, selection, selectionArgs, sortOrder)
        }

        val file = resolveFile(uri) ?: return super.query(uri, projection, selection, selectionArgs, sortOrder)
        val row = projection.map { column -> valueFor(column, file, uri) }.toTypedArray()
        return MatrixCursor(projection, 1).apply { addRow(row) }
    }

    private fun resolveFile(uri: Uri): File? {
        val decoded = Uri.decode(uri.encodedPath ?: return null) ?: return null
        return resolveProviderPath(decoded, roots())?.takeIf { it.exists() }
    }

    private fun roots(): Map<String, File> {
        val context = context ?: return emptyMap()
        return listOfNotNull(
            ROOT_STORAGE to File("/storage"),
            ROOT_MNT to File("/mnt"),
            context.getExternalFilesDir(null)?.let { ROOT_EXTERNAL_FILES to it },
            ROOT_INTERNAL to context.filesDir,
            ROOT_CACHE to context.cacheDir
        ).toMap()
    }

    private fun valueFor(column: String, file: File, uri: Uri): Any? = when (column) {
        OpenableColumns.DISPLAY_NAME -> file.name
        OpenableColumns.SIZE, COLUMN_SIZE -> file.length()
        COLUMN_MIME_TYPE -> {
            val ext = file.extension.lowercase()
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }
        COLUMN_LAST_MODIFIED -> file.lastModified()
        COLUMN_DOCUMENT_ID -> uri.toString()
        COLUMN_FLAGS -> 0
        else -> null
    }

    companion object {
        private const val COLUMN_MIME_TYPE = "mime_type"
        private const val COLUMN_SIZE = "_size"
        private const val COLUMN_LAST_MODIFIED = "last_modified"
        private const val COLUMN_DOCUMENT_ID = "document_id"
        private const val COLUMN_FLAGS = "flags"

        private const val ROOT_STORAGE = "storage"
        private const val ROOT_MNT = "mnt"
        private const val ROOT_EXTERNAL_FILES = "external_files"
        private const val ROOT_INTERNAL = "internal"
        private const val ROOT_CACHE = "cache"

        private val OPENABLE_COLUMNS = setOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)

        /**
         * The file behind a decoded provider path such as `/storage/emulated/0/Games/x.chd`, where
         * the first segment names one of [roots] as declared in res/xml/file_paths.xml. Null for an
         * unknown root or a path that resolves outside its root.
         */
        fun resolveProviderPath(decodedPath: String, roots: Map<String, File>): File? {
            val segments = decodedPath.trimStart('/').split('/', limit = 2)
            if (segments.size < 2) return null
            val root = roots[segments[0]]?.canonicalFile ?: return null
            val file = File(root, segments[1]).canonicalFile
            return file.takeIf { it.path.startsWith(root.path + File.separator) }
        }
    }
}
