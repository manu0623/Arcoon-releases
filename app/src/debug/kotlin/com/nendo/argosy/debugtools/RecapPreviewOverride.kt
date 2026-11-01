package com.nendo.argosy.debugtools

import android.util.Log
import com.nendo.argosy.data.local.dao.AchievementDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlaySessionDao
import com.nendo.argosy.data.local.entity.AchievementEntity
import com.nendo.argosy.data.local.entity.PlaySessionEntity
import com.nendo.argosy.data.emulator.RaUnlockSnapshots
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.RaMetadataSource
import com.nendo.argosy.data.repository.RaUnlockEvidence
import com.nendo.argosy.domain.model.LaunchRecap
import com.nendo.argosy.domain.model.SessionUnlock
import com.nendo.argosy.domain.usecase.recap.GetSessionUnlocksUseCase
import com.nendo.argosy.domain.usecase.recap.PreLaunchRecapOverride
import com.nendo.argosy.domain.usecase.recap.RecapEligibility
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debug-only recap for a game requested through [RecapPreviewRequests]. Shows the last session's
 * unlocks when it has any, else the game's [RecapPreviewUnlocks.RECENT_LIMIT] newest dated unlocks
 * from [RaMetadataSource] whatever their date.
 */
@Singleton
class RecapPreviewOverride @Inject constructor(
    private val requests: RecapPreviewRequests,
    private val preferencesRepository: UserPreferencesRepository,
    private val gameDao: GameDao,
    private val playSessionDao: PlaySessionDao,
    private val metadataSource: RaMetadataSource,
    private val achievementDao: AchievementDao,
    private val getSessionUnlocks: GetSessionUnlocksUseCase
) : PreLaunchRecapOverride {

    override suspend fun consume(gameId: Long): Boolean = requests.consume(gameId)

    override suspend fun recapFor(gameId: Long, gameTitle: String): LaunchRecap? = recapFor(gameId, gameTitle, Instant.now())

    suspend fun recapFor(gameId: Long, gameTitle: String, now: Instant): LaunchRecap? {
        val owner = preferencesRepository.userPreferences.first().rommUserId
        val session = playSessionDao.getLatestForGameAndOwner(gameId, owner)
        val lastSession = session?.let { RecapEligibility.lastSessionOf(it, now) }
        val raGameId = session?.raGameId ?: gameDao.getById(gameId)?.let { it.effectiveRaId ?: it.raId }
        if (raGameId == null) {
            Log.w(TAG, "game $gameId has no RetroAchievements id, preview shows no unlocks")
            return RecapEligibility.launchRecapFor(lastSession, gameTitle, emptyList())
        }
        val sessionUnlocks = session?.let {
            getSessionUnlocks.inWindow(
                gameId,
                raGameId,
                it.ownerUserId,
                it.startTime,
                it.endTime,
                RaUnlockSnapshots.decode(it.raSnapshotUnlocks)
            )
        }.orEmpty()
        val unlocks = sessionUnlocks.ifEmpty { recentUnlocks(gameId, raGameId, owner, session, now) }
        Log.i(TAG, "preview for game $gameId: ${unlocks.size} unlocks, from last session=${sessionUnlocks.isNotEmpty()}")
        return RecapEligibility.launchRecapFor(lastSession, gameTitle, unlocks)
    }

    private suspend fun recentUnlocks(
        gameId: Long,
        raGameId: Long,
        owner: Long?,
        session: PlaySessionEntity?,
        now: Instant
    ): List<SessionUnlock> {
        val evidence = metadataSource.unlockEvidence(raGameId, session?.endTime ?: now)
        if (evidence !is RaUnlockEvidence.Dated) return emptyList()
        val recent = RecapPreviewUnlocks.mostRecent(evidence.earned)
        if (recent.isEmpty()) return emptyList()
        return GetSessionUnlocksUseCase.resolve(
            recent,
            achievementDao.getByGameId(gameId, owner ?: AchievementEntity.NO_OWNER)
        )
    }

    private companion object {
        const val TAG = "RecapHarness"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RecapPreviewModule {
    @Binds
    @IntoSet
    abstract fun bindRecapPreviewOverride(override: RecapPreviewOverride): PreLaunchRecapOverride
}
