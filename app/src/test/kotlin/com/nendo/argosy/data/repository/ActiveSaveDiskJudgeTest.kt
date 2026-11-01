package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.sync.SaveClaim
import com.nendo.argosy.data.sync.SaveOwnershipTracker
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

class ActiveSaveDiskJudgeTest {
    private val saveCacheDao: SaveCacheDao = mockk(relaxed = true)
    private val saveSyncDao: SaveSyncDao = mockk(relaxed = true)
    private val ownership: SaveOwnershipTracker = mockk(relaxed = true)
    private val judge = ActiveSaveDiskJudge(saveCacheDao, saveSyncDao, ownership)

    private val target = SaveCacheEntity(
        id = 50L,
        gameId = GAME_ID,
        emulatorId = EMULATOR,
        cachedAt = Instant.parse("2026-10-01T00:00:00Z"),
        saveSize = 10,
        cachePath = "x/game.srm",
        contentHash = "cached",
        identityHash = "cached-identity",
        channelName = "autosave",
        rommSaveId = 9L
    )

    @Before
    fun setup() {
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(any(), any(), any(), any()) } returns null
        coEvery { saveCacheDao.getAllByGameChannelAndHash(any(), any(), any(), any()) } returns emptyList()
        coEvery { ownership.claim(any(), any()) } returns SaveClaim.Unowned
    }

    private suspend fun verdict(diskHash: String, secureSaves: Boolean) =
        judge.judge(GAME_ID, EMULATOR, null, PATH, diskHash, target, secureSaves, OWNER).verdict

    private fun syncRow(rommSaveId: Long?) = SaveSyncEntity(
        id = 4L, gameId = GAME_ID, rommId = 100L, emulatorId = EMULATOR, channelName = "autosave",
        rommSaveId = rommSaveId, localContentHash = "local-form", syncStatus = SaveSyncEntity.STATUS_SYNCED
    )

    @Test
    fun `a disk holding the content or identity hash matches in either mode`() = runTest {
        for (secure in listOf(true, false)) {
            assertEquals(DiskVerdict.Matches, verdict("cached", secure))
            assertEquals(DiskVerdict.Matches, verdict("cached-identity", secure))
        }
    }

    @Test
    fun `the local form of the last transfer matches only for the same server save`() = runTest {
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(GAME_ID, EMULATOR, "autosave", OWNER) } returns syncRow(9L)
        assertEquals(DiskVerdict.Matches, verdict("local-form", secureSaves = true))

        coEvery { saveSyncDao.getByGameEmulatorAndChannel(GAME_ID, EMULATOR, "autosave", OWNER) } returns syncRow(8L)
        assertEquals(DiskVerdict.ProtectThenRestore, verdict("local-form", secureSaves = true))
    }

    @Test
    fun `a disk matching an unsynced version cached at or after the target is progress`() = runTest {
        val newer = target.copy(id = 61L, cachedAt = Instant.parse("2026-10-02T00:00:00Z"), contentHash = "disk", needsRemoteSync = true)
        val same = newer.copy(id = 62L, cachedAt = target.cachedAt)
        val older = newer.copy(id = 63L, cachedAt = Instant.parse("2026-09-30T00:00:00Z"))
        val synced = newer.copy(id = 64L, needsRemoteSync = false)

        coEvery { saveCacheDao.getAllByGameChannelAndHash(GAME_ID, OWNER, "autosave", "disk") } returns listOf(newer)
        assertEquals(DiskVerdict.Progress(61L), verdict("disk", secureSaves = true))

        coEvery { saveCacheDao.getAllByGameChannelAndHash(GAME_ID, OWNER, "autosave", "disk") } returns listOf(same)
        assertEquals(DiskVerdict.Progress(62L), verdict("disk", secureSaves = false))

        coEvery { saveCacheDao.getAllByGameChannelAndHash(GAME_ID, OWNER, "autosave", "disk") } returns listOf(older, synced)
        assertEquals(DiskVerdict.ProtectThenRestore, verdict("disk", secureSaves = true))
    }

    @Test
    fun `a differing disk is adopted with Secure Saves off and protected then restored with it on`() = runTest {
        assertEquals(DiskVerdict.Adopt, verdict("disk", secureSaves = false))
        assertEquals(DiskVerdict.ProtectThenRestore, verdict("disk", secureSaves = true))
    }

    @Test
    fun `another account's differing disk is never adopted`() = runTest {
        coEvery { ownership.claim(PATH, EMULATOR) } returns SaveClaim.Foreign(7L)

        assertEquals(DiskVerdict.Foreign, verdict("disk", secureSaves = false))
        assertEquals(DiskVerdict.ProtectThenRestore, verdict("disk", secureSaves = true))
    }

    private companion object {
        const val GAME_ID = 1L
        const val OWNER = 3L
        const val EMULATOR = "argosy"
        const val PATH = "/saves/game.srm"
    }
}
