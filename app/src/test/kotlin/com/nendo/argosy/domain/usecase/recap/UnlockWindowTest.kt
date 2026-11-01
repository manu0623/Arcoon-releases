package com.nendo.argosy.domain.usecase.recap

import com.nendo.argosy.data.remote.romm.RomMEarnedAchievement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class UnlockWindowTest {

    private val start = Instant.parse("2026-10-01T10:00:00.400Z")
    private val end = Instant.parse("2026-10-01T12:00:00Z")

    private fun unlocks(vararg earned: RomMEarnedAchievement) =
        UnlockWindow.unlocksWithin(earned.toList(), start, end)

    @Test
    fun `a softcore unlock before the window is left out`() {
        assertTrue(unlocks(RomMEarnedAchievement("1", date = "2026-10-01 09:59:59")).isEmpty())
    }

    @Test
    fun `a softcore unlock inside the window is kept as softcore`() {
        val result = unlocks(RomMEarnedAchievement("1", date = "2026-10-01 11:00:00"))
        assertEquals(1, result.size)
        assertFalse(result.single().isHardcore)
        assertEquals(Instant.parse("2026-10-01T11:00:00Z"), result.single().unlockedAt)
    }

    @Test
    fun `a softcore unlock after the window is left out`() {
        assertTrue(unlocks(RomMEarnedAchievement("1", date = "2026-10-01 12:00:01")).isEmpty())
    }

    @Test
    fun `a hardcore unlock inside the window is kept as hardcore`() {
        val result = unlocks(
            RomMEarnedAchievement("1", date = "2026-10-01 11:00:00", dateHardcore = "2026-10-01 11:00:00")
        )
        assertTrue(result.single().isHardcore)
    }

    @Test
    fun `a hardcore unlock before the window is left out`() {
        assertTrue(
            unlocks(
                RomMEarnedAchievement("1", date = "2026-09-01 11:00:00", dateHardcore = "2026-09-01 11:00:00")
            ).isEmpty()
        )
    }

    @Test
    fun `a hardcore unlock after the window is left out`() {
        assertTrue(
            unlocks(
                RomMEarnedAchievement("1", date = "2026-10-02 11:00:00", dateHardcore = "2026-10-02 11:00:00")
            ).isEmpty()
        )
    }

    @Test
    fun `a hardcore upgrade inside the window of an older softcore unlock counts as hardcore`() {
        val result = unlocks(
            RomMEarnedAchievement("1", date = "2026-01-05 08:00:00", dateHardcore = "2026-10-01 11:30:00")
        )
        assertTrue(result.single().isHardcore)
        assertEquals(Instant.parse("2026-10-01T11:30:00Z"), result.single().unlockedAt)
    }

    @Test
    fun `a softcore unlock inside the window upgraded to hardcore later stays softcore`() {
        val result = unlocks(
            RomMEarnedAchievement("1", date = "2026-10-01 11:00:00", dateHardcore = "2026-10-05 11:00:00")
        )
        assertFalse(result.single().isHardcore)
        assertEquals(Instant.parse("2026-10-01T11:00:00Z"), result.single().unlockedAt)
    }

    @Test
    fun `an entry with no dates is left out`() {
        assertTrue(unlocks(RomMEarnedAchievement("1")).isEmpty())
    }

    @Test
    fun `an entry with only a hardcore date inside the window is kept as hardcore`() {
        assertTrue(unlocks(RomMEarnedAchievement("1", dateHardcore = "2026-10-01 11:00:00")).single().isHardcore)
    }

    @Test
    fun `an unparseable date is left out`() {
        assertTrue(unlocks(RomMEarnedAchievement("1", date = "yesterday")).isEmpty())
    }

    @Test
    fun `an unparseable hardcore date falls back to a softcore date inside the window`() {
        val result = unlocks(RomMEarnedAchievement("1", date = "2026-10-01 11:00:00", dateHardcore = "soon"))
        assertFalse(result.single().isHardcore)
    }

    @Test
    fun `an unlock in the same second the session started is kept`() {
        assertEquals(1, unlocks(RomMEarnedAchievement("1", date = "2026-10-01 10:00:00")).size)
    }

    @Test
    fun `an unlock at the window end is kept`() {
        assertEquals(1, unlocks(RomMEarnedAchievement("1", date = "2026-10-01 12:00:00")).size)
    }

    @Test
    fun `zoned iso dates are read`() {
        assertEquals(1, unlocks(RomMEarnedAchievement("1", date = "2026-10-01T13:00:00+02:00")).size)
    }

    @Test
    fun `unlocks come back oldest first whatever the input order`() {
        val result = unlocks(
            RomMEarnedAchievement("late", date = "2026-10-01 11:50:00"),
            RomMEarnedAchievement("early", date = "2026-10-01 10:10:00"),
            RomMEarnedAchievement("outside", date = "2026-10-03 10:10:00")
        )
        assertEquals(listOf("early", "late"), result.map { it.badgeId })
    }
}
