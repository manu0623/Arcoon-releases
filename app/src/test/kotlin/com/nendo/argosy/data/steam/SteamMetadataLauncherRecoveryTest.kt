package com.nendo.argosy.data.steam

import android.content.Context
import android.util.Log
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.remote.steam.SteamAppData
import com.nendo.argosy.data.remote.steam.SteamAppDetailsResponse
import com.nendo.argosy.data.remote.steam.SteamGenre
import com.nendo.argosy.data.remote.steam.SteamMetacritic
import com.nendo.argosy.data.remote.steam.SteamStoreApi
import com.nendo.argosy.data.repository.SteamRepository
import com.nendo.argosy.data.repository.SteamResult
import `in`.dragonbra.javasteam.types.KeyValue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.time.Instant

class SteamMetadataLauncherRecoveryTest {
    private val snapshot = GameEntity(
        id = 1L,
        platformId = 1L,
        platformSlug = "steam",
        title = "Example",
        sortTitle = "example",
        localPath = "/original/install",
        rommId = null,
        igdbId = 123L,
        steamAppId = 1L,
        steamInstallDir = "Original",
        source = GameSource.STEAM,
        steamLauncher = "app.gamenative",
        launcherSetManually = true,
        developer = "Original developer",
        publisher = "Original publisher",
        releaseYear = 2010,
        genre = "Original genre",
        description = "Original description",
        rating = 20f,
        storeEnrichStatus = GameEntity.STORE_FAILED,
        playCount = 7,
        lastPlayed = Instant.parse("2026-01-01T00:00:00Z"),
        addedAt = Instant.parse("2025-01-01T00:00:00Z"),
        saveId = "preserved-save"
    )
    private val recovered = snapshot.copy(
        steamLauncher = "com.droiddeck.launcher",
        launcherSetManually = false,
        localPath = "/current/install",
        playCount = 8,
        lastPlayed = Instant.parse("2026-02-01T00:00:00Z")
    )
    private var current = snapshot
    private val gameDao: GameDao = mockk(relaxed = true) {
        coEvery { getBySteamAppId(1L) } answers { current }
        coEvery { getById(snapshot.id) } answers { current }
        coEvery { update(any()) } answers { current = firstArg() }
        coEvery { updateSteamStoreMetadata(any(), any(), any(), any()) } answers {
            current = current.copy(
                description = arg<String?>(1) ?: current.description,
                genre = arg<String?>(2) ?: current.genre,
                rating = arg<Float?>(3) ?: current.rating
            )
        }
        coEvery { updateSteamInstallInfo(any(), any()) } answers {
            current = current.copy(
                steamLauncher = current.steamLauncher ?: GameEntity.LAUNCHER_UNSPECIFIED,
                steamInstallDir = arg<String?>(1) ?: current.steamInstallDir
            )
        }
        coEvery { updateSteamLibraryMetadata(any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            current = current.copy(
                title = arg<String?>(1) ?: current.title,
                sortTitle = arg<String?>(2) ?: current.sortTitle,
                developer = arg<String?>(3) ?: current.developer,
                publisher = arg<String?>(4) ?: current.publisher,
                releaseYear = arg<Int?>(5) ?: current.releaseYear,
                genre = arg<String?>(6) ?: current.genre,
                steamInstallDir = arg<String?>(7) ?: current.steamInstallDir,
                steamLauncher = current.steamLauncher ?: GameEntity.LAUNCHER_UNSPECIFIED,
                storeEnrichStatus = GameEntity.STORE_NOT_ATTEMPTED,
                igdbId = null
            )
        }
    }
    private val context: Context = mockk(relaxed = true)

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun `store enrichment cannot restore a pin retired during its request`() = runTest {
        val appData = mockk<SteamAppData> {
            every { plainShortDescription } returns "Store description"
            every { genres } returns listOf(SteamGenre("1", "Action"))
            every { metacritic } returns SteamMetacritic(91, null)
            every { screenshots } returns emptyList()
            every { background } returns null
            every { backgroundRaw } returns null
            every { headerImage } returns null
            every { capsuleImage } returns null
        }
        val api: SteamStoreApi = mockk {
            coEvery { getAppDetails(1L, any()) } answers {
                current = recovered
                Response.success(mapOf("1" to SteamAppDetailsResponse(true, appData)))
            }
        }
        val repository = SteamRepository(
            context = context,
            gameDao = gameDao,
            platformDao = mockk(relaxed = true),
            imageCacheManager = mockk(relaxed = true),
            syncPreferencesRepository = mockk(relaxed = true),
            gameArtDao = mockk(relaxed = true),
            gameScreenshotDao = mockk(relaxed = true)
        )
        SteamRepository::class.java.getDeclaredField("api\$delegate").apply {
            isAccessible = true
            set(repository, lazyOf(api))
        }

        val result = repository.enrichWithStoreData(1L)

        assertTrue(result is SteamResult.Success)
        assertEquals(current, (result as SteamResult.Success<GameEntity>).data)

        assertEquals(recovered.copy(description = "Store description", genre = "Action", rating = 91f), current)
        coVerify(exactly = 0) { gameDao.update(any()) }
    }

    @Test
    fun `ordinary PICS metadata cannot restore a retired pin`() = runTest {
        current = recovered

        libraryManager().updateExistingGame(snapshot, appInfo())

        assertEquals(recovered.copy(steamInstallDir = "Updated"), current)
        coVerify(exactly = 0) { gameDao.update(any()) }
    }

    @Test
    fun `forced PICS metadata cannot restore a retired pin`() = runTest {
        val manager = libraryManager()
        SteamLibraryManager::class.java.getDeclaredField("wasForceOverwrite").apply {
            isAccessible = true
            setBoolean(manager, true)
        }
        current = recovered

        manager.updateExistingGame(snapshot, appInfo())

        assertEquals(recovered.copy(
            title = "The Updated Game",
            sortTitle = "updated game",
            developer = "Updated developer",
            publisher = "Updated publisher",
            releaseYear = 2020,
            genre = "Action",
            steamInstallDir = "Updated",
            storeEnrichStatus = GameEntity.STORE_NOT_ATTEMPTED,
            igdbId = null
        ), current)
        coVerify(exactly = 0) { gameDao.update(any()) }
    }

    private fun libraryManager() = SteamLibraryManager(
        context = context,
        gameDao = gameDao,
        platformDao = mockk(relaxed = true),
        steamAccountDao = mockk(relaxed = true),
        steamLicenseDao = mockk(relaxed = true),
        cachedLicenseDao = mockk(relaxed = true),
        imageCacheManager = mockk(relaxed = true),
        steamRepository = mockk(relaxed = true),
        steamIgdbResolver = mockk(relaxed = true),
        preferencesRepository = mockk(relaxed = true),
        syncPreferencesRepository = mockk(relaxed = true),
        steamLibraryRepair = mockk(relaxed = true),
        gameArtDao = mockk(relaxed = true)
    )

    private fun appInfo() = KeyValue("appinfo").apply {
        children.add(KeyValue("common").apply {
            children.add(KeyValue("name", "The Updated Game"))
            children.add(KeyValue("steam_release_date", "1577880000"))
            children.add(KeyValue("genres").apply {
                children.add(KeyValue("0", "1"))
            })
            children.add(KeyValue("associations").apply {
                children.add(association("developer", "Updated developer"))
                children.add(association("publisher", "Updated publisher"))
            })
        })
        children.add(KeyValue("config").apply {
            children.add(KeyValue("installdir", "Updated"))
        })
    }

    private fun association(type: String, name: String) = KeyValue(type).apply {
        children.add(KeyValue("type", type))
        children.add(KeyValue("name", name))
    }
}
