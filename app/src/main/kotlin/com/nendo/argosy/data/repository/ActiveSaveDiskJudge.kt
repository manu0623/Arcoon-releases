package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.sync.SaveClaim
import com.nendo.argosy.data.sync.SaveOwnershipTracker
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DiskVerdict {
    data object Matches : DiskVerdict
    data class Progress(val cacheId: Long) : DiskVerdict
    data object Adopt : DiskVerdict
    data object Foreign : DiskVerdict
    data object ProtectThenRestore : DiskVerdict
}

data class DiskJudgement(val verdict: DiskVerdict, val syncRow: SaveSyncEntity?)

/**
 * The launch-time decision for a save on disk that is about to be replaced by the cached
 * version [judge] is given. Every restore door that writes a cached save over the disk on
 * launch asks this one judge and only acts on its verdict.
 */
@Singleton
class ActiveSaveDiskJudge @Inject constructor(
    private val saveCacheDao: SaveCacheDao,
    private val saveSyncDao: SaveSyncDao,
    private val saveOwnershipTracker: SaveOwnershipTracker
) {
    suspend fun judge(
        gameId: Long,
        emulatorId: String,
        channelName: String?,
        savePath: String,
        diskHash: String,
        target: SaveCacheEntity,
        secureSaves: Boolean,
        ownerUserId: Long?
    ): DiskJudgement {
        val channel = channelName ?: SaveSyncApiClient.AUTOSAVE_SLOT_NAME
        val syncRow = saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, SaveSyncApiClient.syncKeyOf(channel), ownerUserId)
        val knownForms = setOfNotNull(
            target.contentHash,
            target.identityHash,
            syncRow?.localContentHash?.takeIf { syncRow.rommSaveId != null && syncRow.rommSaveId == target.rommSaveId }
        )
        if (diskHash in knownForms) return DiskJudgement(DiskVerdict.Matches, syncRow)

        val newerUnsynced = saveCacheDao.getAllByGameChannelAndHash(gameId, ownerUserId, target.channelName, diskHash)
            .firstOrNull { it.needsRemoteSync && !it.cachedAt.isBefore(target.cachedAt) }
        if (newerUnsynced != null) return DiskJudgement(DiskVerdict.Progress(newerUnsynced.id), syncRow)

        if (!secureSaves) {
            val verdict = if (saveOwnershipTracker.claim(savePath, emulatorId) is SaveClaim.Foreign) DiskVerdict.Foreign else DiskVerdict.Adopt
            return DiskJudgement(verdict, syncRow)
        }
        return DiskJudgement(DiskVerdict.ProtectThenRestore, syncRow)
    }
}
