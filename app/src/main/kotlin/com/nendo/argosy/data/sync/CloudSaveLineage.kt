package com.nendo.argosy.data.sync

import android.content.Context
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether this device's save for a game belongs to the cloud history ("linked"), the way Steam
 * Cloud tells a save that came from the cloud from one that started on its own.
 *
 * A game is linked once this device has placed a save from RomM, uploaded one as part of its
 * history, found that RomM has no save for it yet, or the player picked which save to keep. Until
 * then, a save made here (a new game started on a fresh install, or progress from before Arcoon)
 * never quietly replaces the save in RomM: the player is asked once instead.
 *
 * Kept per RomM account. Devices that already synced before this existed have their history
 * adopted once, so nothing changes for games they already sync.
 */
@Singleton
class CloudSaveLineage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncPreferencesRepository: SyncPreferencesRepository,
    private val saveSyncDao: SaveSyncDao,
    private val saveCacheDao: SaveCacheDao,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val lock = Mutex()

    suspend fun isLinked(gameId: Long): Boolean {
        val owner = syncPreferencesRepository.getRommUserId()
        return lock.withLock { linkedFor(owner).contains(gameId.toString()) }
    }

    suspend fun markLinked(gameId: Long, reason: String) {
        val owner = syncPreferencesRepository.getRommUserId()
        lock.withLock {
            val current = linkedFor(owner)
            if (current.contains(gameId.toString())) return
            prefs.edit().putStringSet(keyFor(owner), current + gameId.toString()).apply()
        }
        Logger.info(TAG, "[SaveSync] LINEAGE gameId=$gameId | linked to the cloud history ($reason)")
    }

    /** Must be called with [lock] held. Adopts this device's existing history the first time. */
    private suspend fun linkedFor(owner: Long?): Set<String> {
        val key = keyFor(owner)
        val migratedKey = "$key$MIGRATED_SUFFIX"
        if (!prefs.getBoolean(migratedKey, false)) {
            // Only a device updated from an older Arcoon has history to adopt. On a fresh install
            // anything in the tables was written moments ago (history cached from RomM, not a
            // save this device made), and adopting it would switch off the protection.
            val history = if (isFreshInstall()) emptySet() else runCatching {
                (saveSyncDao.gameIdsWithUploadHistory(owner) + saveCacheDao.gameIdsWithAppliedCloudSave(owner))
                    .map { it.toString() }.toSet()
            }.getOrDefault(emptySet())
            val merged = prefs.getStringSet(key, emptySet()).orEmpty() + history
            prefs.edit().putStringSet(key, merged).putBoolean(migratedKey, true).apply()
            Logger.info(TAG, "[SaveSync] LINEAGE | adopted existing history for ${history.size} games (account=$owner)")
            return merged
        }
        return prefs.getStringSet(key, emptySet()).orEmpty()
    }

    private fun keyFor(owner: Long?): String = "linked_${owner ?: 0L}"

    private fun isFreshInstall(): Boolean = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.firstInstallTime == info.lastUpdateTime
    }.getOrDefault(false)

    private companion object {
        const val TAG = "CloudSaveLineage"
        const val PREFS = "cloud_save_lineage"
        const val MIGRATED_SUFFIX = "_migrated"
    }
}
