package com.nendo.argosy.data.repository

import com.nendo.argosy.data.remote.romm.RomMSave
import com.nendo.argosy.util.Logger
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConflictDetector @Inject constructor() {

    fun determineSyncStatus(
        localTime: Instant?,
        serverTime: Instant
    ): String {
        if (localTime == null) return com.nendo.argosy.data.local.entity.SaveSyncEntity.STATUS_SERVER_NEWER
        return when {
            serverTime.isAfter(localTime) -> com.nendo.argosy.data.local.entity.SaveSyncEntity.STATUS_SERVER_NEWER
            localTime.isAfter(serverTime) -> com.nendo.argosy.data.local.entity.SaveSyncEntity.STATUS_LOCAL_NEWER
            else -> com.nendo.argosy.data.local.entity.SaveSyncEntity.STATUS_SYNCED
        }
    }

    fun extractUploaderDeviceName(save: RomMSave?, currentDeviceId: String?): String? {
        val syncs = save?.deviceSyncs ?: return null
        return syncs
            .filter { it.deviceId != currentDeviceId }
            .maxByOrNull { it.lastSyncedAt ?: "" }
            ?.deviceName
    }

    fun pickLatestServerSave(
        serverSaves: List<RomMSave>,
        channelName: String?,
        romBaseName: String?,
        isGciBundle: Boolean
    ): RomMSave? = SaveSyncApiClient.selectLatestServerSave(serverSaves, channelName, romBaseName, isGciBundle)


    data class UploadConflictDecision(
        val isConflict: Boolean,
        val localTimestamp: Instant,
        val serverTimestamp: Instant,
        val serverDeviceName: String?
    )

    fun detectUploadConflict(
        gameId: Long,
        channelName: String?,
        forceOverwrite: Boolean,
        currentDeviceId: String?,
        latestServerSave: RomMSave?,
        localModified: Instant
    ): UploadConflictDecision? {
        if (forceOverwrite) {
            Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Skipping conflict check (force overwrite)")
            return null
        }

        if (currentDeviceId != null) return null

        if (channelName == null && latestServerSave != null) {
            val serverTime = SaveSyncApiClient.parseTimestamp(latestServerSave.updatedAt)
            val deltaMs = serverTime.toEpochMilli() - localModified.toEpochMilli()
            val deltaStr = if (deltaMs >= 0) "+${deltaMs / 1000}s" else "${deltaMs / 1000}s"
            Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Conflict check | local=$localModified, server=$serverTime, delta=$deltaStr")
            if (serverTime.isAfter(localModified)) {
                Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Decision=CONFLICT | Server is newer, blocking upload")
                return UploadConflictDecision(
                    isConflict = true,
                    localTimestamp = localModified,
                    serverTimestamp = serverTime,
                    serverDeviceName = extractUploaderDeviceName(latestServerSave, currentDeviceId)
                )
            }
            Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Decision=PROCEED | Local is newer or equal")
        }
        return null
    }

    companion object {
        private const val TAG = "ConflictDetector"
    }
}
