package com.nendo.argosy.ui.dualscreen.dashboard

import com.nendo.argosy.core.game.AchievementUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LatestAchievementTest {

    private fun achievement(id: Long, unlockedAt: Long?) = AchievementUi(
        raId = id,
        title = "a$id",
        description = null,
        points = 5,
        type = null,
        badgeUrl = null,
        isUnlocked = unlockedAt != null,
        unlockedAtMillis = unlockedAt
    )

    @Test
    fun `picks the most recent unlock when it is listed last`() {
        val list = listOf(achievement(1, 100L), achievement(2, null), achievement(3, 300L))
        assertEquals(3L, list.latestUnlocked()?.raId)
    }

    @Test
    fun `picks the most recent unlock when it is listed first`() {
        val list = listOf(achievement(3, 300L), achievement(2, null), achievement(1, 100L))
        assertEquals(3L, list.latestUnlocked()?.raId)
    }

    @Test
    fun `nothing unlocked yields no latest achievement`() {
        assertNull(listOf(achievement(1, null), achievement(2, null)).latestUnlocked())
    }
}
