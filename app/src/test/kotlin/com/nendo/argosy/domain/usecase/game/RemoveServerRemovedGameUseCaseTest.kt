package com.nendo.argosy.domain.usecase.game

import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.dao.HomeTileDao
import com.nendo.argosy.data.local.entity.GameDiscEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.LocalRomFiles
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RemoveServerRemovedGameUseCaseTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val gameRepository: GameRepository = mockk(relaxed = true)
    private val gameFileDao: GameFileDao = mockk(relaxed = true)
    private val gameDiscDao: GameDiscDao = mockk(relaxed = true)
    private val homeTileDao: HomeTileDao = mockk(relaxed = true)
    private val useCase = RemoveServerRemovedGameUseCase(
        gameRepository,
        LocalRomFiles(gameFileDao, gameDiscDao),
        homeTileDao
    )

    private fun game(
        rommId: Long? = -9L,
        localPath: String? = null,
        source: GameSource = GameSource.ROMM_REMOTE
    ) = GameEntity(
        id = 9L,
        platformId = 1L,
        title = "Rockman X4",
        sortTitle = "rockman x4",
        localPath = localPath,
        rommId = rommId,
        igdbId = null,
        source = source
    )

    private fun stubFiles(downloaded: Int = 0, discPath: String? = null) {
        coEvery { gameFileDao.getDownloadedCount(9L) } returns downloaded
        coEvery { gameDiscDao.getDiscsForGame(9L) } returns listOfNotNull(
            discPath?.let {
                GameDiscEntity(gameId = 9L, discNumber = 1, rommId = 1L, fileName = "d1.cue", localPath = it, fileSize = 0L)
            }
        )
    }

    @Test
    fun `a server-removed game with no rom files loses only its row`() = runTest {
        val cachedSave = temp.newFile("x4.srm").apply { writeText("save") }
        coEvery { gameRepository.getById(9L) } returns game()
        stubFiles()

        assertTrue(useCase(9L))

        coVerify(exactly = 1) { gameRepository.delete(9L) }
        coVerify(exactly = 1) { homeTileDao.deleteTilesForMissingGames() }
        assertTrue(cachedSave.exists())
    }

    @Test
    fun `a server-removed game with a rom path is kept`() = runTest {
        coEvery { gameRepository.getById(9L) } returns game(localPath = "/roms/x4.cue", source = GameSource.ROMM_SYNCED)
        stubFiles()

        assertFalse(useCase(9L))
        coVerify(exactly = 0) { gameRepository.delete(any<Long>()) }
    }

    @Test
    fun `a server-removed game with a downloaded file row is kept`() = runTest {
        coEvery { gameRepository.getById(9L) } returns game()
        stubFiles(downloaded = 1)

        assertFalse(useCase(9L))
        coVerify(exactly = 0) { gameRepository.delete(any<Long>()) }
    }

    @Test
    fun `a server-removed game with a disc on disk is kept`() = runTest {
        coEvery { gameRepository.getById(9L) } returns game()
        stubFiles(discPath = "/roms/x4/d1.cue")

        assertFalse(useCase(9L))
        coVerify(exactly = 0) { gameRepository.delete(any<Long>()) }
    }

    @Test
    fun `a game still on the server is never removed`() = runTest {
        coEvery { gameRepository.getById(9L) } returns game(rommId = 42L)
        stubFiles()

        assertFalse(useCase(9L))
        coVerify(exactly = 0) { gameRepository.delete(any<Long>()) }
    }

    @Test
    fun `a local-only game is never removed`() = runTest {
        coEvery { gameRepository.getById(9L) } returns game(rommId = null, source = GameSource.LOCAL_ONLY)
        stubFiles()

        assertFalse(useCase(9L))
        coVerify(exactly = 0) { gameRepository.delete(any<Long>()) }
    }
}
