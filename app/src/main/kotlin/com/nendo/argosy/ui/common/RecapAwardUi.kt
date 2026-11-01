package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.RecapAwardKind
import com.nendo.argosy.ui.theme.generated.ColorTokens

@get:StringRes
val RecapAwardKind.labelRes: Int
    get() = when (this) {
        RecapAwardKind.BEATEN_SOFTCORE -> R.string.ui_monthly_recap_award_beaten_softcore
        RecapAwardKind.BEATEN_HARDCORE -> R.string.ui_monthly_recap_award_beaten_hardcore
        RecapAwardKind.COMPLETED -> R.string.ui_monthly_recap_award_completed
        RecapAwardKind.MASTERED -> R.string.ui_monthly_recap_award_mastered
    }

val RecapAwardKind.tierColor: Color
    get() = when (this) {
        RecapAwardKind.BEATEN_HARDCORE, RecapAwardKind.MASTERED -> ColorTokens.Domain.AchievementTier.hardcore
        RecapAwardKind.BEATEN_SOFTCORE, RecapAwardKind.COMPLETED -> ColorTokens.Domain.AchievementTier.softcore
    }
