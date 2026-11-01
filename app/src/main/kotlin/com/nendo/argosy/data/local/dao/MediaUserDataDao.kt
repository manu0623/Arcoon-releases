package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.nendo.argosy.data.local.entity.MediaUserDataEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Watch state, scoped to a media account on every read: the position and the played flag belong to
 * one user of the server, and reading them unscoped hands one account another's progress.
 */
@Dao
interface MediaUserDataDao {

    @Query("SELECT * FROM media_user_data WHERE ownerUserId = :ownerUserId AND itemId = :itemId LIMIT 1")
    suspend fun getByItem(ownerUserId: String, itemId: String): MediaUserDataEntity?

    @Query("SELECT * FROM media_user_data WHERE ownerUserId = :ownerUserId AND itemId = :itemId LIMIT 1")
    fun observeByItem(ownerUserId: String, itemId: String): Flow<MediaUserDataEntity?>

    @Query("SELECT * FROM media_user_data WHERE ownerUserId = :ownerUserId AND itemId IN (:itemIds)")
    suspend fun getByItems(ownerUserId: String, itemIds: List<String>): List<MediaUserDataEntity>

    @Query(
        "SELECT * FROM media_user_data WHERE ownerUserId = :ownerUserId AND isFavorite = 1 " +
            "ORDER BY updatedAt DESC"
    )
    fun observeFavorites(ownerUserId: String): Flow<List<MediaUserDataEntity>>

    @Query(
        "SELECT * FROM media_user_data WHERE ownerUserId = :ownerUserId AND needsSync = 1 " +
            "ORDER BY updatedAt ASC"
    )
    suspend fun getNeedingSync(ownerUserId: String): List<MediaUserDataEntity>

    @Query("SELECT COUNT(*) FROM media_user_data WHERE ownerUserId = :ownerUserId AND needsSync = 1")
    fun observeNeedingSyncCount(ownerUserId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(userData: MediaUserDataEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(userData: List<MediaUserDataEntity>)

    /**
     * Clears every pending flag on a row last written at or before [reportedAt]. A non-null
     * [serverPlayback] replaces the row's position, played flag and play history.
     */
    @Transaction
    suspend fun settleReport(
        ownerUserId: String,
        itemId: String,
        reportedAt: Instant,
        serverPlayback: MediaUserDataEntity?
    ) {
        val current = getByItem(ownerUserId, itemId) ?: return
        if (current.updatedAt.isAfter(reportedAt)) return
        val playback = serverPlayback ?: current
        insert(
            current.copy(
                playbackPositionTicks = playback.playbackPositionTicks,
                playedPercentage = playback.playedPercentage,
                played = playback.played,
                playCount = playback.playCount,
                lastPlayedAt = playback.lastPlayedAt,
                needsSync = false,
                pendingPosition = false,
                pendingPlayed = false,
                pendingFavorite = false
            )
        )
    }

    /**
     * Records where playback was left. Creates the row when the item has never been played, so a
     * position taken offline is stored whether or not the server has ever described this item.
     * Marks pending only the fields whose value differs from the stored one.
     */
    @Transaction
    suspend fun recordPosition(
        ownerUserId: String,
        itemId: String,
        positionTicks: Long,
        playedPercentage: Double?,
        played: Boolean,
        updatedAt: Instant
    ) {
        val existing = getByItem(ownerUserId, itemId)
        val playCount = if (played && existing?.played != true) {
            (existing?.playCount ?: 0) + 1
        } else {
            existing?.playCount ?: 0
        }
        val pendingPosition = existing?.pendingPosition == true ||
            positionTicks != (existing?.playbackPositionTicks ?: 0L)
        val pendingPlayed = existing?.pendingPlayed == true || played != (existing?.played ?: false)
        val pendingFavorite = existing?.pendingFavorite == true
        val base = existing ?: MediaUserDataEntity(ownerUserId = ownerUserId, itemId = itemId)
        insert(
            base.copy(
                playbackPositionTicks = positionTicks,
                playedPercentage = playedPercentage,
                played = played,
                playCount = playCount,
                lastPlayedAt = updatedAt,
                needsSync = pendingPosition || pendingPlayed || pendingFavorite,
                pendingPosition = pendingPosition,
                pendingPlayed = pendingPlayed,
                updatedAt = updatedAt
            )
        )
    }

    @Transaction
    suspend fun setFavorite(
        ownerUserId: String,
        itemId: String,
        isFavorite: Boolean,
        updatedAt: Instant
    ) {
        val existing = getByItem(ownerUserId, itemId)
        val pendingFavorite = existing?.pendingFavorite == true ||
            isFavorite != (existing?.isFavorite ?: false)
        val pendingPlayback = existing?.pendingPosition == true || existing?.pendingPlayed == true
        val base = existing ?: MediaUserDataEntity(ownerUserId = ownerUserId, itemId = itemId)
        insert(
            base.copy(
                isFavorite = isFavorite,
                needsSync = pendingFavorite || pendingPlayback,
                pendingFavorite = pendingFavorite,
                updatedAt = updatedAt
            )
        )
    }

    /**
     * Takes the server's version of one item's watch state for every field group with no pending
     * local write. Playback (position, played, play history) is one group and the favourite flag
     * is the other. A row with anything pending keeps its own [MediaUserDataEntity.updatedAt].
     */
    @Transaction
    suspend fun applyServerState(userData: MediaUserDataEntity) {
        val existing = getByItem(userData.ownerUserId, userData.itemId)
        if (existing == null || !existing.needsSync) {
            insert(
                userData.copy(
                    id = existing?.id ?: 0,
                    needsSync = false,
                    pendingPosition = false,
                    pendingPlayed = false,
                    pendingFavorite = false
                )
            )
            return
        }
        val holdPlayback = existing.pendingPosition || existing.pendingPlayed
        val playback = if (holdPlayback) existing else userData
        insert(
            existing.copy(
                playbackPositionTicks = playback.playbackPositionTicks,
                playedPercentage = playback.playedPercentage,
                played = playback.played,
                playCount = playback.playCount,
                lastPlayedAt = playback.lastPlayedAt,
                isFavorite = if (existing.pendingFavorite) existing.isFavorite else userData.isFavorite
            )
        )
    }

    @Query("DELETE FROM media_user_data WHERE ownerUserId = :ownerUserId AND itemId = :itemId")
    suspend fun deleteByItem(ownerUserId: String, itemId: String)

    @Query("DELETE FROM media_user_data WHERE ownerUserId = :ownerUserId")
    suspend fun deleteByOwner(ownerUserId: String)

}
