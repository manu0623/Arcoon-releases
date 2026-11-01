package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.data.model.SourceFilter

@get:StringRes
val SourceFilter.labelRes: Int
    get() = when (this) {
        SourceFilter.ALL -> R.string.source_filter_all
        SourceFilter.PLAYABLE -> R.string.source_filter_playable
        SourceFilter.FAVORITES -> R.string.source_filter_favorites
        SourceFilter.HIDDEN -> R.string.source_filter_hidden
    }
