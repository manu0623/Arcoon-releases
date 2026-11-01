package com.nendo.argosy.data.music

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import com.nendo.argosy.data.preferences.StoragePreferencesRepository
import com.nendo.argosy.util.TreeRelocator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton


/** Resolves and manages the public Music/RomM directory where soundtrack files live. */
@Singleton
class MusicDirectoryManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storagePreferences: StoragePreferencesRepository
) {

    suspend fun resolveMusicDir(): File {
        val override = storagePreferences.preferences.first().musicStoragePath
        return if (override != null) File(override) else defaultMusicDir()
    }

    fun defaultMusicDir(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "RomM")

    suspend fun targetFileFor(
        platformName: String,
        gameName: String,
        trackNumber: Int?,
        title: String?,
        fileName: String
    ): File = targetFileIn(resolveMusicDir(), platformName, gameName, trackNumber, title, fileName)

    fun targetFileIn(
        musicDir: File,
        platformName: String,
        gameName: String,
        trackNumber: Int?,
        title: String?,
        fileName: String
    ): File {
        val extension = fileName.substringAfterLast('.', "").replace(INVALID_CHARS, "_")
        val baseTitle = sanitize(title ?: fileName.substringBeforeLast('.'))
        val baseName = if (trackNumber != null) {
            String.format(Locale.ROOT, "%02d - %s", trackNumber, baseTitle)
        } else {
            baseTitle
        }
        val targetName = if (extension.isNotEmpty()) "$baseName.$extension" else baseName
        val gameDir = File(File(musicDir, sanitize(platformName)), sanitize(gameName))
        return File(gameDir, targetName)
    }

    fun moveIntoMusic(source: File, target: File): Boolean {
        return try {
            target.parentFile?.mkdirs()
            if (target.exists()) target.delete()
            if (source.renameTo(target)) {
                true
            } else {
                source.copyTo(target, overwrite = true)
                source.delete()
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun countFiles(): Int = withContext(Dispatchers.IO) {
        val dir = resolveMusicDir()
        if (dir.exists()) dir.walkTopDown().count { it.isFile } else 0
    }

    suspend fun relocate(source: File, destination: File): TreeRelocator.Result = withContext(Dispatchers.IO) {
        val result = TreeRelocator.relocate(source, destination)
        if (result.moved.isNotEmpty()) {
            MediaScannerConnection.scanFile(context, result.moved.toTypedArray(), null, null)
        }
        result
    }

    fun scanFile(file: File) {
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
    }

    private fun sanitize(name: String): String {
        val folded = name.replace(INVALID_CHARS, "_").trim()
        return if (folded.isEmpty() || folded.all { it == '.' }) "track" else folded
    }

    companion object {
        private val INVALID_CHARS = Regex("[\\\\/:*?\"<>|;=]")
    }
}
