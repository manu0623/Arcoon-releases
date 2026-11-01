package com.nendo.argosy.domain.usecase.game

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class GetRelatedGamesUseCaseTest {

    private lateinit var gameDao: GameDao
    private lateinit var useCase: GetRelatedGamesUseCase

    private fun game(igdbId: Long?): GameEntity = mockk<GameEntity>(relaxed = true).also {
        every { it.id } returns GAME_ID
        every { it.igdbId } returns igdbId
        every { it.platformId } returns PLATFORM_ID
        every { it.collections } returns "Mario Kart"
        every { it.franchises } returns "Mario"
        every { it.genres } returns "Racing"
        every { it.genre } returns null
        every { it.releaseYear } returns 1996
    }

    @Before
    fun setUp() {
        gameDao = mockk()
        val syncPreferences = mockk<SyncPreferencesRepository>()
        coEvery { syncPreferences.getRommUserId() } returns OWNER_ID
        coEvery { gameDao.getRelatedByCollection(any(), any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { gameDao.getRelatedByFranchise(any(), any(), any(), any(), any(), any()) } returns emptyList()
        coEvery {
            gameDao.getRelatedByGenreAndYear(any(), any(), any(), any(), any(), any(), any(), any())
        } returns emptyList()
        useCase = GetRelatedGamesUseCase(gameDao, syncPreferences)
    }

    @Test
    fun `excludes the game and its regional copies on the same platform from every related query`() = runTest {
        useCase(game(igdbId = IGDB_ID))

        coVerify {
            gameDao.getRelatedByCollection("Mario Kart", GAME_ID, IGDB_ID, PLATFORM_ID, OWNER_ID, any())
        }
        coVerify {
            gameDao.getRelatedByFranchise("Mario", GAME_ID, IGDB_ID, PLATFORM_ID, OWNER_ID, any())
        }
        coVerify {
            gameDao.getRelatedByGenreAndYear("Racing", 1993, 1999, GAME_ID, IGDB_ID, PLATFORM_ID, OWNER_ID, any())
        }
    }

    @Test
    fun `excludes only the game itself when it has no igdb id`() = runTest {
        useCase(game(igdbId = null))

        coVerify {
            gameDao.getRelatedByCollection("Mario Kart", GAME_ID, null, PLATFORM_ID, OWNER_ID, any())
        }
        coVerify {
            gameDao.getRelatedByFranchise("Mario", GAME_ID, null, PLATFORM_ID, OWNER_ID, any())
        }
        coVerify {
            gameDao.getRelatedByGenreAndYear("Racing", 1993, 1999, GAME_ID, null, PLATFORM_ID, OWNER_ID, any())
        }
    }

    private companion object {
        const val GAME_ID = 42L
        const val IGDB_ID = 1234L
        const val PLATFORM_ID = 9L
        const val OWNER_ID = 7L
    }
}
