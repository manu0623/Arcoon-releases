package com.nendo.argosy.data.steam

import android.os.Environment
import android.util.Log
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.storage.AndroidDataAccessor
import com.nendo.argosy.data.storage.StorageVolumeDetector
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SteamPathResolver"
private const val GN_PACKAGE = "app.gamenative"
private const val DOWNLOAD_COMPLETE_MARKER = ".download_complete"

@Singleton
class SteamPathResolver @Inject constructor(
    private val gameDao: GameDao,
    private val androidDataAccessor: AndroidDataAccessor,
    private val storageVolumeDetector: StorageVolumeDetector
) {

    /**
     * Whether [game] is playable from disk or through its launcher. A stored install path is
     * forgotten only when its parent is reachable and the path itself is absent; an unmounted
     * volume or a missing storage grant leaves the row untouched.
     */
    suspend fun isGameInstalled(game: GameEntity): Boolean {
        if (game.isExternallyManaged) return true
        val path = game.localPath ?: return false
        if (File(path, DOWNLOAD_COMPLETE_MARKER).exists() ||
            androidDataAccessor.exists("$path/$DOWNLOAD_COMPLETE_MARKER")
        ) {
            return true
        }
        if (isPositivelyGone(path)) {
            Log.d(TAG, "isGameInstalled: install is gone, clearing stale path | appId=${game.steamAppId}, path=$path")
            runCatching { gameDao.update(game.copy(localPath = null)) }
        }
        return false
    }

    private fun isPositivelyGone(path: String): Boolean {
        val parent = File(path).parentFile ?: return false
        val parentReachable = parent.exists() || androidDataAccessor.exists(parent.absolutePath)
        if (!parentReachable) return false
        return !File(path).exists() && !androidDataAccessor.exists(path)
    }

    fun findGnStoragePath(): String? = findAllGnStoragePaths().firstOrNull()

    fun findAllGnStoragePaths(): List<String> {
        val primary = Environment.getExternalStorageDirectory().absolutePath
        val removables = storageVolumeDetector.detectStorageVolumes().map { it.path }
        val roots = (removables + primary).distinct()

        val results = mutableListOf<String>()
        for (root in roots) {
            val packageDir = "$root/Android/data/$GN_PACKAGE"
            val basePath = "$packageDir/files"

            if (File(packageDir).exists() || androidDataAccessor.exists(packageDir)) {
                results += basePath
            }
        }
        return results.distinct()
    }
}
