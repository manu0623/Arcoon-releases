package com.nendo.argosy.data.repository

import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ServerSaveHold {
    val reason: String

    data class InactiveSlot(val activeChannel: String?) : ServerSaveHold {
        override val reason: String get() = "slot is not the active one (active=$activeChannel)"
    }

    data class Superseded(val serverTime: Instant, val activeCacheId: Long, val activeAt: Instant) : ServerSaveHold {
        override val reason: String
            get() = "server save ($serverTime) is not newer than the active save (cacheId=$activeCacheId, $activeAt)"
    }
}

/**
 * Decides whether a server save may replace what is on disk. A save for a slot the game is not
 * on stays on the server, and a save in the active slot that is not newer than the active save
 * is superseded by it. A slot named after the ROM counts as the latest slot.
 */
@Singleton
class ServerSaveGate @Inject constructor(
    private val activeSaveRepository: ActiveSaveRepository
) {
    suspend fun holdFor(gameId: Long, romPath: String, channelName: String?, serverTime: Instant?): ServerSaveHold? {
        val active = activeSaveRepository.getActiveRow(gameId) ?: return null
        val romBaseName = File(romPath).nameWithoutExtension
        if (bucketOf(channelName, romBaseName) != bucketOf(active.channelName, romBaseName)) {
            return ServerSaveHold.InactiveSlot(active.channelName)
        }
        serverTime ?: return null
        if (serverTime.isAfter(active.cachedAt)) return null
        return ServerSaveHold.Superseded(serverTime, active.id, active.cachedAt)
    }

    private fun bucketOf(channelName: String?, romBaseName: String): String =
        if (SaveSyncApiClient.isLatestBucket(channelName, romBaseName)) SaveSyncApiClient.AUTOSAVE_SLOT_NAME
        else channelName.orEmpty()
}
