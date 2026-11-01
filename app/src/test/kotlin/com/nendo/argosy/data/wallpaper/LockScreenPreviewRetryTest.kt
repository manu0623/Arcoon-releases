package com.nendo.argosy.data.wallpaper

import com.nendo.argosy.data.repository.GameRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LockScreenPreviewRetryTest {

    private val scenes = LockScreenScenes()
    private val gameRepository = mockk<GameRepository>()
    private val mosaic = LockScreenScene.Mosaic(rows = emptyList(), dimAlpha = 0)

    private suspend fun buildFromLibrary(): LockScreenScene? {
        val covers = gameRepository.coversOnePerTitle().distinct()
        return if (covers.isEmpty()) null else mosaic
    }

    @Test
    fun `an empty library is retried and the mosaic shows once covers arrive`() = runTest {
        coEvery { gameRepository.coversOnePerTitle() } returnsMany listOf(emptyList(), listOf("/covers/a.png"))

        val job = launch { LockScreenArtManager.showWhenReady(scenes, PREVIEW_RETRY_MS) { buildFromLibrary() } }
        runCurrent()

        assertNull(scenes.scene.value)
        assertTrue(job.isActive)

        advanceTimeBy(PREVIEW_RETRY_MS)
        runCurrent()

        assertSame(mosaic, scenes.scene.value)
        assertTrue(job.isCompleted)
        coVerify(exactly = 2) { gameRepository.coversOnePerTitle() }
    }

    @Test
    fun `cancelling the preview job stops the retries`() = runTest {
        coEvery { gameRepository.coversOnePerTitle() } returns emptyList()

        val job = launch { LockScreenArtManager.showWhenReady(scenes, PREVIEW_RETRY_MS) { buildFromLibrary() } }
        runCurrent()
        advanceTimeBy(PREVIEW_RETRY_MS)
        runCurrent()
        coVerify(exactly = 2) { gameRepository.coversOnePerTitle() }

        job.cancel()
        advanceTimeBy(PREVIEW_RETRY_MS * 5)
        runCurrent()

        assertFalse(job.isActive)
        assertNull(scenes.scene.value)
        coVerify(exactly = 2) { gameRepository.coversOnePerTitle() }
    }
}
