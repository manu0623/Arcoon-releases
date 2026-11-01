package com.nendo.argosy.data.sync

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PspSavePrefixLearnerTest {

    private val gameDao = mockk<GameDao>(relaxed = true)
    private val learner = PspSavePrefixLearner(gameDao)

    private fun game(platformSlug: String = "psp", saveId: String? = "NPJH50804") = GameEntity(
        id = 78L, platformId = 3L, platformSlug = platformSlug,
        title = "Masou Kishin", sortTitle = "masou kishin",
        localPath = "/roms/masou.chd", rommId = 85L, igdbId = null,
        source = GameSource.ROMM_SYNCED,
        titleId = "NPJH50804", saveId = saveId, titleIdLocked = true
    )

    @Before
    fun setUp() {
        coEvery { gameDao.getById(78L) } returns game()
        coEvery { gameDao.countOtherGamesKeyedBy(any(), any(), any()) } returns 0
    }

    @Test
    fun `adopts the one serial every written folder starts with and locks it`() = runTest {
        val adopted = learner.adopt(78L, setOf("ULJS00445CNT", "ULJS00445SYS"))

        assertEquals("ULJS00445", adopted)
        coVerify(exactly = 1) { gameDao.setTitleAndSaveIdWithLock(78L, "NPJH50804", "ULJS00445", true) }
    }

    @Test
    fun `folders already matching the stored id change nothing`() = runTest {
        assertNull(learner.adopt(78L, setOf("NPJH50804DATA")))
        coVerify(exactly = 0) { gameDao.setTitleAndSaveIdWithLock(any(), any(), any(), any()) }
    }

    @Test
    fun `folders naming two serials are ambiguous`() = runTest {
        assertNull(learner.adopt(78L, setOf("ULJS00445CNT", "ULJS00003DATA")))
        coVerify(exactly = 0) { gameDao.setTitleAndSaveIdWithLock(any(), any(), any(), any()) }
    }

    @Test
    fun `a written folder without a serial blocks adoption`() = runTest {
        assertNull(learner.adopt(78L, setOf("ULJS00445CNT", "SYSTEM")))
        coVerify(exactly = 0) { gameDao.setTitleAndSaveIdWithLock(any(), any(), any(), any()) }
    }

    @Test
    fun `nothing written adopts nothing`() = runTest {
        assertNull(learner.adopt(78L, emptySet()))
    }

    @Test
    fun `a prefix another game is keyed by is never taken`() = runTest {
        coEvery { gameDao.countOtherGamesKeyedBy(78L, "psp", "ULJS00445") } returns 1

        assertNull(learner.adopt(78L, setOf("ULJS00445CNT")))
        coVerify(exactly = 0) { gameDao.setTitleAndSaveIdWithLock(any(), any(), any(), any()) }
    }

    @Test
    fun `only PSP games learn a prefix`() = runTest {
        coEvery { gameDao.getById(78L) } returns game(platformSlug = "ps2")

        assertNull(learner.adopt(78L, setOf("ULJS00445CNT")))
    }

    @Test
    fun `a row with no save id compares against its title id`() = runTest {
        coEvery { gameDao.getById(78L) } returns game(saveId = null)

        assertNull(learner.adopt(78L, setOf("NPJH50804DATA")))
        assertEquals("ULJS00445", learner.adopt(78L, setOf("ULJS00445CNT")))
    }
}
