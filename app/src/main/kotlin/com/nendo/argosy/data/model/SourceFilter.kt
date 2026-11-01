package com.nendo.argosy.data.model

/**
 * Which slice of the library is shown. The name is the stored and compared value; the display
 * label lives in `ui/common/SourceFilterUi.kt`.
 */
enum class SourceFilter {
    ALL,
    PLAYABLE,
    FAVORITES,
    HIDDEN;

    companion object {
        fun fromString(value: String?): SourceFilter? = entries.find { it.name == value }
    }
}
