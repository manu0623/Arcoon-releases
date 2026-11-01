package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nendo.argosy.data.local.entity.RAPatchCacheEntity

@Dao
interface RAPatchCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RAPatchCacheEntity)

    @Query("SELECT * FROM ra_patch_cache WHERE raGameId = :raGameId AND ownerUserId = :ownerUserId")
    suspend fun get(raGameId: Long, ownerUserId: Long): RAPatchCacheEntity?

    @Query("SELECT raGameId FROM ra_patch_cache WHERE ownerUserId = :ownerUserId")
    suspend fun getCachedRaGameIds(ownerUserId: Long): List<Long>

    @Query("""
        SELECT DISTINCT CASE WHEN raIdVerified = 1 THEN verifiedRaId ELSE COALESCE(verifiedRaId, raId) END
        FROM games
        WHERE localPath IS NOT NULL
          AND (CASE WHEN raIdVerified = 1 THEN verifiedRaId ELSE COALESCE(verifiedRaId, raId) END) IS NOT NULL
    """)
    suspend fun getInstalledRaGameIds(): List<Long>

    @Query("DELETE FROM ra_patch_cache WHERE ownerUserId = :ownerUserId")
    suspend fun deleteByOwner(ownerUserId: Long)
}
