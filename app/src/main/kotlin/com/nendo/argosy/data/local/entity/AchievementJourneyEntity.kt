package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * The last journey built for a game and the unlock set it was built from. [evidenceKey] lets the
 * repository tell a stale journey from a current one without rewriting an unchanged row.
 */
@Entity(
    tableName = "achievement_journeys",
    primaryKeys = ["gameId", "ownerUserId"],
    indices = [Index("ownerUserId")]
)
data class AchievementJourneyEntity(
    val gameId: Long,
    val ownerUserId: Long,
    val raGameId: Long?,
    val evidenceKey: String,
    val generatedAt: Long,
    val totalCount: Int,
    val unlockedCount: Int,
    val totalPoints: Int,
    val unlockedPoints: Int,
    val stateToken: String
)
