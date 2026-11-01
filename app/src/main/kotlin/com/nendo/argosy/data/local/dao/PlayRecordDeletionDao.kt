package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.nendo.argosy.data.local.entity.PlayRecordDeletionEntity
import com.nendo.argosy.data.model.PlayRecordCutoffs
import java.time.Instant

@Dao
interface PlayRecordDeletionDao {

    @Upsert
    suspend fun upsert(entity: PlayRecordDeletionEntity)

    @Query("SELECT * FROM play_record_deletions WHERE ownerUserId = :ownerKey")
    suspend fun getForOwner(ownerKey: Long): List<PlayRecordDeletionEntity>

    @Query("DELETE FROM play_record_deletions WHERE ownerUserId = :ownerUserId")
    suspend fun deleteByOwner(ownerUserId: Long)

    @Query("""
        DELETE FROM pending_social_sync
        WHERE syncType = 'PLAY_SESSION'
          AND (ownerUserId IS NULL OR ownerUserId = :ownerUserId)
          AND occurredAt IN (
              SELECT startTime FROM play_sessions
              WHERE gameId = :gameId AND ownerUserId IS :ownerUserId
          )
    """)
    suspend fun deleteQueuedSocialSessions(gameId: Long, ownerUserId: Long?)

    @Query("DELETE FROM play_sessions WHERE gameId = :gameId AND ownerUserId IS :ownerUserId")
    suspend fun deleteSessions(gameId: Long, ownerUserId: Long?)

    @Query("""
        UPDATE game_user_overlay SET playCount = 0, playTimeMinutes = 0, lastPlayed = NULL
        WHERE ownerUserId = :ownerUserId AND gameId = :gameId
    """)
    suspend fun resetOverlayPlayTotals(ownerUserId: Long, gameId: Long)

    @Query("UPDATE games SET playCount = 0, playTimeMinutes = 0, lastPlayed = NULL WHERE id = :gameId")
    suspend fun resetMirroredPlayTotals(gameId: Long)

    /**
     * Deletes [ownerUserId]'s play history for [gameId] on this device: its sessions, their queued
     * social uploads and the game's play totals, then records [deletedBefore] so the same sessions
     * are never pulled or sent again. Other accounts' sessions for the game are untouched.
     */
    @Transaction
    suspend fun deleteRecords(gameId: Long, ownerUserId: Long?, deletedBefore: Instant) {
        deleteQueuedSocialSessions(gameId, ownerUserId)
        deleteSessions(gameId, ownerUserId)
        if (ownerUserId != null) resetOverlayPlayTotals(ownerUserId, gameId)
        resetMirroredPlayTotals(gameId)
        upsert(
            PlayRecordDeletionEntity(
                gameId = gameId,
                ownerUserId = PlayRecordDeletionEntity.ownerKey(ownerUserId),
                deletedBefore = deletedBefore
            )
        )
    }
}

suspend fun PlayRecordDeletionDao.cutoffsFor(ownerUserId: Long?): PlayRecordCutoffs =
    PlayRecordCutoffs(
        getForOwner(PlayRecordDeletionEntity.ownerKey(ownerUserId))
            .associate { it.gameId to it.deletedBefore }
    )
