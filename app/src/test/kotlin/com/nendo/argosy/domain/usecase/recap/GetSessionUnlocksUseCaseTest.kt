package com.nendo.argosy.domain.usecase.recap

import com.nendo.argosy.data.emulator.RaUnlockSnapshots
import com.nendo.argosy.data.local.dao.AchievementDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.AchievementEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.remote.ra.RAWebAchievementProgress
import com.nendo.argosy.data.remote.ra.RAWebAchievements
import com.nendo.argosy.data.remote.ra.RAWebGameProgress
import com.nendo.argosy.data.remote.romm.RomMEarnedAchievement
import com.nendo.argosy.data.repository.RA_BADGE_BASE_URL
import com.nendo.argosy.data.repository.RaMetadataSource
import com.nendo.argosy.data.repository.RaMetadataSource.Companion.toEarnedAchievements
import com.nendo.argosy.data.repository.RaUnlockEvidence
import com.nendo.argosy.domain.model.SnapshotUnlock
import com.nendo.argosy.domain.model.WindowUnlock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class GetSessionUnlocksUseCaseTest {

    private val start = Instant.parse("2026-10-01T10:00:00Z")
    private val end = Instant.parse("2026-10-01T12:00:00Z")

    private val metadataSource: RaMetadataSource = mockk()
    private val achievementDao: AchievementDao = mockk()
    private val gameDao: GameDao = mockk()
    private val useCase = GetSessionUnlocksUseCase(metadataSource, achievementDao, gameDao)

    private fun row(
        badgeId: String,
        title: String,
        points: Int,
        cached: String? = null,
        description: String? = null,
        type: String? = null,
        raId: Long = badgeId.hashCode().toLong()
    ) = AchievementEntity(
        gameId = 1L,
        raId = raId,
        title = title,
        description = description,
        points = points,
        type = type,
        badgeUrl = "$RA_BADGE_BASE_URL$badgeId.png",
        badgeUrlLock = "$RA_BADGE_BASE_URL${badgeId}_lock.png",
        cachedBadgeUrl = cached,
        ownerUserId = 7L
    )

    @Test
    fun `badge id reads off a retroachievements badge url`() {
        assertEquals("12345", GetSessionUnlocksUseCase.badgeIdOf("${RA_BADGE_BASE_URL}12345.png"))
    }

    @Test
    fun `badge id is null for any other url shape`() {
        assertNull(GetSessionUnlocksUseCase.badgeIdOf("https://romm.local/assets/badges/12345.png"))
        assertNull(GetSessionUnlocksUseCase.badgeIdOf(null))
        assertNull(GetSessionUnlocksUseCase.badgeIdOf("${RA_BADGE_BASE_URL}.png"))
        assertNull(GetSessionUnlocksUseCase.badgeIdOf("${RA_BADGE_BASE_URL}sub/1.png"))
    }

    @Test
    fun `an unlock with no local row keeps its badge and leaves title and points empty`() {
        val resolved = GetSessionUnlocksUseCase.resolve(
            listOf(WindowUnlock("999", start, isHardcore = false)),
            listOf(row("1", "First", 5))
        ).single()
        assertNull(resolved.title)
        assertNull(resolved.points)
        assertNull(resolved.description)
        assertNull(resolved.type)
        assertNull(resolved.gameTitle)
        assertEquals("${RA_BADGE_BASE_URL}999.png", resolved.badgePath)
    }

    @Test
    fun `an unlock takes its description and type token from the local row`() {
        val resolved = GetSessionUnlocksUseCase.resolve(
            listOf(WindowUnlock("1", start, isHardcore = false)),
            listOf(row("1", "First", 5, description = "Beat the first boss", type = "progression"))
        ).single()
        assertEquals("Beat the first boss", resolved.description)
        assertEquals("progression", resolved.type)
    }

    @Test
    fun `a cached badge file wins over the remote url`() {
        val resolved = GetSessionUnlocksUseCase.resolve(
            listOf(WindowUnlock("1", start, isHardcore = true)),
            listOf(row("1", "First", 5, cached = "/cache/1.png"))
        ).single()
        assertEquals("/cache/1.png", resolved.badgePath)
        assertEquals("First", resolved.title)
        assertEquals(5, resolved.points)
        assertTrue(resolved.isHardcore)
    }

    @Test
    fun `dated evidence keeps only the unlocks inside the session and resolves them`() = runTest {
        coEvery { metadataSource.unlockEvidence(55L, end, null) } returns RaUnlockEvidence.Dated(
            listOf(
                RomMEarnedAchievement("1", date = "2026-10-01 11:00:00"),
                RomMEarnedAchievement("2", date = "2026-09-30 11:00:00")
            )
        )
        coEvery { achievementDao.getByGameId(1L, 7L) } returns listOf(row("1", "First", 5))

        val result = useCase(session(gameId = 1L, start = start, activeMinutes = 120, raGameId = 55L))

        assertEquals(listOf("First"), result.map { it.title })
    }

    @Test
    fun `web progress maps into the same unlock shape with UTC dates`() = runTest {
        val web = RAWebGameProgress(
            id = 55L,
            achievements = RAWebAchievements(
                mapOf(
                    "9" to RAWebAchievementProgress(
                        id = 9L,
                        badgeName = "00042",
                        dateEarned = "2026-10-01 10:30:00",
                        dateEarnedHardcore = "2026-10-01 10:30:00"
                    ),
                    "10" to RAWebAchievementProgress(id = 10L, badgeName = "00043", dateEarned = "2026-10-01 09:59:59")
                )
            )
        )
        coEvery { metadataSource.unlockEvidence(55L, end, null) } returns RaUnlockEvidence.Dated(web.toEarnedAchievements())
        coEvery { achievementDao.getByGameId(1L, 7L) } returns listOf(
            row("00042", "Jungle Hijinxs", 10, description = "Clear the first level", type = "progression")
        )

        val unlock = useCase.inWindow(1L, 55L, 7L, start, end).single()

        assertEquals("00042", unlock.badgeId)
        assertEquals("Jungle Hijinxs", unlock.title)
        assertEquals(10, unlock.points)
        assertEquals("Clear the first level", unlock.description)
        assertEquals("progression", unlock.type)
        assertTrue(unlock.isHardcore)
        assertEquals(Instant.parse("2026-10-01T10:30:00Z"), unlock.unlockedAt)
    }

    @Test
    fun `a session recorded without an ra id falls back to the game's`() = runTest {
        val game = mockk<GameEntity> {
            every { effectiveRaId } returns 77L
            every { raId } returns 76L
        }
        coEvery { gameDao.getById(1L) } returns game
        coEvery { metadataSource.unlockEvidence(77L, end, null) } returns RaUnlockEvidence.Dated(
            listOf(RomMEarnedAchievement("1", date = "2026-10-01 11:00:00"))
        )
        coEvery { achievementDao.getByGameId(1L, 7L) } returns emptyList()

        val result = useCase(session(gameId = 1L, start = start, activeMinutes = 120))

        assertEquals(listOf("1"), result.map { it.badgeId })
    }

    @Test
    fun `an explicit window keeps only the unlocks inside it`() = runTest {
        val windowStart = start.minus(Duration.ofDays(30))
        coEvery { metadataSource.unlockEvidence(55L, end, null) } returns RaUnlockEvidence.Dated(
            listOf(
                RomMEarnedAchievement("1", date = "2026-09-10 11:00:00"),
                RomMEarnedAchievement("2", date = "2026-08-01 11:00:00")
            )
        )
        coEvery { achievementDao.getByGameId(1L, 7L) } returns emptyList()

        val result = useCase.inWindow(gameId = 1L, raGameId = 55L, ownerUserId = 7L, start = windowStart, end = end)

        assertEquals(listOf("1"), result.map { it.badgeId })
    }

    @Test
    fun `a session's stored snapshot unlocks reach the source and resolve by achievement id`() = runTest {
        val stored = listOf(SnapshotUnlock(901L, isHardcore = true), SnapshotUnlock(902L, isHardcore = false))
        val sessionRow = session(gameId = 1L, start = start, activeMinutes = 120, raGameId = 55L)
            .copy(raSnapshotUnlocks = RaUnlockSnapshots.encode(stored))
        coEvery { metadataSource.unlockEvidence(55L, end, stored) } returns RaUnlockEvidence.Snapshot(stored, end)
        coEvery { achievementDao.getByGameId(1L, 7L) } returns listOf(
            row("00042", "Hardcore one", 10, raId = 901L),
            row("00043", "Softcore one", 5, raId = 902L)
        )

        val result = useCase(sessionRow)

        assertEquals(listOf("00042", "00043"), result.map { it.badgeId })
        assertEquals(listOf(true, false), result.map { it.isHardcore })
        assertTrue(result.all { it.unlockedAt == end })
    }

    @Test
    fun `a snapshot unlock with no local row is left out`() {
        val resolved = GetSessionUnlocksUseCase.resolveSnapshot(
            listOf(SnapshotUnlock(1L, isHardcore = false), SnapshotUnlock(2L, isHardcore = false)),
            end,
            listOf(row("00042", "Known", 5, raId = 2L))
        )
        assertEquals(listOf("Known"), resolved.map { it.title })
    }

    @Test
    fun `a game with no ra id has no unlocks and never asks a source`() = runTest {
        val game = mockk<GameEntity> {
            every { effectiveRaId } returns null
            every { raId } returns null
        }
        coEvery { gameDao.getById(1L) } returns game

        val result = useCase(session(gameId = 1L, start = start, activeMinutes = 120))

        assertTrue(result.isEmpty())
        coVerify(exactly = 0) { metadataSource.unlockEvidence(any(), any(), any()) }
    }
}
