package com.nendo.argosy.ui.common

import com.nendo.argosy.R
import com.nendo.argosy.core.game.AchievementType
import com.nendo.argosy.domain.model.SessionUnlock
import com.nendo.argosy.ui.theme.generated.ColorTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class SessionUnlockUiTest {

    private fun unlock(isHardcore: Boolean = true, type: String? = null) = SessionUnlock(
        badgeId = "1",
        title = "First",
        points = 5,
        badgePath = "/badge.png",
        isHardcore = isHardcore,
        unlockedAt = Instant.parse("2026-10-01T10:00:00Z"),
        description = "Finish the first level",
        type = type,
        gameTitle = null
    )

    @Test
    fun `a hardcore unlock wears gold`() {
        assertEquals(ColorTokens.Domain.AchievementTier.hardcore, unlock(isHardcore = true).recapTierColor)
    }

    @Test
    fun `a softcore unlock wears the softcore badge colour`() {
        assertEquals(ColorTokens.Domain.AchievementTier.softcore, unlock(isHardcore = false).recapTierColor)
    }

    @Test
    fun `the two modes never share a colour`() {
        assertNotEquals(unlock(isHardcore = true).recapTierColor, unlock(isHardcore = false).recapTierColor)
    }

    @Test
    fun `each mode carries its own tier label`() {
        assertEquals(R.string.ui_launch_recap_tier_hardcore, unlock(isHardcore = true).recapTierLabelRes)
        assertEquals(R.string.ui_launch_recap_tier_softcore, unlock(isHardcore = false).recapTierLabelRes)
    }

    @Test
    fun `progression and win condition unlocks carry a type label`() {
        assertEquals(
            R.string.ui_launch_recap_type_progression,
            unlock(type = AchievementType.PROGRESSION).recapTypeLabelRes
        )
        assertEquals(
            R.string.ui_launch_recap_type_win_condition,
            unlock(type = AchievementType.WIN_CONDITION).recapTypeLabelRes
        )
    }

    @Test
    fun `missable, untyped and unknown unlocks carry no type label`() {
        assertNull(unlock(type = AchievementType.MISSABLE).recapTypeLabelRes)
        assertNull(unlock(type = null).recapTypeLabelRes)
        assertNull(unlock(type = "Progression").recapTypeLabelRes)
    }
}
