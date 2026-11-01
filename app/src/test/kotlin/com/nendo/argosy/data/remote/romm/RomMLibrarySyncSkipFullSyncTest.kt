package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.dao.ServerBackedGameRef
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.SyncFilterPreferences
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.GameUserOverlayWriter
import com.nendo.argosy.data.repository.LocalRomFiles
import com.nendo.argosy.data.repository.SiblingGroupRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.time.Instant

class RomMLibrarySyncSkipFullSyncTest {

    private val synced = RomMPlatform(id = 1L, slug = "snes", name = "SNES", fsSlug = "snes", romCount = 1)
    private val skipped = RomMPlatform(id = 2L, slug = "nes", name = "NES", fsSlug = "nes", romCount = 5000)

    private lateinit var api: RomMApi
    private lateinit var apiClient: RomMApiClient
    private lateinit var gameDao: GameDao
    private lateinit var gameFileDao: GameFileDao
    private lateinit var gameDiscDao: GameDiscDao
    private lateinit var platformDao: PlatformDao
    private lateinit var gameRepository: GameRepository
    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var siblingGroupRepository: SiblingGroupRepository
    private lateinit var siblingSplitRepair: SiblingSplitRepair
    private lateinit var service: RomMLibrarySyncService

    private val romQueries = mutableListOf<Map<String, String>>()

    private fun local(id: Long, slug: String, skipFullSync: Boolean) = PlatformEntity(
        id = id,
        slug = slug,
        fsSlug = slug,
        name = slug.uppercase(),
        shortName = slug.uppercase(),
        romExtensions = "",
        skipFullSync = skipFullSync
    )

    private fun rom(id: Long, platformId: Long, platformSlug: String) = RomMRom(
        id = id,
        platformId = platformId,
        platformSlug = platformSlug,
        name = "Game $id",
        slug = "game-$id",
        fileName = "game$id.bin",
        filePath = "/roms/$platformSlug/game$id.bin",
        igdbId = null,
        mobyId = null,
        summary = null,
        coverSmall = null,
        coverLarge = null,
        regions = null,
        languages = null,
        revision = null,
        crcHash = null,
        md5Hash = null,
        sha1Hash = null
    )

    private fun game(id: Long, rommId: Long, platformId: Long) = GameEntity(
        id = id,
        platformId = platformId,
        platformSlug = "nes",
        title = "Game $id",
        sortTitle = "game $id",
        localPath = null,
        rommId = rommId,
        igdbId = null,
        source = GameSource.ROMM_REMOTE
    )

    @Before
    fun setup() {
        api = mockk(relaxed = true)
        apiClient = mockk(relaxed = true)
        gameDao = mockk(relaxed = true)
        gameFileDao = mockk(relaxed = true)
        gameDiscDao = mockk(relaxed = true)
        platformDao = mockk(relaxed = true)
        gameRepository = mockk(relaxed = true)
        preferencesRepository = mockk(relaxed = true)
        siblingGroupRepository = mockk(relaxed = true)
        siblingSplitRepair = mockk(relaxed = true)
        val connectionManager = mockk<RomMConnectionManager>(relaxed = true)
        val overlayWriter = mockk<GameUserOverlayWriter>(relaxed = true)
        val visibilityService = mockk<RomMVisibilityService>(relaxed = true)
        val preferences = mockk<UserPreferences>(relaxed = true)

        every { connectionManager.getApi() } returns api
        every { preferences.boxArtCacheEnabled } returns false
        every { preferences.syncFilters } returns SyncFilterPreferences(deleteOrphans = true)
        every { preferencesRepository.preferences } returns flowOf(preferences)
        coEvery { preferencesRepository.getSyncResumeGeneration() } returns null
        every {
            apiClient.buildRomsQueryParams(any(), any(), any(), any(), any(), any(), any(), any())
        } answers { callOriginal() }
        every { apiClient.buildCoverUrls(any()) } returns emptyList()
        every { apiClient.buildLogoUrls(any()) } returns emptyList()
        every { apiClient.buildBackgroundUrls(any()) } returns emptyList()
        every { apiClient.buildMediaUrl(any()) } returns null
        every { apiClient.buildResourceUrl(any()) } returns null
        coEvery { apiClient.getRomIdentifiers() } returns RomMResult.Success(setOf(41L, 99L))
        coEvery { api.getPlatforms() } returns Response.success(listOf(synced, skipped))
        coEvery { api.getPlatform(2L) } returns Response.success(skipped)
        coEvery { api.getRoms(any()) } answers {
            val params = firstArg<Map<String, String>>()
            romQueries += params
            val items = when (params["platform_ids"]) {
                "1" -> listOf(rom(41L, 1L, "snes"))
                "2" -> listOf(rom(99L, 2L, "nes"))
                else -> emptyList()
            }
            Response.success(RomMRomPage(items = items, total = items.size))
        }
        coEvery { platformDao.getById(1L) } returns local(1L, "snes", skipFullSync = false)
        coEvery { platformDao.getById(2L) } returns local(2L, "nes", skipFullSync = true)
        coEvery { overlayWriter.activeOwnerId() } returns null
        coEvery { visibilityService.fetch(any()) } returns RomMVisibility.Known(emptySet(), emptySet(), isAdmin = false)
        coEvery { gameRepository.romStorageVolumesReadable() } returns true
        coEvery { gameFileDao.getDownloadedCount(any()) } returns 0
        coEvery { gameDiscDao.getDiscsForGame(any()) } returns emptyList()
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
            biosRepository = mockk(relaxed = true),
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
            siblingSplitRepair = siblingSplitRepair,
            siblingConfigCarryOver = mockk(relaxed = true),
            siblingGroupRepository = siblingGroupRepository,
            variantFileCleanup = mockk(relaxed = true),
            gameArtDao = mockk(relaxed = true),
            gameScreenshotDao = mockk(relaxed = true)
        )
    }

    private fun queriedPlatformIds(): List<String?> = romQueries.map { it["platform_ids"] }

    @Test
    fun `a full pass never fetches roms for a platform set to skip it`() = runTest {
        val result = service.syncLibrary()

        assertTrue("errors: ${result.errors}", result.errors.isEmpty())
        assertTrue(queriedPlatformIds().contains("1"))
        assertTrue(queriedPlatformIds().none { it == "2" })
    }

    @Test
    fun `a full pass neither marks nor sweeps a skipped platform's games`() = runTest {
        service.syncLibrary()

        coVerify(exactly = 1) { gameDao.markSyncDirtyForOwner(1L, any(), any()) }
        coVerify(exactly = 0) { gameDao.markSyncDirtyForOwner(2L, any(), any()) }
        coVerify(exactly = 0) { gameDao.getSyncDirtyGames(2L, any()) }
    }

    @Test
    fun `a skipped platform's games the server still lists survive the library sweep`() = runTest {
        coEvery { gameDao.getServerBackedIdsForOwner(any(), any()) } returns
            listOf(ServerBackedGameRef(id = 9L, rommId = 99L, platformId = 2L))
        coEvery { gameDao.getById(9L) } returns game(9L, 99L, 2L)

        val result = service.syncLibrary()

        assertTrue("errors: ${result.errors}", result.errors.isEmpty())
        coVerify(atLeast = 1) { apiClient.getRomIdentifiers() }
        coVerify(exactly = 0) { gameDao.delete(9L) }
    }

    @Test
    fun `a game the server deleted on a skipped platform is still removed by the library sweep`() = runTest {
        coEvery { gameDao.getServerBackedIdsForOwner(any(), any()) } returns
            listOf(ServerBackedGameRef(id = 10L, rommId = 555L, platformId = 2L))
        coEvery { gameDao.getById(10L) } returns game(10L, 555L, 2L)

        val result = service.syncLibrary()

        assertEquals(1, result.gamesDeleted)
        coVerify(exactly = 1) { gameDao.delete(10L) }
    }

    @Test
    fun `a skipped platform is neither progress, resume state nor a synced platform`() = runTest {
        val result = service.syncLibrary()

        assertEquals(1, result.platformsSynced)
        assertEquals(1, service.syncProgress.value.platformsTotal)
        assertEquals(listOf(1L), service.syncProgress.value.platforms.map { it.platformId })
        coVerify(exactly = 1) { preferencesRepository.addSyncResumeCompletedPlatform(1L) }
        coVerify(exactly = 0) { preferencesRepository.addSyncResumeCompletedPlatform(2L) }
    }

    @Test
    fun `a full pass with a skipped platform records its time but not the one-time full pass steps`() = runTest {
        service.syncLibrary()

        coVerify(exactly = 1) { preferencesRepository.setLastRommFullSyncTime(any()) }
        coVerify(exactly = 0) { siblingGroupRepository.completeFullPass() }
        coVerify(exactly = 0) { siblingSplitRepair.runOnce() }
        coVerify { siblingGroupRepository.recomputeAll() }
    }

    @Test
    fun `a full pass that skipped a platform records its time but keeps the changes watermark`() = runTest {
        service.syncLibrary()

        coVerify(exactly = 1) { preferencesRepository.setLastRommSyncTime(any()) }
        coVerify(exactly = 0) { preferencesRepository.setRommChangesWatermark(any()) }
    }

    @Test
    fun `a full pass with nothing skipped records its time and advances the changes watermark`() = runTest {
        coEvery { platformDao.getById(2L) } returns local(2L, "nes", skipFullSync = false)

        service.syncLibrary()

        coVerify(exactly = 1) { preferencesRepository.setLastRommSyncTime(any()) }
        coVerify(exactly = 1) { preferencesRepository.setRommChangesWatermark(any()) }
    }

    @Test
    fun `a full pass with nothing skipped still runs the one-time full pass steps`() = runTest {
        coEvery { platformDao.getById(2L) } returns local(2L, "nes", skipFullSync = false)

        service.syncLibrary()

        assertTrue(queriedPlatformIds().contains("2"))
        coVerify(exactly = 1) { siblingGroupRepository.completeFullPass() }
        coVerify(exactly = 1) { siblingSplitRepair.runOnce() }
    }

    @Test
    fun `platform metadata written during a full pass keeps the skip flag`() = runTest {
        val written = mutableListOf<PlatformEntity>()
        coEvery { platformDao.update(capture(written)) } returns Unit

        service.syncLibrary()

        val skippedRows = written.filter { it.id == 2L }
        assertTrue(skippedRows.isNotEmpty())
        assertTrue(skippedRows.all { it.skipFullSync })
        assertTrue(written.filter { it.id == 1L }.none { it.skipFullSync })
    }

    @Test
    fun `the changes pass still stores roms from a platform set to skip the full pass`() = runTest {
        coEvery { api.getRoms(any()) } returns
            Response.success(RomMRomPage(items = listOf(rom(99L, 2L, "nes")), total = 1))

        val result = service.syncLibraryChanges(Instant.parse("2026-10-01T00:00:00Z"))

        assertTrue("errors: ${result.errors}", result.errors.isEmpty())
        assertEquals(1, result.gamesAdded + result.gamesUpdated)
        coVerify { gameDao.insert(match { it.rommId == 99L && it.platformId == 2L }) }
    }

    @Test
    fun `syncing a skipped platform on its own fetches its roms`() = runTest {
        val result = service.syncPlatform(2L)

        assertTrue("errors: ${result.errors}", result.errors.isEmpty())
        assertEquals(listOf<String?>("2"), queriedPlatformIds())
        coVerify(exactly = 1) { gameDao.markSyncDirtyForOwner(2L, any(), any()) }
    }
}
