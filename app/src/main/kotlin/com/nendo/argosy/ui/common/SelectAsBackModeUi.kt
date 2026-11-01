package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.data.local.entity.SelectAsBackMode

@get:StringRes
val SelectAsBackMode.labelRes: Int
    get() = when (this) {
        SelectAsBackMode.AUTO -> R.string.settings_builtin_controls_select_as_back_auto
        SelectAsBackMode.ON -> R.string.settings_builtin_controls_select_as_back_on
        SelectAsBackMode.OFF -> R.string.settings_builtin_controls_select_as_back_off
    }
