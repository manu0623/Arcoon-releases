package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.RequiredAction
import com.nendo.argosy.ui.screens.settings.SettingsSection

/**
 * The display label for a changelog's required action.
 *
 * `RequiredAction` lives in `domain/model` and must not import `R`, so the label is attached here
 * instead, following the shape of [CompletionStatusUi].
 */
@get:StringRes
val RequiredAction.labelRes: Int
    get() = when (this) {
        RequiredAction.ReloginRomM -> R.string.ui_changelog_action_relogin_romm
        RequiredAction.ResyncLibrary -> R.string.ui_changelog_action_resync_library
        RequiredAction.ClearCache -> R.string.ui_changelog_action_clear_cache
    }

val RequiredAction.settingsSection: SettingsSection
    get() = when (this) {
        RequiredAction.ReloginRomM -> SettingsSection.ROMM
        RequiredAction.ResyncLibrary -> SettingsSection.ROMM
        RequiredAction.ClearCache -> SettingsSection.STORAGE
    }
