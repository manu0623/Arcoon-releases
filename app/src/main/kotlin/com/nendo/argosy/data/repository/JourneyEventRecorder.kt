package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.AchievementJourneyEventDao
import com.nendo.argosy.data.local.entity.AchievementJourneyEventEntity
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "JourneyEventRecorder"

/**
 * Records the rich presence line RetroAchievements reports at the moment of an unlock. The insert
 * ignores a row that already exists, so a repeated unlock keeps the context captured the first
 * time. A missing answer stores the event with no context and the journal says nothing more.
 */
@Singleton
class JourneyEventRecorder @Inject constructor(
    private val eventDao: AchievementJourneyEventDao,
    private val metadataSource: RaMetadataSource,
    private val raRepository: RetroAchievementsRepository
) {

    suspend fun capture(gameId: Long, raId: Long, at: Long = System.currentTimeMillis()) {
        withContext(Dispatchers.IO) {
            val owner = raRepository.activeOwnerUserId()
            val presence = runCatching { metadataSource.userSummary()?.richPresenceMsg }
                .getOrNull()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            if (presence == null) Logger.debug(TAG, "No rich presence captured for raId=$raId")
            eventDao.insert(
                AchievementJourneyEventEntity(
                    gameId = gameId,
                    raId = raId,
                    ownerUserId = owner,
                    richPresence = presence,
                    capturedAt = at
                )
            )
        }
    }
}
