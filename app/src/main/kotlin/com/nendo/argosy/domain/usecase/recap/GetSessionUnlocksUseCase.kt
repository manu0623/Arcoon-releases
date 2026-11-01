package com.nendo.argosy.domain.usecase.recap

import com.nendo.argosy.data.emulator.RaUnlockSnapshots
import com.nendo.argosy.data.local.dao.AchievementDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.AchievementEntity
import com.nendo.argosy.data.local.entity.PlaySessionEntity
import com.nendo.argosy.data.repository.RA_BADGE_BASE_URL
import com.nendo.argosy.data.repository.RaMetadataSource
import com.nendo.argosy.data.repository.RaUnlockEvidence
import com.nendo.argosy.domain.model.SessionUnlock
import com.nendo.argosy.domain.model.SnapshotUnlock
import com.nendo.argosy.domain.model.WindowUnlock
import java.time.Instant
import javax.inject.Inject

/**
 * The achievements unlocked during one play session, from whichever source [RaMetadataSource]
 * picks. Never registers a play session with RetroAchievements and never uses the RA password.
 */
class GetSessionUnlocksUseCase @Inject constructor(
    private val metadataSource: RaMetadataSource,
    private val achievementDao: AchievementDao,
    private val gameDao: GameDao
) {
    suspend operator fun invoke(session: PlaySessionEntity): List<SessionUnlock> {
        val raGameId = session.raGameId
            ?: gameDao.getById(session.gameId)?.let { it.effectiveRaId ?: it.raId }
            ?: return emptyList()
        return inWindow(
            session.gameId,
            raGameId,
            session.ownerUserId,
            session.startTime,
            session.endTime,
            RaUnlockSnapshots.decode(session.raSnapshotUnlocks)
        )
    }

    /**
     * The unlocks of [raGameId] earned inside [start, end], resolved against [gameId]'s local
     * achievement rows for [ownerUserId]. [snapshotUnlocks] is the session's stored snapshot
     * difference, if one was taken.
     */
    suspend fun inWindow(
        gameId: Long,
        raGameId: Long,
        ownerUserId: Long?,
        start: Instant,
        end: Instant,
        snapshotUnlocks: List<SnapshotUnlock>? = null
    ): List<SessionUnlock> {
        val owner = ownerUserId ?: AchievementEntity.NO_OWNER
        return when (val evidence = metadataSource.unlockEvidence(raGameId, end, snapshotUnlocks)) {
            is RaUnlockEvidence.Dated -> {
                val unlocks = UnlockWindow.unlocksWithin(evidence.earned, start, end)
                if (unlocks.isEmpty()) emptyList() else resolve(unlocks, achievementDao.getByGameId(gameId, owner))
            }
            is RaUnlockEvidence.Snapshot -> {
                if (evidence.unlocks.isEmpty()) {
                    emptyList()
                } else {
                    resolveSnapshot(evidence.unlocks, evidence.at, achievementDao.getByGameId(gameId, owner))
                }
            }
        }
    }

    companion object {
        /**
         * The badge id of an achievement row, read off its RetroAchievements badge url
         * (`<RA_BADGE_BASE_URL><badgeId>.png`). Null for a url of any other shape.
         */
        fun badgeIdOf(badgeUrl: String?): String? {
            if (badgeUrl == null || !badgeUrl.startsWith(RA_BADGE_BASE_URL)) return null
            return badgeUrl.removePrefix(RA_BADGE_BASE_URL).removeSuffix(BADGE_EXTENSION)
                .takeIf { it.isNotEmpty() && '/' !in it }
        }

        fun resolve(unlocks: List<WindowUnlock>, definitions: List<AchievementEntity>): List<SessionUnlock> {
            val byBadgeId = definitions.mapNotNull { row -> badgeIdOf(row.badgeUrl)?.let { it to row } }.toMap()
            return unlocks.map { unlock ->
                val row = byBadgeId[unlock.badgeId]
                SessionUnlock(
                    badgeId = unlock.badgeId,
                    title = row?.title,
                    points = row?.points,
                    badgePath = row?.cachedBadgeUrl ?: row?.badgeUrl
                        ?: "$RA_BADGE_BASE_URL${unlock.badgeId}$BADGE_EXTENSION",
                    isHardcore = unlock.isHardcore,
                    unlockedAt = unlock.unlockedAt,
                    description = row?.description,
                    type = row?.type,
                    gameTitle = null
                )
            }
        }

        /**
         * Snapshot unlocks matched to local rows by achievement id, each dated [at]. An unlock with
         * no local row, or a row without a RetroAchievements badge url, is left out.
         */
        fun resolveSnapshot(
            unlocks: List<SnapshotUnlock>,
            at: Instant,
            definitions: List<AchievementEntity>
        ): List<SessionUnlock> {
            val byAchievementId = definitions.associateBy { it.raId }
            val window = unlocks.mapNotNull { unlock ->
                val badgeId = badgeIdOf(byAchievementId[unlock.achievementId]?.badgeUrl) ?: return@mapNotNull null
                WindowUnlock(badgeId, at, unlock.isHardcore)
            }
            return resolve(window, definitions)
        }

        private const val BADGE_EXTENSION = ".png"
    }
}
