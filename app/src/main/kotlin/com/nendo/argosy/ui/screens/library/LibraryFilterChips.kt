package com.nendo.argosy.ui.screens.library

import com.nendo.argosy.ui.components.ActiveFilterChipUi
import com.nendo.argosy.ui.components.ActiveFilterKind

val FilterCategory.chipKind: ActiveFilterKind
    get() = when (this) {
        FilterCategory.SORT -> ActiveFilterKind.SORT
        FilterCategory.SEARCH -> ActiveFilterKind.SEARCH
        FilterCategory.SOURCE -> ActiveFilterKind.SOURCE
        FilterCategory.PLATFORM -> ActiveFilterKind.PLATFORM
        FilterCategory.GENRE -> ActiveFilterKind.GENRE
        FilterCategory.REGION -> ActiveFilterKind.REGION
        FilterCategory.PLAYERS -> ActiveFilterKind.PLAYERS
        FilterCategory.SERIES -> ActiveFilterKind.SERIES
    }

val ActiveFilters.chips: List<ActiveFilterChipUi>
    get() = entries.map { entry ->
        ActiveFilterChipUi(
            kind = entry.category.chipKind,
            labelRes = entry.labelRes,
            text = entry.text,
            count = entry.count
        )
    }
