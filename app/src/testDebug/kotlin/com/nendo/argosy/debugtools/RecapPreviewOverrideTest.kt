package com.nendo.argosy.debugtools

import com.nendo.argosy.data.local.dao.AchievementDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlaySessionDao
import com.nendo.argosy.data.local.entity.AchievementEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PlaySessionEntity
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMEarnedAchievement
import com.nendo.argosy.data.repository.RA_BADGE_BASE_URL
import com.nendo.argosy.data.repository.RaMetadataSource
import com.nendo.argosy.data.repository.RaUnlockEvidence
import com.nendo.argosy.domain.model.SnapshotUnlock
import com.nendo.argosy.domain.model.SessionUnlock
import com.nendo.argosy.domain.usecase.recap.GetSessionUnlocksUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class RecapPreviewOverrideTest {

    private val now = Instant.parse("2026-10-13T12:00:00Z")
    private val sessionStart = Instant.parse("2026-10-12T10:00:00Z")
    private val sessionEnd = Instant.parse("2026-10-12T11:00:00Z")

    private val preferencesRepository: UserPreferencesRepository = mockk {
        every { userPreferences } returns flowOf(UserPreferences(rommUserId = 7L))
    }
    private val gameDao: GameDao = mockk()
    private val playSessionDao: PlaySessionDao = mockk()
    private val metadataSource: RaMetadataSource = mockk()
    private val achievementDao: AchievementDao = mockk()
    private val getSessionUnlocks: GetSessionUnlocksUseCase = mockk()

    private val override = RecapPreviewOverride(
        requests = mockk(),
        preferencesRepository = preferencesRepository,
        gameDao = gameDao,
        playSessionDao = playSessionDao,
        metadataSource = metadataSource,
        achievementDao = achievementDao,
        getSessionUnlocks = getSessionUnlocks
    )

    private fun session(richPresence: String? = null) = PlaySessionEntity(
        userId = null,
        gameId = 1L,
        igdbId = null,
        gameTitle = "Donkey Kong Country",
        platformSlug = "snes",
        startTime = sessionStart,
        endTime = sessionEnd,
        deviceId = "device",
        deviceManufacturer = "maker",
        deviceModel = "model",
        activePlayMs = Duration.ofHours(1).toMillis(),
        ownerUserId = 7L,
        raGameId = 337L,
        richPresence = richPresence
    )

    private fun sessionUnlock(id: String) =
        SessionUnlock(
            id,
            "Session $id",
            5,
            "/badge/$id.png",
            isHardcore = false,
            unlockedAt = sessionStart,
            description = "Reach world $id",
            type = null,
            gameTitle = null
        )

    private fun gameWithRaId(raGameId: Long?) {
        val game = mockk<GameEntity> {
            every { effectiveRaId } returns raGameId
            every { raId } returns raGameId
        }
        coEvery { gameDao.getById(1L) } returns game
    }

    @Test
    fun `a last session with unlocks shows those unlocks`() = runTest {
        coEvery { playSessionDao.getLatestForGameAndOwner(1L, 7L) } returns session()
        coEvery { getSessionUnlocks.inWindow(1L, 337L, 7L, sessionStart, sessionEnd) } returns listOf(sessionUnlock("9"))

        val recap = override.recapFor(1L, "Donkey Kong Country", now)!!

        assertEquals(listOf("9"), recap.unlocks.map { it.badgeId })
        assertEquals(1L, recap.lastSession!!.daysSince)
    }

    @Test
    fun `a last session without unlocks falls back to the game's months-old unlocks`() = runTest {
        coEvery { playSessionDao.getLatestForGameAndOwner(1L, 7L) } returns session()
        coEvery { getSessionUnlocks.inWindow(any(), any(), any(), any(), any(), any()) } returns emptyList()
        dated(
            RomMEarnedAchievement("101", date = "2025-12-11 20:10:00"),
            RomMEarnedAchievement("102", date = "2025-12-11 20:40:00")
        )
        coEvery { achievementDao.getByGameId(1L, 7L) } returns emptyList()

        val recap = override.recapFor(1L, "Donkey Kong Country", now)!!

        assertEquals(listOf("102", "101"), recap.unlocks.map { it.badgeId })
        assertEquals("Donkey Kong Country", recap.gameTitle)
    }

    @Test
    fun `fallback unlocks carry the description and type of their local rows`() = runTest {
        coEvery { playSessionDao.getLatestForGameAndOwner(1L, 7L) } returns session()
        coEvery { getSessionUnlocks.inWindow(any(), any(), any(), any(), any(), any()) } returns emptyList()
        dated(RomMEarnedAchievement("102", date = "2025-12-11 20:40:00"))
        coEvery { achievementDao.getByGameId(1L, 7L) } returns listOf(
            AchievementEntity(
                gameId = 1L,
                raId = 102L,
                title = "Jungle Hijinxs",
                description = "Clear the first level",
                points = 10,
                type = "win_condition",
                badgeUrl = "${RA_BADGE_BASE_URL}102.png",
                badgeUrlLock = "${RA_BADGE_BASE_URL}102_lock.png",
                cachedBadgeUrl = null,
                ownerUserId = 7L
            )
        )

        val unlock = override.recapFor(1L, "Donkey Kong Country", now)!!.unlocks.single()

        assertEquals("Clear the first level", unlock.description)
        assertEquals("win_condition", unlock.type)
    }

    @Test
    fun `a game never played shows its newest ten unlocks, newest first`() = runTest {
        coEvery { playSessionDao.getLatestForGameAndOwner(1L, 7L) } returns null
        gameWithRaId(337L)
        dated(
            *(1..12).map { day ->
                RomMEarnedAchievement("$day", dateHardcore = "2025-12-${day.toString().padStart(2, '0')} 10:00:00")
            }.toTypedArray()
        )
        coEvery { achievementDao.getByGameId(1L, 7L) } returns emptyList()

        val recap = override.recapFor(1L, "Donkey Kong Country", now)!!

        assertNull(recap.lastSession)
        assertEquals((12 downTo 3).map { "$it" }, recap.unlocks.map { it.badgeId })
        assertTrue(recap.unlocks.all { it.isHardcore })
    }

    @Test
    fun `a game with no unlocks and no rich presence has nothing to show`() = runTest {
        coEvery { playSessionDao.getLatestForGameAndOwner(1L, 7L) } returns null
        gameWithRaId(337L)
        dated()

        assertNull(override.recapFor(1L, "Donkey Kong Country", now))
    }

    @Test
    fun `a last session with stored snapshot unlocks hands them to the session unlock read`() = runTest {
        val stored = listOf(SnapshotUnlock(901L, isHardcore = true))
        coEvery { playSessionDao.getLatestForGameAndOwner(1L, 7L) } returns
            session().copy(raSnapshotUnlocks = "h:901")
        coEvery { getSessionUnlocks.inWindow(1L, 337L, 7L, sessionStart, sessionEnd, stored) } returns listOf(sessionUnlock("9"))

        val recap = override.recapFor(1L, "Donkey Kong Country", now)!!

        assertEquals(listOf("9"), recap.unlocks.map { it.badgeId })
    }

    @Test
    fun `snapshot evidence never fills the months-old fallback`() = runTest {
        coEvery { playSessionDao.getLatestForGameAndOwner(1L, 7L) } returns session(richPresence = "Jungle Hijinxs")
        coEvery { getSessionUnlocks.inWindow(any(), any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { metadataSource.unlockEvidence(337L, any(), any()) } returns
            RaUnlockEvidence.Snapshot(listOf(SnapshotUnlock(1L, isHardcore = false)), sessionEnd)

        val recap = override.recapFor(1L, "Donkey Kong Country", now)!!

        assertEquals(emptyList<SessionUnlock>(), recap.unlocks)
    }

    private fun dated(vararg earned: RomMEarnedAchievement) {
        coEvery { metadataSource.unlockEvidence(337L, any(), any()) } returns RaUnlockEvidence.Dated(earned.toList())
    }

    @Test
    fun `a game without an ra id still shows its rich presence`() = runTest {
        coEvery { playSessionDao.getLatestForGameAndOwner(1L, 7L) } returns session(richPresence = "Jungle Hijinxs").copy(raGameId = null)
        gameWithRaId(null)

        val recap = override.recapFor(1L, "Donkey Kong Country", now)!!

        assertEquals("Jungle Hijinxs", recap.lastSession!!.richPresence)
        assertEquals(emptyList<SessionUnlock>(), recap.unlocks)
    }
}
