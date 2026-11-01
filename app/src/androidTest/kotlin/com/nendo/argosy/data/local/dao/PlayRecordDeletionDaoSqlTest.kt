package com.nendo.argosy.data.local.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nendo.argosy.data.local.ALauncherDatabase
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameUserOverlayEntity
import com.nendo.argosy.data.local.entity.PendingSocialSyncEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.local.entity.PlaySessionEntity
import com.nendo.argosy.data.local.entity.SocialSyncType
import com.nendo.argosy.data.model.GameSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class PlayRecordDeletionDaoSqlTest {

    private lateinit var db: ALauncherDatabase
    private lateinit var dao: PlayRecordDeletionDao

    private val played = Instant.parse("2026-10-01T12:00:00Z")
    private val otherGamePlayed = Instant.parse("2026-10-02T12:00:00Z")
    private val otherOwnerPlayed = Instant.parse("2026-10-03T12:00:00Z")
    private val deletedAt = Instant.parse("2026-10-20T00:00:00Z")

    @Before
    fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            ALauncherDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.playRecordDeletionDao()
        db.platformDao().insert(
            PlatformEntity(id = 1L, slug = "snes", name = "SNES", shortName = "SNES", romExtensions = "sfc")
        )
        db.gameDao().insert(
            GameEntity(
                id = 7L,
                platformId = 1L,
                platformSlug = "snes",
                title = "Game",
                sortTitle = "game",
                localPath = null,
                rommId = 42L,
                igdbId = null,
                source = GameSource.ROMM_REMOTE
            )
        )
        val overlayDao = db.gameUserOverlayDao()
        overlayDao.mirrorMergedPlayTotals(7L, 4, 120, played)
        overlayDao.upsert(
            GameUserOverlayEntity(ownerUserId = 3L, gameId = 7L, playCount = 4, playTimeMinutes = 120, lastPlayed = played)
        )
        overlayDao.upsert(
            GameUserOverlayEntity(ownerUserId = 5L, gameId = 7L, playCount = 2, playTimeMinutes = 60, lastPlayed = otherOwnerPlayed)
        )
        val sessions = db.playSessionDao()
        sessions.insert(session(gameId = 7L, ownerUserId = 3L, start = played))
        sessions.insert(session(gameId = 8L, ownerUserId = 3L, start = otherGamePlayed))
        sessions.insert(session(gameId = 7L, ownerUserId = 5L, start = otherOwnerPlayed))
        val queue = db.pendingSocialSyncDao()
        queue.insert(queued(start = played, ownerUserId = 3L))
        queue.insert(queued(start = otherGamePlayed, ownerUserId = 3L))
        queue.insert(queued(start = otherOwnerPlayed, ownerUserId = 5L))
        Unit
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun session(gameId: Long, ownerUserId: Long, start: Instant) = PlaySessionEntity(
        userId = "social-user",
        gameId = gameId,
        igdbId = null,
        gameTitle = "Game",
        platformSlug = "snes",
        startTime = start,
        endTime = start.plusSeconds(1800),
        deviceId = "device-x",
        deviceManufacturer = "Acme",
        deviceModel = "ZX-90",
        activePlayMs = 1_800_000L,
        ownerUserId = ownerUserId
    )

    private fun queued(start: Instant, ownerUserId: Long) = PendingSocialSyncEntity(
        syncType = SocialSyncType.PLAY_SESSION,
        payloadJson = "{}",
        occurredAt = start,
        ownerUserId = ownerUserId
    )

    @Test
    fun deletingRemovesOnlyThisOwnersSessionsOfThatGame() = runBlocking {
        dao.deleteRecords(7L, 3L, deletedAt)

        val sessions = db.playSessionDao()
        assertTrue(sessions.getSessionsForGameAndOwner(7L, 3L).isEmpty())
        assertEquals(1, sessions.getSessionsForGameAndOwner(8L, 3L).size)
        assertEquals(1, sessions.getSessionsForGameAndOwner(7L, 5L).size)
    }

    @Test
    fun deletingDropsOnlyTheQueuedSocialUploadsOfTheDeletedSessions() = runBlocking {
        dao.deleteRecords(7L, 3L, deletedAt)

        val queue = db.pendingSocialSyncDao()
        assertEquals(
            listOf(otherGamePlayed),
            queue.getPendingByType(SocialSyncType.PLAY_SESSION, 3L).map { it.occurredAt }
        )
        assertEquals(
            listOf(otherOwnerPlayed),
            queue.getPendingByType(SocialSyncType.PLAY_SESSION, 5L).map { it.occurredAt }
        )
    }

    @Test
    fun deletingResetsThisOwnersTotalsAndTheMirrorButNotAnotherOwners() = runBlocking {
        dao.deleteRecords(7L, 3L, deletedAt)

        val overlayDao = db.gameUserOverlayDao()
        val mine = overlayDao.get(3L, 7L)!!
        assertEquals(0, mine.playCount)
        assertEquals(0, mine.playTimeMinutes)
        assertNull(mine.lastPlayed)
        val theirs = overlayDao.get(5L, 7L)!!
        assertEquals(2, theirs.playCount)
        assertEquals(60, theirs.playTimeMinutes)
        val mirror = overlayDao.mirroredPlayTotals(7L)!!
        assertEquals(0, mirror.playCount)
        assertEquals(0, mirror.playTimeMinutes)
        assertNull(mirror.lastPlayed)
    }

    @Test
    fun deletingRecordsTheCutoffForThatOwnerOnly() = runBlocking {
        dao.deleteRecords(7L, 3L, deletedAt)

        assertTrue(dao.cutoffsFor(3L).blocks(7L, played))
        assertFalse(dao.cutoffsFor(3L).blocks(7L, deletedAt.plusSeconds(1)))
        assertFalse(dao.cutoffsFor(5L).blocks(7L, played))
    }

    @Test
    fun deletingWithNoAccountTouchesOnlyUnownedSessionsAndKeysTheCutoffToNoOwner() = runBlocking {
        db.playSessionDao().insert(session(gameId = 7L, ownerUserId = 3L, start = played).copy(ownerUserId = null))

        dao.deleteRecords(7L, null, deletedAt)

        val sessions = db.playSessionDao()
        assertTrue(sessions.getSessionsForGameAndOwner(7L, null).isEmpty())
        assertEquals(1, sessions.getSessionsForGameAndOwner(7L, 3L).size)
        assertEquals(4, db.gameUserOverlayDao().get(3L, 7L)!!.playCount)
        assertTrue(dao.cutoffsFor(null).blocks(7L, played))
    }
}
