package com.nendo.argosy.data.local.entity

import androidx.room.Entity

/**
 * One witnessed unlock and the rich presence line RetroAchievements held at that moment. A row
 * exists only for an unlock Argosy saw happen; an unlock imported later has no event, which is what
 * keeps the journal from attaching a present state to a past achievement. [richPresence] is null
 * when the call could not answer at unlock time.
 */
@Entity(tableName = "achievement_journey_events", primaryKeys = ["gameId", "raId", "ownerUserId"])
data class AchievementJourneyEventEntity(
    val gameId: Long,
    val raId: Long,
    val ownerUserId: Long,
    val richPresence: String?,
    val capturedAt: Long
)
