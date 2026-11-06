package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nendo.argosy.data.local.entity.AchievementJourneyEventEntity

@Dao
interface AchievementJourneyEventDao {

    @Query(
        "SELECT * FROM achievement_journey_events " +
            "WHERE gameId = :gameId AND ownerUserId = :ownerUserId"
    )
    suspend fun getForGame(gameId: Long, ownerUserId: Long): List<AchievementJourneyEventEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: AchievementJourneyEventEntity)

    @Query("DELETE FROM achievement_journey_events WHERE ownerUserId = :ownerUserId")
    suspend fun deleteByOwner(ownerUserId: Long)
}
