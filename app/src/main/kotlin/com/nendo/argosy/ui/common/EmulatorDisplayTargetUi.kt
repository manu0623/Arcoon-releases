package com.nendo.argosy.ui.common

import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.EmulatorDisplayTarget

/**
 * The display label. `EmulatorDisplayTarget` lives in `data/` and must not import `R`, so the
 * label is attached here and the enum name stays the stored token.
 */
@get:androidx.annotation.StringRes
val EmulatorDisplayTarget.labelRes: Int
    get() = when (this) {
        EmulatorDisplayTarget.DEFAULT -> R.string.settings_platform_display_target_default
        EmulatorDisplayTarget.PRESENTATION -> R.string.settings_platform_display_target_presentation
        EmulatorDisplayTarget.PRIMARY -> R.string.settings_platform_display_target_primary
        EmulatorDisplayTarget.APP_SCREEN -> R.string.settings_platform_display_target_app_screen
    }
