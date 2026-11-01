package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import java.time.Instant

@Entity(
    tableName = "play_record_deletions",
    primaryKeys = ["gameId", "ownerUserId"],
    indices = [Index("ownerUserId")]
)
data class PlayRecordDeletionEntity(
    val gameId: Long,
    val ownerUserId: Long,
    val deletedBefore: Instant
) {
    companion object {
        const val NO_OWNER = 0L

        fun ownerKey(ownerUserId: Long?): Long = ownerUserId ?: NO_OWNER
    }
}
