package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.dao.PlaySessionDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Response
import java.time.Instant

class RomMLibrarySyncServerRemovedTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val platform = RomMPlatform(id = 1L, slug = "snes", name = "SNES", fsSlug = "snes", romCount = 0)
    private val romSources = listOf(GameSource.ROMM_REMOTE, GameSource.ROMM_SYNCED)

    private lateinit var api: RomMApi
    private lateinit var apiClient: RomMApiClient
    private lateinit var gameDao: GameDao
    private lateinit var gameFileDao: GameFileDao
    private lateinit var gameDiscDao: GameDiscDao
    private lateinit var saveCacheDao: SaveCacheDao
    private lateinit var playSessionDao: PlaySessionDao
    private lateinit var gameRepository: GameRepository
    private lateinit var visibilityService: RomMVisibilityService
    private lateinit var service: RomMLibrarySyncService

    private var dirty: List<GameEntity> = emptyList()

    private fun game(
        id: Long = 9L,
        rommId: Long? = 42L,
        localPath: String? = null,
        source: GameSource = GameSource.ROMM_REMOTE
    ) = GameEntity(
        id = id,
        platformId = 1L,
        platformSlug = "snes",
        title = "Rockman X4",
        sortTitle = "rockman x4",
        localPath = localPath,
        rommId = rommId,
        igdbId = null,
        source = source,
        syncDirty = true
    )

    @Before
    fun setup() {
        api = mockk(relaxed = true)
        apiClient = mockk(relaxed = true)
        gameDao = mockk(relaxed = true)
        gameFileDao = mockk(relaxed = true)
        gameDiscDao = mockk(relaxed = true)
        saveCacheDao = mockk(relaxed = true)
        playSessionDao = mockk(relaxed = true)
        gameRepository = mockk(relaxed = true)
        visibilityService = mockk(relaxed = true)
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
        coEvery { api.getPlatforms() } returns Response.success(listOf(platform))
        coEvery { api.getPlatform(1L) } returns Response.success(platform)
        coEvery { api.getRoms(any()) } returns Response.success(RomMRomPage(items = emptyList(), total = 0))
        coEvery { platformDao.getById(1L) } returns mockk<PlatformEntity>(relaxed = true) {
            every { slug } returns "snes"
            every { syncEnabled } returns true
        }
        coEvery { overlayWriter.activeOwnerId() } returns null
        coEvery { visibilityService.fetch(any()) } returns RomMVisibility.Known(emptySet(), emptySet(), isAdmin = false)
        coEvery { apiClient.getRomIdentifiers() } returns RomMResult.Success(setOf(777L))
        coEvery { gameRepository.romStorageVolumesReadable() } returns true
        coEvery { gameFileDao.getDownloadedCount(any()) } returns 0
        coEvery { gameDiscDao.getDiscsForGame(any()) } returns emptyList()
        coEvery { gameDao.getSyncDirtyGames(1L, any()) } answers { dirty }
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
            saveCacheDao = saveCacheDao,
            localRomFiles = LocalRomFiles(gameFileDao, gameDiscDao),
            platformDao = platformDao,
            emulatorConfigDao = mockk(relaxed = true),
            platformLibretroSettingsDao = mockk(relaxed = true),
            playSessionDao = playSessionDao,
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
            siblingSplitRepair = mockk(relaxed = true),
            siblingConfigCarryOver = mockk(relaxed = true),
            siblingGroupRepository = mockk(relaxed = true),
            variantFileCleanup = mockk(relaxed = true),
            gameArtDao = mockk(relaxed = true),
            gameScreenshotDao = mockk(relaxed = true)
        )
    }

    @Test
    fun `a server-deleted game with no rom files is removed`() = runTest {
        dirty = listOf(game())

        val result = service.syncPlatform(1L)

        assertEquals(1, result.gamesDeleted)
        coVerify(exactly = 1) { gameDao.delete(9L) }
    }

    @Test
    fun `a server-deleted game with rom files is kept and marked as removed from RomM`() = runTest {
        dirty = listOf(game(localPath = "/roms/snes/x4.cue", source = GameSource.ROMM_SYNCED))
        val kept = slot<GameEntity>()
        coEvery { gameDao.insert(capture(kept)) } returns 9L

        val result = service.syncPlatform(1L)

        assertEquals(0, result.gamesDeleted)
        coVerify(exactly = 0) { gameDao.delete(9L) }
        assertEquals(-9L, kept.captured.rommId)
        assertTrue(kept.captured.isRemovedFromRomm)
    }

    @Test
    fun `a game held only by cached saves is removed and its cache files stay on disk`() = runTest {
        val cachedSave = temp.newFile("x4.srm").apply { writeText("save") }
        dirty = listOf(game())

        service.syncPlatform(1L)

        coVerify(exactly = 1) { gameDao.delete(9L) }
        assertTrue(cachedSave.exists())
        assertEquals("save", cachedSave.readText())
        verify { saveCacheDao wasNot io.mockk.Called }
    }

    @Test
    fun `removing a game leaves its play sessions untouched`() = runTest {
        dirty = listOf(game())

        service.syncPlatform(1L)

        coVerify(exactly = 1) { gameDao.delete(9L) }
        verify { playSessionDao wasNot io.mockk.Called }
    }

    @Test
    fun `a previously preserved row whose files are gone is removed on the next platform sweep`() = runTest {
        dirty = listOf(game(rommId = -9L))

        val result = service.syncPlatform(1L)

        assertEquals(1, result.gamesDeleted)
        coVerify(exactly = 1) { gameDao.delete(9L) }
    }

    @Test
    fun `a previously preserved row that still has rom files stays without being rewritten`() = runTest {
        dirty = listOf(game(rommId = -9L, localPath = "/roms/snes/x4.cue", source = GameSource.ROMM_SYNCED))

        service.syncPlatform(1L)

        coVerify(exactly = 0) { gameDao.delete(9L) }
        coVerify(exactly = 0) { gameDao.insert(match { it.id == 9L }) }
    }

    @Test
    fun `a stale localPath cleared by validation does not preserve the row`() = runTest {
        dirty = listOf(game(localPath = "/roms/snes/gone.cue", source = GameSource.ROMM_SYNCED))
        coEvery { gameRepository.validateLocalFilesForPlatform(1L) } answers {
            dirty = dirty.map { it.copy(localPath = null, source = GameSource.ROMM_REMOTE) }
            1
        }

        val result = service.syncPlatform(1L)

        assertEquals(1, result.gamesDeleted)
        coVerify(exactly = 1) { gameDao.delete(9L) }
        coVerify(exactly = 0) { gameDao.insert(match { it.rommId == -9L }) }
    }

    @Test
    fun `an unreadable rom volume withholds removal of a row with no rom files`() = runTest {
        dirty = listOf(game(rommId = -9L))
        coEvery { gameRepository.romStorageVolumesReadable() } returns false

        service.syncPlatform(1L)

        coVerify(exactly = 0) { gameDao.delete(9L) }
    }

    @Test
    fun `the library sweep removes a previously preserved row once its files are gone`() = runTest {
        val preserved = game(rommId = -9L)
        coEvery { gameDao.getServerRemovedIdsForOwner(romSources, null) } returns
            listOf(ServerBackedGameRef(id = 9L, rommId = -9L, platformId = 1L))
        coEvery { gameDao.getById(9L) } returns preserved

        val result = service.syncLibraryChanges(Instant.parse("2026-10-01T00:00:00Z"))

        assertEquals(1, result.gamesDeleted)
        coVerify(exactly = 1) { gameDao.delete(9L) }
    }

    @Test
    fun `the library sweep removes a preserved row even when rom identifiers are unavailable`() = runTest {
        coEvery { apiClient.getRomIdentifiers() } returns RomMResult.Error("offline")
        coEvery { gameDao.getServerRemovedIdsForOwner(romSources, null) } returns
            listOf(ServerBackedGameRef(id = 9L, rommId = -9L, platformId = 1L))
        coEvery { gameDao.getById(9L) } returns game(rommId = -9L)

        service.syncLibraryChanges(Instant.parse("2026-10-01T00:00:00Z"))

        coVerify(exactly = 1) { gameDao.delete(9L) }
    }

    @Test
    fun `the library sweep validates files before deciding, so a stale path does not preserve`() = runTest {
        var stored = game(localPath = "/roms/snes/gone.cue", source = GameSource.ROMM_SYNCED)
        coEvery { gameDao.getServerBackedIdsForOwner(romSources, null) } returns
            listOf(ServerBackedGameRef(id = 9L, rommId = 42L, platformId = 1L))
        coEvery { gameDao.getById(9L) } answers { stored }
        coEvery { gameRepository.validateLocalFilesForPlatform(1L) } answers {
            stored = stored.copy(localPath = null, source = GameSource.ROMM_REMOTE)
            1
        }

        val result = service.syncLibraryChanges(Instant.parse("2026-10-01T00:00:00Z"))

        assertEquals(1, result.gamesDeleted)
        coVerify(exactly = 1) { gameDao.delete(9L) }
        coVerify(exactly = 0) { gameDao.insert(match { it.rommId == -9L }) }
    }

    @Test
    fun `the library sweep keeps a server-deleted game with a downloaded file row`() = runTest {
        coEvery { gameDao.getServerBackedIdsForOwner(romSources, null) } returns
            listOf(ServerBackedGameRef(id = 9L, rommId = 42L, platformId = 1L))
        coEvery { gameDao.getById(9L) } returns game()
        coEvery { gameFileDao.getDownloadedCount(9L) } returns 1

        val result = service.syncLibraryChanges(Instant.parse("2026-10-01T00:00:00Z"))

        assertEquals(0, result.gamesDeleted)
        coVerify(exactly = 0) { gameDao.delete(9L) }
        coVerify(exactly = 1) { gameDao.insert(match { it.id == 9L && it.rommId == -9L }) }
    }

    @Test
    fun `the startup prune removes a preserved row with no rom files without asking the server`() = runTest {
        coEvery { gameDao.getServerRemovedIdsForOwner(romSources, null) } returns
            listOf(ServerBackedGameRef(id = 9L, rommId = -9L, platformId = 1L))
        coEvery { gameDao.getById(9L) } returns game(rommId = -9L)

        assertEquals(1, service.pruneServerRemovedGames())

        coVerify(exactly = 1) { gameDao.delete(9L) }
        coVerify(exactly = 0) { apiClient.getRomIdentifiers() }
        assertNoFileValidation()
    }

    @Test
    fun `the startup prune keeps a preserved row that still has rom files`() = runTest {
        coEvery { gameDao.getServerRemovedIdsForOwner(romSources, null) } returns
            listOf(ServerBackedGameRef(id = 9L, rommId = -9L, platformId = 1L))
        coEvery { gameDao.getById(9L) } returns game(rommId = -9L)
        coEvery { gameFileDao.getDownloadedCount(9L) } returns 1

        assertEquals(0, service.pruneServerRemovedGames())

        coVerify(exactly = 0) { gameDao.delete(9L) }
        assertNoFileValidation()
    }

    @Test
    fun `the startup prune judges a preserved row by its stored path without validating it`() = runTest {
        coEvery { gameDao.getServerRemovedIdsForOwner(romSources, null) } returns
            listOf(ServerBackedGameRef(id = 9L, rommId = -9L, platformId = 1L))
        coEvery { gameDao.getById(9L) } returns
            game(rommId = -9L, localPath = "/roms/snes/x4.cue", source = GameSource.ROMM_SYNCED)

        assertEquals(0, service.pruneServerRemovedGames())

        coVerify(exactly = 0) { gameDao.delete(9L) }
        assertNoFileValidation()
    }

    @Test
    fun `the startup prune withholds when a rom volume cannot be read`() = runTest {
        coEvery { gameDao.getServerRemovedIdsForOwner(romSources, null) } returns
            listOf(ServerBackedGameRef(id = 9L, rommId = -9L, platformId = 1L))
        coEvery { gameDao.getById(9L) } returns game(rommId = -9L)
        coEvery { gameRepository.romStorageVolumesReadable() } returns false

        assertEquals(0, service.pruneServerRemovedGames())

        coVerify(exactly = 0) { gameDao.delete(9L) }
        assertNoFileValidation()
    }

    @Test
    fun `the library sweep still validates the platforms of preserved rows`() = runTest {
        coEvery { gameDao.getServerRemovedIdsForOwner(romSources, null) } returns
            listOf(ServerBackedGameRef(id = 9L, rommId = -9L, platformId = 1L))
        coEvery { gameDao.getById(9L) } returns game(rommId = -9L)

        service.syncLibraryChanges(Instant.parse("2026-10-01T00:00:00Z"))

        coVerify(exactly = 1) { gameRepository.validateLocalFilesForPlatform(1L) }
        coVerify(exactly = 1) { gameRepository.discoverLocalFilesForPlatform(1L) }
    }

    private fun assertNoFileValidation() {
        coVerify(exactly = 0) { gameRepository.validateLocalFilesForPlatform(any()) }
        coVerify(exactly = 0) { gameRepository.discoverLocalFilesForPlatform(any()) }
        coVerify(exactly = 0) { gameRepository.validateDiscLocalFiles(any()) }
        coVerify(exactly = 0) { gameRepository.validateFileLocalFiles(any()) }
    }

    @Test
    fun `the library sweep only asks for server-removed rows under RomM sources`() = runTest {
        service.syncLibraryChanges(Instant.parse("2026-10-01T00:00:00Z"))

        coVerify(exactly = 1) { gameDao.getServerRemovedIdsForOwner(romSources, null) }
    }
}
