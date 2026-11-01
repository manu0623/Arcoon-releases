package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.nendo.argosy.R
import com.nendo.argosy.core.game.AchievementType
import com.nendo.argosy.domain.model.SessionUnlock
import com.nendo.argosy.ui.theme.generated.ColorTokens

/**
 * The recap tile's border colour, the same tier colours achievement badges use.
 */
val SessionUnlock.recapTierColor: Color
    get() = if (isHardcore) {
        ColorTokens.Domain.AchievementTier.hardcore
    } else {
        ColorTokens.Domain.AchievementTier.softcore
    }

@get:StringRes
val SessionUnlock.recapTierLabelRes: Int
    get() = if (isHardcore) R.string.ui_launch_recap_tier_hardcore else R.string.ui_launch_recap_tier_softcore

/**
 * The recap detail panel's type label, or null for every type but progression and win condition.
 */
@get:StringRes
val SessionUnlock.recapTypeLabelRes: Int?
    get() = when (type) {
        AchievementType.PROGRESSION -> R.string.ui_launch_recap_type_progression
        AchievementType.WIN_CONDITION -> R.string.ui_launch_recap_type_win_condition
        else -> null
    }
