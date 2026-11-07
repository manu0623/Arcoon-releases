package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.AchievementDao
import com.nendo.argosy.data.local.dao.AchievementJourneyDao
import com.nendo.argosy.data.local.dao.AchievementJourneyEventDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.AchievementJourneyEntity
import com.nendo.argosy.domain.model.AchievementJourney
import com.nendo.argosy.domain.usecase.journey.JourneyReconstruction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds a game's journey from the achievement rows already stored for the active account and keeps
 * the generated version on disk. A rebuilt journey with the same [AchievementJourney.evidenceKey]
 * as the stored row keeps the stored generation time, so an unchanged unlock set reads as one
 * narrative rather than a fresh one.
 */
@Singleton
class AchievementJourneyRepository @Inject constructor(
    private val achievementDao: AchievementDao,
    private val journeyDao: AchievementJourneyDao,
    private val eventDao: AchievementJourneyEventDao,
    private val gameDao: GameDao,
    private val raRepository: RetroAchievementsRepository,
    private val metadataSource: RaMetadataSource
) {

    suspend fun load(gameId: Long, platformName: String?): AchievementJourney? =
        withContext(Dispatchers.IO) {
            val owner = raRepository.activeOwnerUserId()
            val game = gameDao.getById(gameId) ?: return@withContext null
            val raGameId = game.effectiveRaId ?: game.raId
            val rows = withRealUnlockDates(raGameId, achievementDao.getByGameId(gameId, owner))
            if (rows.isEmpty()) return@withContext null
            val contexts = eventDao.getForGame(gameId, owner)
                .mapNotNull { event -> event.richPresence?.let { event.raId to it } }
                .toMap()
            val journey = JourneyReconstruction.build(
                gameId = gameId,
                gameTitle = game.title,
                platformName = platformName,
                raGameId = raGameId,
                rows = rows,
                contexts = contexts
            )
            if (journey.isEmpty) return@withContext null
            val stored = journeyDao.get(gameId, owner)
            if (stored?.evidenceKey == journey.evidenceKey) {
                journey.copy(generatedAt = Instant.ofEpochMilli(stored.generatedAt))
            } else {
                journeyDao.upsert(journey.toEntity(owner))
                journey
            }
        }

    /**
     * An unlock RomM did not date is stored with the moment Arcoon first saw it. RetroAchievements
     * keeps the real moment, so each unlocked row takes the earlier of the two. Rows stay as stored
     * when RetroAchievements cannot answer.
     */
    private suspend fun withRealUnlockDates(
        raGameId: Long?,
        rows: List<com.nendo.argosy.data.local.entity.AchievementEntity>
    ): List<com.nendo.argosy.data.local.entity.AchievementEntity> {
        if (raGameId == null || rows.none { it.isUnlocked }) return rows
        val dates = runCatching {
            metadataSource.webUnlockDates(raGameId, Instant.now().minusSeconds(UNLOCK_DATES_FRESH_SECONDS))
        }.getOrNull() ?: return rows
        return rows.map { row ->
            val (casual, hardcore) = dates[row.raId] ?: return@map row
            row.copy(
                unlockedAt = earlier(row.unlockedAt, casual),
                unlockedHardcoreAt = earlier(row.unlockedHardcoreAt, hardcore)
            )
        }
    }

    private fun earlier(stored: Long?, real: Long?): Long? =
        if (stored == null || real == null) stored else minOf(stored, real)

    private fun AchievementJourney.toEntity(ownerUserId: Long) = AchievementJourneyEntity(
        gameId = gameId,
        ownerUserId = ownerUserId,
        raGameId = raGameId,
        evidenceKey = evidenceKey,
        generatedAt = generatedAt.toEpochMilli(),
        totalCount = progress.total,
        unlockedCount = progress.unlocked,
        totalPoints = progress.totalPoints,
        unlockedPoints = progress.unlockedPoints,
        stateToken = progress.state.name
    )

    private companion object {
        const val UNLOCK_DATES_FRESH_SECONDS = 600L
    }
}
