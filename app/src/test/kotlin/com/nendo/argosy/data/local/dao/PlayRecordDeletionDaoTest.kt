package com.nendo.argosy.data.local.dao

import com.nendo.argosy.data.local.entity.PlayRecordDeletionEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PlayRecordDeletionDaoTest {

    private class RecordingDao : PlayRecordDeletionDao {
        val calls = mutableListOf<String>()
        val rows = mutableListOf<PlayRecordDeletionEntity>()

        override suspend fun upsert(entity: PlayRecordDeletionEntity) {
            calls += "upsert"
            rows.removeAll { it.gameId == entity.gameId && it.ownerUserId == entity.ownerUserId }
            rows += entity
        }

        override suspend fun getForOwner(ownerKey: Long): List<PlayRecordDeletionEntity> =
            rows.filter { it.ownerUserId == ownerKey }

        override suspend fun deleteByOwner(ownerUserId: Long) {
            rows.removeAll { it.ownerUserId == ownerUserId }
        }

        override suspend fun deleteQueuedSocialSessions(gameId: Long, ownerUserId: Long?) {
            calls += "queue:$gameId:$ownerUserId"
        }

        override suspend fun deleteSessions(gameId: Long, ownerUserId: Long?) {
            calls += "sessions:$gameId:$ownerUserId"
        }

        override suspend fun resetOverlayPlayTotals(ownerUserId: Long, gameId: Long) {
            calls += "overlay:$ownerUserId:$gameId"
        }

        override suspend fun resetMirroredPlayTotals(gameId: Long) {
            calls += "mirror:$gameId"
        }
    }

    private val deletedAt = Instant.parse("2026-10-20T00:00:00Z")

    @Test
    fun `queued uploads go before their sessions, then totals reset and the cutoff is written`() = runTest {
        val dao = RecordingDao()

        dao.deleteRecords(7L, 3L, deletedAt)

        assertEquals(
            listOf("queue:7:3", "sessions:7:3", "overlay:3:7", "mirror:7", "upsert"),
            dao.calls
        )
        assertEquals(listOf(PlayRecordDeletionEntity(7L, 3L, deletedAt)), dao.rows)
    }

    @Test
    fun `with no account there is no overlay to reset and the cutoff is keyed to no owner`() = runTest {
        val dao = RecordingDao()

        dao.deleteRecords(7L, null, deletedAt)

        assertEquals(listOf("queue:7:null", "sessions:7:null", "mirror:7", "upsert"), dao.calls)
        assertEquals(PlayRecordDeletionEntity.NO_OWNER, dao.rows.single().ownerUserId)
    }

    @Test
    fun `a second delete moves the cutoff forward instead of adding a row`() = runTest {
        val dao = RecordingDao()
        val later = deletedAt.plusSeconds(3600)

        dao.deleteRecords(7L, 3L, deletedAt)
        dao.deleteRecords(7L, 3L, later)

        assertEquals(listOf(PlayRecordDeletionEntity(7L, 3L, later)), dao.rows)
    }

    @Test
    fun `cutoffs are read for the owner asked for and no other`() = runTest {
        val dao = RecordingDao()
        dao.deleteRecords(7L, 3L, deletedAt)
        dao.deleteRecords(9L, 5L, deletedAt)

        val cutoffs = dao.cutoffsFor(3L)

        assertTrue(cutoffs.blocks(7L, deletedAt.minusSeconds(1)))
        assertFalse(cutoffs.blocks(9L, deletedAt.minusSeconds(1)))
    }
}
