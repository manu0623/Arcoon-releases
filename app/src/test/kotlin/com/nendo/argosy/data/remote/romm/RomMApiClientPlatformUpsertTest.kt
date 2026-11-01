package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.PlatformEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class RomMApiClientPlatformUpsertTest {

    private val remote = RomMPlatform(id = 7L, slug = "nes", name = "NES", fsSlug = "nes", romCount = 12)

    private val api = mockk<RomMApi>(relaxed = true)
    private val platformDao = mockk<PlatformDao>(relaxed = true)
    private val connectionManager = mockk<RomMConnectionManager>(relaxed = true)
    private val client = RomMApiClient(connectionManager, platformDao)

    private fun stored(skipFullSync: Boolean) = PlatformEntity(
        id = 7L,
        slug = "nes",
        fsSlug = "nes",
        name = "NES",
        shortName = "NES",
        romExtensions = "",
        skipFullSync = skipFullSync
    )

    private suspend fun upsert(existing: PlatformEntity?): PlatformEntity {
        every { connectionManager.getApi() } returns api
        every { connectionManager.getBaseUrl() } returns "http://romm.local"
        coEvery { api.getPlatforms() } returns Response.success(listOf(remote))
        coEvery { platformDao.getById(7L) } returns existing
        coEvery { platformDao.getBySlugAndFsSlug(any(), any()) } returns null
        coEvery { platformDao.getBySlug(any()) } returns null
        val written = slot<PlatformEntity>()
        coEvery { platformDao.update(capture(written)) } returns Unit
        coEvery { platformDao.insert(capture(written)) } returns Unit
        client.fetchAndStorePlatforms()
        return written.captured
    }

    @Test
    fun `a platform refresh keeps a stored skip of the full sync`() = runTest {
        val written = upsert(stored(skipFullSync = true))

        assertTrue(written.skipFullSync)
        coVerify(exactly = 1) { platformDao.update(any()) }
    }

    @Test
    fun `a platform refresh keeps a platform in the full sync when it was never skipped`() = runTest {
        assertFalse(upsert(stored(skipFullSync = false)).skipFullSync)
    }

    @Test
    fun `a new platform starts in the full sync`() = runTest {
        assertFalse(upsert(existing = null).skipFullSync)
    }
}
