package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "ra_patch_cache",
    primaryKeys = ["raGameId", "ownerUserId"],
    indices = [Index("ownerUserId")]
)
data class RAPatchCacheEntity(
    val raGameId: Long,
    val ownerUserId: Long,
    val patchJson: String,
    val fetchedAt: Long
)
