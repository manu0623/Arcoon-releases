package com.nendo.argosy.ui.startup

import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.data.preferences.SyncFilterPreferences
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.LibraryPointerRepair
import com.nendo.argosy.domain.usecase.libretro.LibretroMigrationUseCase
import com.nendo.argosy.domain.usecase.libretro.MigrationResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test

class StartupMaintenanceCoordinatorTest {

    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var gameRepository: GameRepository
    private lateinit var romMRepository: RomMRepository
    private lateinit var libretroMigrationUseCase: LibretroMigrationUseCase
    private lateinit var notificationManager: NotificationManager
    private lateinit var libraryPointerRepair: LibraryPointerRepair

    @Before
    fun setup() {
        preferencesRepository = mockk(relaxed = true)
        gameRepository = mockk(relaxed = true)
        romMRepository = mockk(relaxed = true)
        libretroMigrationUseCase = mockk(relaxed = true)
        notificationManager = mockk(relaxed = true)
        libraryPointerRepair = mockk(relaxed = true)
        every { preferencesRepository.userPreferences } returns flow { throw IllegalStateException("prefs unreadable") }
        every { romMRepository.isConnected() } returns true
        coEvery { libretroMigrationUseCase.runMigrationIfNeeded() } returns MigrationResult.AlreadyComplete
    }

    private fun coordinator() = StartupMaintenanceCoordinator(
        preferencesRepository,
        gameRepository,
        romMRepository,
        libretroMigrationUseCase,
        notificationManager,
        libraryPointerRepair
    )

    @Test
    fun `the home does not wait on a hanging server call`() = runBlocking {
        coEvery { romMRepository.syncCollections() } coAnswers { awaitCancellation() }

        withTimeout(PASS_TIMEOUT_MS) { coordinator().awaitPass() }

        coVerify(exactly = 1) { libretroMigrationUseCase.cleanupRemovedCores() }
    }

    @Test
    fun `a throwing step does not stop the steps after it or the pointer repair`() = runBlocking {
        coEvery { romMRepository.syncCollections() } throws IllegalStateException("server down")

        withTimeout(PASS_TIMEOUT_MS) { coordinator().awaitPass() }

        coVerify(exactly = 1) { libretroMigrationUseCase.cleanupRemovedCores() }
        verify(timeout = PASS_TIMEOUT_MS, exactly = 1) { libraryPointerRepair.start() }
    }

    @Test
    fun `a throwing blocking step still runs the background steps`() = runBlocking {
        coEvery { libretroMigrationUseCase.cleanupRemovedCores() } throws IllegalStateException("cores dir gone")

        withTimeout(PASS_TIMEOUT_MS) { coordinator().awaitPass() }

        coVerify(timeout = PASS_TIMEOUT_MS, exactly = 1) { romMRepository.syncCollections() }
        verify(timeout = PASS_TIMEOUT_MS, exactly = 1) { libraryPointerRepair.start() }
    }

    @Test
    fun `a second caller joins the completed pass instead of rethrowing a failure`() = runBlocking {
        coEvery { romMRepository.syncCollections() } throws IllegalStateException("server down")
        val coordinator = coordinator()

        withTimeout(PASS_TIMEOUT_MS) {
            coordinator.awaitPass()
            coordinator.awaitPass()
        }

        coVerify(timeout = PASS_TIMEOUT_MS, exactly = 1) { romMRepository.syncCollections() }
        verify(timeout = PASS_TIMEOUT_MS, exactly = 1) { libraryPointerRepair.start() }
    }

    private fun preferencesWith(deleteOrphans: Boolean, weeklyIntegrity: Boolean = false) {
        val preferences = mockk<UserPreferences>(relaxed = true)
        every { preferences.syncFilters } returns SyncFilterPreferences(deleteOrphans = deleteOrphans)
        every { preferences.weeklyIntegrityCheckEnabled } returns weeklyIntegrity
        every { preferences.lastIntegrityCheckTime } returns null
        every { preferencesRepository.userPreferences } returns flowOf(preferences)
    }

    @Test
    fun `the server-removed prune is skipped when deleting orphans is off`() = runBlocking {
        preferencesWith(deleteOrphans = false)

        withTimeout(PASS_TIMEOUT_MS) { coordinator().awaitPass() }

        coVerify(timeout = PASS_TIMEOUT_MS, exactly = 1) { romMRepository.syncCollections() }
        coVerify(exactly = 0) { romMRepository.pruneServerRemovedGames() }
    }

    @Test
    fun `the server-removed prune runs when deleting orphans is on`() = runBlocking {
        preferencesWith(deleteOrphans = true)

        withTimeout(PASS_TIMEOUT_MS) { coordinator().awaitPass() }

        coVerify(timeout = PASS_TIMEOUT_MS, exactly = 1) { romMRepository.pruneServerRemovedGames() }
    }

    @Test
    fun `the server-removed prune runs after a due integrity check`() = runBlocking {
        preferencesWith(deleteOrphans = true, weeklyIntegrity = true)

        withTimeout(PASS_TIMEOUT_MS) { coordinator().awaitPass() }

        verify(timeout = PASS_TIMEOUT_MS, exactly = 1) { libraryPointerRepair.start() }
        coVerifyOrder {
            gameRepository.validateLocalFiles()
            gameRepository.discoverLocalFiles()
            romMRepository.pruneServerRemovedGames()
        }
    }

    private companion object {
        const val PASS_TIMEOUT_MS = 5_000L
    }
}
