package com.nendo.argosy.domain.usecase.journey

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.repository.AchievementJourneyRepository
import com.nendo.argosy.domain.usecase.achievement.FetchAchievementsUseCase
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * A game's written journey. Achievements are read again from RomM first when none are stored yet
 * or the stored list is older than [REFRESH_AFTER_MS], so a game never opened in the details
 * screen still gets its history. Without a RomM link the stored rows are all there is. The game's
 * stored play sessions supply the session history and the latest reported status.
 */
class LoadJourneyStoryUseCase @Inject constructor(
    private val repository: AchievementJourneyRepository,
    private val fetchAchievements: FetchAchievementsUseCase,
    private val gameDao: GameDao,
    private val playSessionDao: com.nendo.argosy.data.local.dao.PlaySessionDao
) {
    suspend operator fun invoke(gameId: Long, platformName: String?): JourneyStory? {
        val game = gameDao.getById(gameId) ?: return null
        val fetchedAt = game.achievementsFetchedAt
        val stale = fetchedAt == null || System.currentTimeMillis() - fetchedAt > REFRESH_AFTER_MS
        val rommId = game.rommId
        if (stale && rommId != null) {
            runCatching { fetchAchievements(gameId, rommId, game.raId, refreshProgression = false) }
        }
        val journey = repository.load(gameId, platformName) ?: return null
        val records = playSessionDao.observeByGame(gameId).first().map { session ->
            JourneySessionRecord(
                start = session.startTime,
                end = session.endTime,
                activePlayMs = session.activePlayMs,
                status = session.richPresence,
                statusAt = session.richPresenceAt
            )
        }
        return JourneyStoryComposer.compose(journey, records)
    }

    private companion object {
        const val REFRESH_AFTER_MS = 60 * 60 * 1000L
    }
}
