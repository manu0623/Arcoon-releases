package com.nendo.argosy.domain.model

import com.nendo.argosy.data.model.ActiveSort
import com.nendo.argosy.data.model.SortOption
import com.nendo.argosy.data.model.SourceFilter

/**
 * What a library link tile opens the library onto, mirroring the library's filter categories.
 * Platforms are ids, genres and series the raw server values, never display names. The library's
 * search box has no counterpart; a saved query would need text entry the tile setup lacks.
 */
data class LibraryLinkFilters(
    val source: SourceFilter = SourceFilter.ALL,
    val platformIds: Set<Long> = emptySet(),
    val genres: Set<String> = emptySet(),
    val series: Set<String> = emptySet(),
    val players: PlayerCountBucket? = null,
    val sort: ActiveSort = ActiveSort()
) {
    val isUnfiltered: Boolean
        get() = source == SourceFilter.ALL &&
            platformIds.isEmpty() &&
            genres.isEmpty() &&
            series.isEmpty() &&
            players == null &&
            sort.option == SortOption.TITLE
}
