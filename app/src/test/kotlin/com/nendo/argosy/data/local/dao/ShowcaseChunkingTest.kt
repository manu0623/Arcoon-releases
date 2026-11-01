package com.nendo.argosy.data.local.dao

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ShowcaseChunkingTest {

    private val dao = mockk<GameDao>()
    private val ids = (1L..1500L).toList()

    @Test
    fun `stats over more ids than SQLite binds are summed across chunks`() = runBlocking {
        coEvery { dao.statsForGames(match { it.first() == 1L }, 7L) } returns stats(10, 1995, 2001)
        coEvery { dao.statsForGames(match { it.first() != 1L }, 7L) } returns stats(4, 1989, 1999)

        val total = dao.statsForGamesChunked(ids, 7L)!!

        coVerify(exactly = 2) { dao.statsForGames(match { it.size <= 900 }, 7L) }
        assertEquals(14, total.gameCount)
        assertEquals(1989, total.earliestYear)
        assertEquals(2001, total.latestYear)
    }

    @Test
    fun `covers from every chunk are ranked together before the limit applies`() = runBlocking {
        coEvery { dao.coverCandidatesForGames(match { it.first() == 1L }, null, 2) } returns listOf(
            ShowcaseCoverCandidate("a.png", installed = false, isFavorite = true, rating = 90f, sortTitle = "a"),
            ShowcaseCoverCandidate("b.png", installed = false, isFavorite = false, rating = null, sortTitle = "b")
        )
        coEvery { dao.coverCandidatesForGames(match { it.first() != 1L }, null, 2) } returns listOf(
            ShowcaseCoverCandidate("c.png", installed = true, isFavorite = false, rating = 10f, sortTitle = "c")
        )

        assertEquals(listOf("c.png", "a.png"), dao.coverPathsForGamesChunked(ids, null, 2))
    }

    private fun stats(count: Int, earliest: Int, latest: Int) = PlatformShowcaseStats(
        platformId = 0L,
        gameCount = count,
        installedCount = 0,
        achievementsEarned = 0,
        achievementsTotal = 0,
        playTimeMinutes = 0,
        earliestYear = earliest,
        latestYear = latest
    )
}
