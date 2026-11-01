package com.nendo.argosy.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "core_option_overrides",
    primaryKeys = ["coreId", "optionKey"]
)
data class CoreOptionOverrideEntity(
    val coreId: String,
    val optionKey: String,
    val value: String
)
