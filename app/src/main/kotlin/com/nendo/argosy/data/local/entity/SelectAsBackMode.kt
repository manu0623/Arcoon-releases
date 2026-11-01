package com.nendo.argosy.data.local.entity

enum class SelectAsBackMode {
    AUTO,
    ON,
    OFF;

    companion object {
        fun fromString(value: String?): SelectAsBackMode = entries.firstOrNull { it.name == value } ?: AUTO
    }
}
