package com.nendo.argosy.data.sync

import com.nendo.argosy.domain.usecase.sync.SyncLibraryResult
import com.nendo.argosy.domain.usecase.sync.SyncLibraryUseCase
import com.nendo.argosy.domain.usecase.sync.SyncPlatformFailureReason
import com.nendo.argosy.domain.usecase.sync.SyncPlatformResult
import com.nendo.argosy.domain.usecase.sync.SyncPlatformUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformSyncQueueTest {

    private val libraryGate = CompletableDeferred<Unit>()
    private val platformRan = CompletableDeferred<Long>()

    private val syncLibrary = mockk<SyncLibraryUseCase> {
        coEvery { this@mockk.invoke(any(), any(), any()) } coAnswers {
            libraryGate.await()
            SyncLibraryResult.AlreadyInProgress
        }
    }
    private val syncPlatform = mockk<SyncPlatformUseCase> {
        coEvery { this@mockk.invoke(any(), any()) } coAnswers {
            platformRan.complete(firstArg())
            SyncPlatformResult.Error(SyncPlatformFailureReason.NotConnected)
        }
    }

    private val queue = PlatformSyncQueue({ syncPlatform }, { syncLibrary })

    @Test
    fun `a platform queued after the library runs once the library sync ends`() = runBlocking {
        assertTrue(queue.enqueueLibrary())
        withTimeout(5_000) { queue.activeJob.first { it is PlatformSyncQueue.Job.Library } }

        queue.enqueuePlatformAfterLibrary(7L, "Saturn")
        coVerify(exactly = 0) { syncPlatform.invoke(any(), any()) }

        libraryGate.complete(Unit)

        assertTrue(withTimeout(5_000) { platformRan.await() } == 7L)
    }

    @Test
    fun `a platform queued after the library runs at once when no library sync is running`() = runBlocking {
        libraryGate.complete(Unit)

        queue.enqueuePlatformAfterLibrary(7L, "Saturn")

        assertTrue(withTimeout(5_000) { platformRan.await() } == 7L)
    }
}
