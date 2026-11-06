package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nendo.argosy.data.local.entity.AchievementJourneyEntity

@Dao
interface AchievementJourneyDao {

    @Query("SELECT * FROM achievement_journeys WHERE gameId = :gameId AND ownerUserId = :ownerUserId")
    suspend fun get(gameId: Long, ownerUserId: Long): AchievementJourneyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(journey: AchievementJourneyEntity)

    @Query("DELETE FROM achievement_journeys WHERE ownerUserId = :ownerUserId")
    suspend fun deleteByOwner(ownerUserId: Long)
}
