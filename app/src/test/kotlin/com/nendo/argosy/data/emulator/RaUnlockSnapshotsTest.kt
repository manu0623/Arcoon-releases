package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.repository.RetroAchievementsRepository.GameUnlocks
import com.nendo.argosy.domain.model.SnapshotUnlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RaUnlockSnapshotsTest {

    private fun unlocks(softcore: Set<Long> = emptySet(), hardcore: Set<Long> = emptySet()) =
        GameUnlocks(unlockedIds = softcore, hardcoreUnlockedIds = hardcore)

    @Test
    fun `an id new to the softcore set only is a softcore unlock`() {
        val diff = RaUnlockSnapshots.diff(unlocks(), unlocks(softcore = setOf(5L)))
        assertEquals(listOf(SnapshotUnlock(5L, isHardcore = false)), diff)
    }

    @Test
    fun `an id new to the hardcore set is a hardcore unlock, even when it is new to softcore too`() {
        val diff = RaUnlockSnapshots.diff(unlocks(), unlocks(softcore = setOf(5L), hardcore = setOf(5L)))
        assertEquals(listOf(SnapshotUnlock(5L, isHardcore = true)), diff)
    }

    @Test
    fun `an id new to hardcore alone is a hardcore unlock`() {
        val diff = RaUnlockSnapshots.diff(unlocks(softcore = setOf(5L)), unlocks(softcore = setOf(5L), hardcore = setOf(5L)))
        assertEquals(listOf(SnapshotUnlock(5L, isHardcore = true)), diff)
    }

    @Test
    fun `ids already unlocked at launch are not this session's`() {
        val diff = RaUnlockSnapshots.diff(
            unlocks(softcore = setOf(1L, 2L), hardcore = setOf(2L)),
            unlocks(softcore = setOf(1L, 2L), hardcore = setOf(2L))
        )
        assertEquals(emptyList<SnapshotUnlock>(), diff)
    }

    @Test
    fun `an id unlocked in hardcore before launch never reappears as a softcore unlock`() {
        val diff = RaUnlockSnapshots.diff(unlocks(hardcore = setOf(2L)), unlocks(softcore = setOf(2L), hardcore = setOf(2L)))
        assertEquals(emptyList<SnapshotUnlock>(), diff)
    }

    @Test
    fun `a missing launch snapshot yields no unlocks rather than everything`() {
        assertNull(RaUnlockSnapshots.diff(null, unlocks(softcore = setOf(1L, 2L), hardcore = setOf(2L))))
    }

    @Test
    fun `mixed unlocks come back ordered by achievement id`() {
        val diff = RaUnlockSnapshots.diff(
            unlocks(softcore = setOf(1L)),
            unlocks(softcore = setOf(1L, 9L, 4L), hardcore = setOf(9L))
        )
        assertEquals(listOf(SnapshotUnlock(4L, false), SnapshotUnlock(9L, true)), diff)
    }

    @Test
    fun `encoding round-trips`() {
        val list = listOf(SnapshotUnlock(4L, false), SnapshotUnlock(9L, true))
        assertEquals("s:4,h:9", RaUnlockSnapshots.encode(list))
        assertEquals(list, RaUnlockSnapshots.decode(RaUnlockSnapshots.encode(list)))
    }

    @Test
    fun `an empty difference decodes to an empty list, distinct from no snapshot`() {
        assertEquals(emptyList<SnapshotUnlock>(), RaUnlockSnapshots.decode(RaUnlockSnapshots.encode(emptyList())))
        assertNull(RaUnlockSnapshots.decode(null))
    }

    @Test
    fun `unparseable entries are left out`() {
        val decoded = RaUnlockSnapshots.decode("h:9,x:3,s:abc,s:4")
        assertTrue(decoded == listOf(SnapshotUnlock(9L, true), SnapshotUnlock(4L, false)))
    }
}
