package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.model.FileOrigin
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.platform.PlatformFolderResolver
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BaseRomFileResolverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val gameDao = mockk<GameDao>(relaxed = true)
    private val platformDao = mockk<PlatformDao>(relaxed = true)
    private lateinit var customRoot: File
    private lateinit var update: File
    private lateinit var resolver: BaseRomFileResolver

    @Before
    fun setUp() {
        val storageRoot = tempFolder.newFolder("roms")
        customRoot = tempFolder.newFolder("ps3pkg")
        update = File(customRoot, "Game [Update].pkg").apply { writeBytes(ByteArray(1)) }
        File(customRoot, "Other Game.pkg").writeBytes(ByteArray(64))

        val discs = platform(1L, "ps3", customRomPath = null)
        val packages = platform(2L, "ps3", customRomPath = customRoot.absolutePath)
        coEvery { platformDao.getById(any()) } returns null
        coEvery { platformDao.getById(discs.id) } returns discs
        coEvery { platformDao.getById(packages.id) } returns packages
        coEvery { platformDao.getAllPlatforms() } returns listOf(discs, packages)

        val preferences = mockk<UserPreferencesRepository>()
        every { preferences.userPreferences } returns
            flowOf(UserPreferences(romStoragePath = storageRoot.absolutePath))

        resolver = BaseRomFileResolver(
            gameDao = gameDao,
            platformFolderResolver = PlatformFolderResolver(mockk(relaxed = true), platformDao, gameDao, preferences)
        )
    }

    @Test
    fun `a custom folder shared by slug is a platform root for the platform that owns it`() = runTest {
        val game = game(platformId = 2L)

        assertEquals(update, resolver.resolve(game, update))
        coVerify(exactly = 0) { gameDao.updateLocalPath(any(), any(), any(), any()) }
    }

    @Test
    fun `another platform's custom folder is not a root for a same-slug platform`() = runTest {
        val game = game(platformId = 1L)

        assertEquals(File(customRoot, "Other Game.pkg"), resolver.resolve(game, update))
    }

    private fun platform(id: Long, slug: String, customRomPath: String?) = PlatformEntity(
        id = id,
        slug = slug,
        fsSlug = null,
        name = slug,
        shortName = slug,
        romExtensions = "",
        customRomPath = customRomPath
    )

    private fun game(platformId: Long) = GameEntity(
        id = 7L,
        platformId = platformId,
        platformSlug = "ps3",
        title = "Game",
        sortTitle = "game",
        localPath = update.absolutePath,
        fileOrigin = FileOrigin.ROMM_DOWNLOAD,
        rommId = null,
        igdbId = null,
        source = GameSource.ROMM_SYNCED
    )
}
