package com.nendo.argosy.data.sync

import com.nendo.argosy.data.local.dao.RAPatchCacheDao
import com.nendo.argosy.data.remote.ra.RAPatchData
import com.nendo.argosy.data.repository.RAPatchCacheStore
import com.nendo.argosy.data.repository.RAPatchFetchResult
import com.nendo.argosy.data.repository.RetroAchievementsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RAPatchBackfillTest {

    private val dao = mockk<RAPatchCacheDao>(relaxed = true)
    private val store = RAPatchCacheStore(dao)
    private val raRepository = mockk<RetroAchievementsRepository>()
    private val backfill = RAPatchBackfill(raRepository, store)
    private val fetched = RAPatchFetchResult.Fetched(RAPatchData(id = 0L))

    private fun library(installed: List<Long>, cached: List<Long>) {
        coEvery { raRepository.activeOwnerUserId() } returns 7L
        coEvery { dao.getInstalledRaGameIds() } returns installed
        coEvery { dao.getCachedRaGameIds(7L) } returns cached
        coEvery { dao.get(any(), 7L) } returns null
    }

    @Test
    fun `the selection is installed games minus those already cached for the account`() = runTest {
        library(installed = listOf(10L, 20L, 30L, 40L), cached = listOf(20L, 40L, 99L))

        assertEquals(listOf(10L, 30L), store.uncachedInstalledRaGameIds(7L))
    }

    @Test
    fun `cached games are never requested and requests are spaced two seconds apart`() = runTest {
        library(installed = listOf(10L, 20L, 30L), cached = listOf(20L))
        coEvery { raRepository.fetchPatchData(any()) } returns fetched

        val outcome = backfill.run()

        assertEquals(RAPatchBackfill.Outcome.COMPLETE, outcome)
        coVerify(exactly = 1) { raRepository.fetchPatchData(10L) }
        coVerify(exactly = 1) { raRepository.fetchPatchData(30L) }
        coVerify(exactly = 0) { raRepository.fetchPatchData(20L) }
        assertEquals(RAPatchBackfill.REQUEST_SPACING_MS, testScheduler.currentTime)
    }

    @Test
    fun `throttling stops the pass and asks to retry later`() = runTest {
        library(installed = listOf(10L, 20L, 30L), cached = emptyList())
        coEvery { raRepository.fetchPatchData(10L) } returns fetched
        coEvery { raRepository.fetchPatchData(20L) } returns RAPatchFetchResult.RetryLater

        val outcome = backfill.run()

        assertEquals(RAPatchBackfill.Outcome.RETRY_LATER, outcome)
        coVerify(exactly = 0) { raRepository.fetchPatchData(30L) }
    }

    @Test
    fun `a game with no definitions does not stop the pass`() = runTest {
        library(installed = listOf(10L, 20L), cached = emptyList())
        coEvery { raRepository.fetchPatchData(10L) } returns RAPatchFetchResult.Unavailable
        coEvery { raRepository.fetchPatchData(20L) } returns fetched

        assertEquals(RAPatchBackfill.Outcome.COMPLETE, backfill.run())
        coVerify(exactly = 1) { raRepository.fetchPatchData(20L) }
    }

    @Test
    fun `a game cached by a launch or download during the pass is skipped`() = runTest {
        library(installed = listOf(10L, 20L), cached = emptyList())
        coEvery { dao.get(20L, 7L) } returns mockk(relaxed = true)
        coEvery { raRepository.fetchPatchData(10L) } returns fetched

        backfill.run()

        coVerify(exactly = 0) { raRepository.fetchPatchData(20L) }
    }
}
