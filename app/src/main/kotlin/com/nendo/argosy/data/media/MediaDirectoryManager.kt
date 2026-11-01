package com.nendo.argosy.data.media

import android.content.Context
import com.nendo.argosy.data.preferences.StoragePreferencesRepository
import com.nendo.argosy.util.AppPaths
import com.nendo.argosy.util.TreeRelocator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the directory downloaded movies and episodes live in, and moves that tree when the
 * user picks a different location. Internal storage is the default; an override is used verbatim
 * so an SD card or USB volume keeps whatever layout the user chose.
 */
@Singleton
class MediaDirectoryManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storagePreferences: StoragePreferencesRepository
) {

    private val relocationMutex = Mutex()

    /**
     * Serialises whatever must not see a half-moved tree. Moving the files and repointing the rows
     * that name them are one operation from outside: between the two, every stored path names a file
     * that has already left, and a reader that concluded those files were deleted would clear records
     * that are about to be corrected.
     */
    suspend fun <T> underRelocationLock(block: suspend () -> T): T = relocationMutex.withLock { block() }

    fun defaultMediaDir(): File = AppPaths.mediaDir(context.filesDir)

    suspend fun resolveMediaDir(): File {
        val override = storagePreferences.preferences.first().mediaStoragePath
        return if (override.isNullOrBlank()) defaultMediaDir() else File(override)
    }

    suspend fun targetFileFor(libraryName: String, itemPath: String, fileName: String): File {
        val extension = fileName.substringAfterLast('.', "")
        val baseName = sanitize(fileName.substringBeforeLast('.'))
        val targetName = if (extension.isNotEmpty()) "$baseName.$extension" else baseName
        val itemDir = itemPath.split('/')
            .filter { it.isNotBlank() }
            .fold(File(resolveMediaDir(), sanitize(libraryName))) { dir, segment ->
                File(dir, sanitize(segment))
            }
        return File(itemDir, targetName)
    }

    suspend fun countFiles(): Int = withContext(Dispatchers.IO) {
        val dir = resolveMediaDir()
        if (dir.exists()) dir.walkTopDown().count { it.isFile } else 0
    }

    suspend fun relocate(source: File, destination: File): TreeRelocator.Result = withContext(Dispatchers.IO) {
        TreeRelocator.relocate(source, destination)
    }

    private fun sanitize(name: String): String =
        name.replace(INVALID_CHARS, "_").trim().ifEmpty { "item" }

    companion object {
        private val INVALID_CHARS = Regex("[\\\\/:*?\"<>|;=]")
    }
}
