package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.preferences.SyncFilterPreferences
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.BiosRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.GameUserOverlayWriter
import com.nendo.argosy.data.repository.LocalRomFiles
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class RomMLibrarySyncFirmwareTest {

    private val basePlatform = RomMPlatform(id = 1L, slug = "snes", name = "SNES", fsSlug = "snes", romCount = 0)

    private lateinit var api: RomMApi
    private lateinit var biosRepository: BiosRepository
    private lateinit var service: RomMLibrarySyncService

    @Before
    fun setup() {
        api = mockk(relaxed = true)
        biosRepository = mockk(relaxed = true)
        val apiClient = mockk<RomMApiClient>(relaxed = true)
        val gameDao = mockk<GameDao>(relaxed = true)
        val gameFileDao = mockk<GameFileDao>(relaxed = true)
        val gameDiscDao = mockk<GameDiscDao>(relaxed = true)
        val gameRepository = mockk<GameRepository>(relaxed = true)
        val visibilityService = mockk<RomMVisibilityService>(relaxed = true)
        val platformDao = mockk<com.nendo.argosy.data.local.dao.PlatformDao>(relaxed = true)
        val connectionManager = mockk<RomMConnectionManager>(relaxed = true)
        val overlayWriter = mockk<GameUserOverlayWriter>(relaxed = true)
        val preferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
        val preferences = mockk<UserPreferences>(relaxed = true)

        every { connectionManager.getApi() } returns api
        every { preferences.boxArtCacheEnabled } returns false
        every { preferences.syncFilters } returns SyncFilterPreferences(deleteOrphans = true)
        every { preferencesRepository.preferences } returns flowOf(preferences)
        every {
            apiClient.buildRomsQueryParams(any(), any(), any(), any(), any(), any(), any(), any())
        } answers { callOriginal() }
        every { apiClient.buildMediaUrl(any()) } returns null
        coEvery { api.getRoms(any()) } returns Response.success(RomMRomPage(items = emptyList(), total = 0))
        coEvery { platformDao.getById(1L) } returns mockk<PlatformEntity>(relaxed = true) {
            every { slug } returns "snes"
            every { syncEnabled } returns true
        }
        coEvery { overlayWriter.activeOwnerId() } returns null
        coEvery { visibilityService.fetch(any()) } returns RomMVisibility.Known(emptySet(), emptySet(), isAdmin = false)
        coEvery { apiClient.getRomIdentifiers() } returns RomMResult.Success(emptySet())
        coEvery { gameRepository.romStorageVolumesReadable() } returns true
        coEvery { gameFileDao.getDownloadedCount(any()) } returns 0
        coEvery { gameDiscDao.getDiscsForGame(any()) } returns emptyList()
        coEvery { gameDao.getSyncDirtyGames(1L, any()) } returns emptyList()
        coEvery { gameDao.getServerBackedIdsForOwner(any(), any()) } returns emptyList()
        coEvery { gameDao.getServerRemovedIdsForOwner(any(), any()) } returns emptyList()

        service = RomMLibrarySyncService(
            apiClient = apiClient,
            connectionManager = connectionManager,
            userPreferencesRepository = preferencesRepository,
            database = mockk(relaxed = true),
            gameDao = gameDao,
            homeTileDao = mockk(relaxed = true),
            gameDiscDao = gameDiscDao,
            gameFileDao = gameFileDao,
            saveSyncDao = mockk(relaxed = true),
            saveCacheDao = mockk(relaxed = true),
            localRomFiles = LocalRomFiles(gameFileDao, gameDiscDao),
            platformDao = platformDao,
            emulatorConfigDao = mockk(relaxed = true),
            platformLibretroSettingsDao = mockk(relaxed = true),
            playSessionDao = mockk(relaxed = true),
            firmwareDao = mockk(relaxed = true),
            controllerMappingDao = mockk(relaxed = true),
            collectionDao = mockk(relaxed = true),
            imageCacheManager = mockk(relaxed = true),
            musicDirectoryManager = mockk(relaxed = true),
            gameFileSync = mockk(relaxed = true),
            biosRepository = biosRepository,
            installedAppResolver = mockk(relaxed = true),
            gameRepository = dagger.Lazy { gameRepository },
            overlayWriter = overlayWriter,
            overlayDao = mockk(relaxed = true),
            visibilityService = visibilityService,
            syncVirtualCollectionsUseCase = dagger.Lazy { mockk(relaxed = true) },
            fileAccessLayer = mockk(relaxed = true),
            androidGameScanner = dagger.Lazy { mockk(relaxed = true) },
            attributionRepository = mockk(relaxed = true),
            userRomsHiddenDao = mockk(relaxed = true),
            pendingSyncQueueDao = mockk(relaxed = true),
            siblingSplitRepair = mockk(relaxed = true),
            siblingConfigCarryOver = mockk(relaxed = true),
            siblingGroupRepository = mockk(relaxed = true),
            variantFileCleanup = mockk(relaxed = true),
            gameArtDao = mockk(relaxed = true),
            gameScreenshotDao = mockk(relaxed = true)
        )
    }

    private fun serve(platform: RomMPlatform) {
        coEvery { api.getPlatforms() } returns Response.success(listOf(platform))
        coEvery { api.getPlatform(1L) } returns Response.success(platform)
    }

    @Test
    fun `a platform sync passes an empty firmware array through to the firmware prune`() = runTest {
        serve(basePlatform.copy(firmware = emptyList()))

        service.syncPlatform(1L)

        coVerify(exactly = 1) { biosRepository.syncPlatformFirmware(1L, "snes", emptyList()) }
    }

    @Test
    fun `a platform sync without a firmware field leaves firmware untouched`() = runTest {
        serve(basePlatform.copy(firmware = null))

        service.syncPlatform(1L)

        coVerify(exactly = 0) { biosRepository.syncPlatformFirmware(any(), any(), any()) }
    }
}
