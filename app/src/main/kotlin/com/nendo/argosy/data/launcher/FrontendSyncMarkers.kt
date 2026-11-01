package com.nendo.argosy.data.launcher

import com.nendo.argosy.data.preferences.StoragePreferencesRepository
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the marker files in one Frontend Sync folder. A marker holds a single positive decimal id
 * of at most 128 bytes; ids past the 32-bit range (DroidDeck's added-exe entries) and anything
 * else in the folder, such as DroidDeck's inventory json and images directory, are skipped.
 */
object FrontendSyncMarkers {
    private const val TAG = "FrontendSyncMarkers"
    private const val MAX_MARKER_BYTES = 128L
    private val MARKER_ID = Regex("[1-9][0-9]{0,19}")

    /**
     * The markers in [dir], or null when the folder or any marker in it cannot be read, so a
     * caller never mistakes an unreadable marker for an uninstalled game.
     */
    fun read(dir: File, folder: GameNativeSyncFolder): List<Pair<Int, String>>? {
        val files = dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(folder.markerExtension, ignoreCase = true) }
            ?: return null
        val markers = mutableListOf<Pair<Int, String>>()
        for (file in files) {
            if (file.length() > MAX_MARKER_BYTES) continue
            val content = runCatching { file.readText() }.getOrElse {
                Logger.warn(TAG, "read: unreadable marker | file=${file.name}, error=${it.message}")
                return null
            }
            val id = parseId(content) ?: continue
            markers += id to file.name.dropLast(folder.markerExtension.length)
        }
        return markers
    }

    fun parseId(content: String): Int? =
        content.trim().takeIf { MARKER_ID.matches(it) }?.toIntOrNull()
}

@Singleton
class SteamInstallReports @Inject constructor(
    private val storagePrefs: StoragePreferencesRepository
) {
    suspend fun launchersReporting(steamAppId: Long): List<String> = withContext(Dispatchers.IO) {
        val dirs = storagePrefs.preferences.first().gameNativeSyncDirs
        GameNativeSyncFolder.STEAM_INSTALL_MARKERS
            .filter { folder ->
                val dir = dirs[folder]?.let(::File) ?: return@filter false
                FrontendSyncMarkers.read(dir, folder)?.any { it.first.toLong() == steamAppId } == true
            }
            .map { it.launcherPackage }
            .distinct()
    }
}
