package com.nendo.argosy.domain.usecase.journey

import com.nendo.argosy.data.local.entity.AchievementEntity
import com.nendo.argosy.domain.model.JourneyCategory
import com.nendo.argosy.domain.model.JourneyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class JourneyReconstructionTest {

    private fun row(
        raId: Long,
        title: String,
        points: Int = 10,
        type: String? = null,
        unlockedAt: Long? = 1_000L
    ) = AchievementEntity(
        gameId = 7L,
        raId = raId,
        title = title,
        description = "Complete stage $raId.",
        points = points,
        type = type,
        badgeUrl = null,
        badgeUrlLock = null,
        unlockedAt = unlockedAt
    )

    @Test
    fun `locked rows are totals only`() {
        val journey = JourneyReconstruction.build(
            gameId = 7L,
            gameTitle = "Example",
            platformName = "SNES",
            raGameId = 1L,
            rows = listOf(row(1, "First steps", unlockedAt = 10L), row(2, "Locked", unlockedAt = null))
        )
        assertEquals(1, journey.progress.unlocked)
        assertEquals(2, journey.progress.total)
        assertEquals(1, journey.entries.size)
        assertEquals(10, journey.progress.unlockedPoints)
        assertEquals(20, journey.progress.totalPoints)
    }

    @Test
    fun `entries are chronological`() {
        val journey = JourneyReconstruction.build(
            gameId = 7L,
            gameTitle = "Example",
            platformName = null,
            raGameId = null,
            rows = listOf(row(2, "Second", unlockedAt = 500L), row(1, "First", unlockedAt = 100L))
        )
        assertEquals(listOf(1L, 2L), journey.entries.map { it.raId })
    }

    @Test
    fun `phases run by category in order`() {
        val entries = listOf(
            row(1, "Stage one", unlockedAt = 100L),
            row(2, "Stage two", unlockedAt = 200L),
            row(3, "Hidden cave", unlockedAt = 300L),
            row(4, "Stage three", unlockedAt = 400L)
        ).mapNotNull(JourneyReconstruction::toEntry)

        val phases = JourneyReconstruction.phases(entries)
        assertEquals(3, phases.size)
        assertEquals(JourneyCategory.PROGRESSION, phases[0].category)
        assertEquals(2, phases[0].entries.size)
        assertEquals(JourneyCategory.EXPLORATION, phases[1].category)
        assertEquals(JourneyCategory.PROGRESSION, phases[2].category)
    }

    @Test
    fun `phase count is capped by merging the cheapest neighbours`() {
        val entries = (1L..12L).map { index ->
            val title = if (index % 2 == 0L) "Stage $index" else "Hidden room $index"
            row(index, title, points = index.toInt(), unlockedAt = index * 100).let(JourneyReconstruction::toEntry)!!
        }
        val phases = JourneyReconstruction.phases(entries)
        assertTrue(phases.size <= JourneyReconstruction.MAX_PHASES)
        assertEquals(12, phases.sumOf { it.entries.size })
    }

    @Test
    fun `every achievement unlocked is mastery`() {
        val journey = JourneyReconstruction.build(
            gameId = 7L,
            gameTitle = "Example",
            platformName = null,
            raGameId = null,
            rows = listOf(row(1, "Stage one", unlockedAt = 100L), row(2, "Stage two", unlockedAt = 200L))
        )
        assertEquals(JourneyState.MASTERED, journey.progress.state)
        assertEquals(100, journey.progress.percent)
    }

    @Test
    fun `a win condition is main completion, not mastery`() {
        val journey = JourneyReconstruction.build(
            gameId = 7L,
            gameTitle = "Example",
            platformName = null,
            raGameId = null,
            rows = listOf(
                row(1, "Stage one", unlockedAt = 100L),
                row(2, "The end", type = "win_condition", unlockedAt = 200L),
                row(3, "Locked", unlockedAt = null)
            )
        )
        assertEquals(JourneyState.MAIN_COMPLETE, journey.progress.state)
    }

    @Test
    fun `a short record is early progress`() {
        val journey = JourneyReconstruction.build(
            gameId = 7L,
            gameTitle = "Example",
            platformName = null,
            raGameId = null,
            rows = listOf(row(1, "Stage one", unlockedAt = 100L), row(2, "Locked", unlockedAt = null))
        )
        assertEquals(JourneyState.EARLY, journey.progress.state)
    }

    @Test
    fun `evidence key tracks the unlock set`() {
        val first = JourneyReconstruction.evidenceKey(
            listOf(row(1, "Stage one", unlockedAt = 100L)).mapNotNull(JourneyReconstruction::toEntry)
        )
        val same = JourneyReconstruction.evidenceKey(
            listOf(row(1, "Stage one", unlockedAt = 100L)).mapNotNull(JourneyReconstruction::toEntry)
        )
        val later = JourneyReconstruction.evidenceKey(
            listOf(row(1, "Stage one", unlockedAt = 900L)).mapNotNull(JourneyReconstruction::toEntry)
        )
        assertEquals(first, same)
        assertNotEquals(first, later)
    }

    @Test
    fun `milestones name the first unlock and the completion`() {
        val journey = JourneyReconstruction.build(
            gameId = 7L,
            gameTitle = "Example",
            platformName = null,
            raGameId = null,
            rows = listOf(
                row(1, "Stage one", unlockedAt = 100L),
                row(2, "The end", type = "win_condition", points = 50, unlockedAt = 200L),
                row(3, "Hidden room", points = 25, unlockedAt = 150L)
            )
        )
        val ids = journey.milestones.map { it.raId }
        assertTrue(ids.contains(1L))
        assertTrue(ids.contains(2L))
        assertEquals(journey.milestones.map { it.unlockedAt }, journey.milestones.map { it.unlockedAt }.sorted())
    }

    @Test
    fun `hardcore unlock dates the entry`() {
        val entity = AchievementEntity(
            gameId = 7L,
            raId = 4L,
            title = "Stage one",
            description = null,
            points = 5,
            type = null,
            badgeUrl = null,
            badgeUrlLock = null,
            unlockedAt = null,
            unlockedHardcoreAt = 750L
        )
        val entry = JourneyReconstruction.toEntry(entity)
        assertEquals(Instant.ofEpochMilli(750L), entry?.unlockedAt)
        assertEquals(true, entry?.isHardcore)
    }

    @Test
    fun `a locked row has no entry`() {
        assertNull(JourneyReconstruction.toEntry(row(9, "Locked", unlockedAt = null)))
    }
}
