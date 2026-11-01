package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "media_user_data",
    indices = [
        Index(value = ["ownerUserId", "itemId"], unique = true),
        Index(value = ["ownerUserId", "needsSync"])
    ]
)
data class MediaUserDataEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ownerUserId: String,
    val itemId: String,
    val playbackPositionTicks: Long = 0,
    val playedPercentage: Double? = null,
    val played: Boolean = false,
    val playCount: Int = 0,
    val isFavorite: Boolean = false,
    val lastPlayedAt: Instant? = null,
    val needsSync: Boolean = false,
    val pendingPosition: Boolean = false,
    val pendingPlayed: Boolean = false,
    val pendingFavorite: Boolean = false,
    val updatedAt: Instant = Instant.now()
)
