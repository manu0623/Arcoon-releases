package com.nendo.argosy.domain.usecase.savechannel

import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncRepository
import javax.inject.Inject

class CopySaveChannelUseCase @Inject constructor(
    private val saveCacheManager: SaveCacheManager,
    private val saveSyncRepository: SaveSyncRepository
) {
    suspend operator fun invoke(
        gameId: Long,
        targetChannel: String,
        localCacheId: Long?,
        serverSaveId: Long?,
        emulatorId: String?
    ): Boolean = when {
        localCacheId != null -> saveCacheManager.copyToChannel(localCacheId, targetChannel) != null
        serverSaveId != null ->
            saveSyncRepository.downloadSaveAsChannel(gameId, serverSaveId, targetChannel, emulatorId)
        else -> false
    }
}
