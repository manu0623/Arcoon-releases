package com.nendo.argosy.domain.usecase.recap

import com.nendo.argosy.data.local.dao.PlayDayTotal
import com.nendo.argosy.data.model.PlayDay
import com.nendo.argosy.data.remote.ra.RAWebEarnedAchievement
import com.nendo.argosy.data.repository.RA_BADGE_BASE_URL
import com.nendo.argosy.data.repository.RaGameAward
import com.nendo.argosy.domain.model.RecapAwardKind
import com.nendo.argosy.domain.model.SessionUnlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class MonthlyDigestAssemblerTest {

    private val september = YearMonth.of(2026, 9)
    private val window = RecapEligibility.monthWindow(september, ZoneOffset.UTC)

    private fun earned(
        id: Long,
        date: String,
        hardcore: Boolean = false,
        badge: String? = "b$id",
        gameId: Long = 10L,
        gameTitle: String? = "Game"
    ) = RAWebEarnedAchievement(
        date = date,
        hardcoreMode = if (hardcore) 1.0 else 0.0,
        achievementId = id,
        title = "Achievement $id",
        description = "Do thing $id",
        badgeName = badge,
        points = 5,
        type = null,
        gameId = gameId,
        gameTitle = gameTitle
    )

    @Test
    fun `web unlocks keep only those inside the month, first and last instant included`() {
        val unlocks = MonthlyDigestAssembler.webUnlocks(
            listOf(
                earned(1L, "2026-08-31 23:59:59"),
                earned(2L, "2026-09-01 00:00:00"),
                earned(3L, "2026-09-30 23:59:59"),
                earned(4L, "2026-10-01 00:00:00")
            ),
            window
        )
        assertEquals(listOf("b2", "b3"), unlocks.map { it.badgeId })
    }

    @Test
    fun `a softcore then hardcore unlock of one achievement shows once as hardcore`() {
        val unlocks = MonthlyDigestAssembler.webUnlocks(
            listOf(
                earned(1L, "2026-09-02 10:00:00", hardcore = false),
                earned(1L, "2026-09-05 10:00:00", hardcore = true)
            ),
            window
        )
        val unlock = unlocks.single()
        assertTrue(unlock.isHardcore)
        assertEquals(Instant.parse("2026-09-05T10:00:00Z"), unlock.unlockedAt)
        assertEquals("${RA_BADGE_BASE_URL}b1.png", unlock.badgePath)
        assertEquals("Achievement 1", unlock.title)
    }

    @Test
    fun `entries without a badge or a readable date are left out`() {
        val unlocks = MonthlyDigestAssembler.webUnlocks(
            listOf(
                earned(1L, "2026-09-02 10:00:00", badge = null),
                earned(2L, "whenever"),
                earned(3L, "2026-09-02 10:00:00")
            ),
            window
        )
        assertEquals(listOf("b3"), unlocks.map { it.badgeId })
    }

    @Test
    fun `two achievements sharing a badge image both stay`() {
        val unlocks = MonthlyDigestAssembler.webUnlocks(
            listOf(
                earned(1L, "2026-09-02 10:00:00", badge = "00000"),
                earned(2L, "2026-09-03 10:00:00", badge = "00000", hardcore = true)
            ),
            window
        )
        assertEquals(2, unlocks.size)
        assertEquals(listOf(false, true), unlocks.map { it.isHardcore })
    }

    @Test
    fun `unlocks come out oldest first`() {
        val unlocks = MonthlyDigestAssembler.webUnlocks(
            listOf(
                earned(3L, "2026-09-20 10:00:00"),
                earned(1L, "2026-09-02 10:00:00"),
                earned(2L, "2026-09-10 10:00:00")
            ),
            window
        )
        assertEquals(listOf("b1", "b2", "b3"), unlocks.map { it.badgeId })
    }

    @Test
    fun `each web unlock carries the title of the game it was earned in`() {
        val unlocks = MonthlyDigestAssembler.webUnlocks(
            listOf(
                earned(1L, "2026-09-02 10:00:00", gameId = 10L, gameTitle = "Donkey Kong Country"),
                earned(2L, "2026-09-03 10:00:00", gameId = 11L, gameTitle = "Super Mario World"),
                earned(3L, "2026-09-04 10:00:00", gameId = 12L, gameTitle = " ")
            ),
            window
        )
        assertEquals(listOf("Donkey Kong Country", "Super Mario World", null), unlocks.map { it.gameTitle })
    }

    @Test
    fun `game titles come from the web entries that carry one`() {
        val titles = MonthlyDigestAssembler.webGameTitles(
            listOf(earned(1L, "2026-09-02 10:00:00", gameId = 10L, gameTitle = "Zelda"), earned(2L, "2026-09-02 10:00:00", gameId = 11L, gameTitle = " "))
        )
        assertEquals(mapOf(10L to "Zelda"), titles)
    }

    @Test
    fun `the digest carries covers, drops untitled awards and is empty only without play or achievements`() {
        val recap = MonthlyRecapCalculator.compute(
            month = september,
            zone = ZoneOffset.UTC,
            sessions = listOf(session(1L, Instant.parse("2026-09-05T10:00:00Z"), 60)),
            firstPlayed = mapOf(1L to Instant.parse("2026-09-05T10:00:00Z")),
            progression = emptyList(),
            awards = listOf(
                RaGameAward(55L, "mastered", "2026-09-20 10:00:00"),
                RaGameAward(56L, "completed", "2026-09-21 10:00:00")
            )
        )
        val digest = MonthlyDigestAssembler.assemble(
            recap,
            noDays,
            emptyList(),
            mapOf(1L to "/covers/1.png"),
            mapOf(55L to "Zelda")
        )

        assertEquals("/covers/1.png", digest.topGames.single().coverPath)
        assertTrue(digest.topGames.single().isNewFavorite)
        assertEquals(listOf(RecapAwardKind.MASTERED), digest.awards.map { it.kind })
        assertFalse(digest.isEmpty)

        val quiet = MonthlyRecapCalculator.compute(september, ZoneOffset.UTC, emptyList(), emptyMap(), emptyList())
        assertTrue(MonthlyDigestAssembler.assemble(quiet, noDays, emptyList(), emptyMap(), emptyMap()).isEmpty)

        val unlockOnly = MonthlyDigestAssembler.assemble(
            quiet,
            noDays,
            listOf(SessionUnlock("b1", null, null, "p", false, Instant.parse("2026-09-02T10:00:00Z"), null, null, null)),
            emptyMap(),
            emptyMap()
        )
        assertFalse(unlockOnly.isEmpty)
    }

    @Test
    fun `top games keep every game in order and mark only new favorites`() {
        val recap = MonthlyRecapCalculator.compute(
            month = september,
            zone = ZoneOffset.UTC,
            sessions = listOf(
                session(1L, Instant.parse("2026-08-05T10:00:00Z"), 300),
                session(1L, Instant.parse("2026-09-05T10:00:00Z"), 200),
                session(2L, Instant.parse("2026-09-06T10:00:00Z"), 150),
                session(3L, Instant.parse("2026-09-07T10:00:00Z"), 100),
                session(4L, Instant.parse("2026-09-08T10:00:00Z"), 50),
                session(5L, Instant.parse("2026-09-09T10:00:00Z"), 10)
            ),
            firstPlayed = emptyMap(),
            progression = emptyList(),
            topGamesLimit = Int.MAX_VALUE
        )
        val digest = MonthlyDigestAssembler.assemble(recap, noDays, emptyList(), emptyMap(), emptyMap())

        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), digest.topGames.map { it.gameId })
        assertEquals(listOf(false, true, true, false, false), digest.topGames.map { it.isNewFavorite })
    }

    @Test
    fun `month days hold every date of the month with zero where nothing was played`() {
        val days = MonthlyDigestAssembler.monthDays(
            september,
            listOf(PlayDayTotal("2026-09-01", 60_000L), PlayDayTotal("2026-09-30", 120_000L))
        )

        assertEquals(30, days.size)
        assertEquals(LocalDate.of(2026, 9, 1), days.first().date)
        assertEquals(LocalDate.of(2026, 9, 30), days.last().date)
        assertEquals(60_000L, days.first().activeMs)
        assertEquals(120_000L, days.last().activeMs)
        assertEquals(28, days.count { it.activeMs == 0L })
    }

    @Test
    fun `month days drop totals dated outside the month or unreadable`() {
        val days = MonthlyDigestAssembler.monthDays(
            september,
            listOf(
                PlayDayTotal("2026-08-31", 60_000L),
                PlayDayTotal("2026-10-01", 60_000L),
                PlayDayTotal("someday", 60_000L),
                PlayDayTotal("2026-09-15", 90_000L)
            )
        )

        assertEquals(30, days.size)
        assertEquals(listOf(LocalDate.of(2026, 9, 15)), days.filter { it.activeMs > 0L }.map { it.date })
    }

    @Test
    fun `a leap february has twenty nine days`() {
        assertEquals(29, MonthlyDigestAssembler.monthDays(YearMonth.of(2028, 2), emptyList()).size)
    }

    @Test
    fun `the busiest day marker points at the day with the most play, the earliest on a tie`() {
        val days = MonthlyDigestAssembler.monthDays(
            september,
            listOf(
                PlayDayTotal("2026-09-03", 90_000L),
                PlayDayTotal("2026-09-10", 300_000L),
                PlayDayTotal("2026-09-20", 300_000L)
            )
        )
        val digest = digestWith(days)

        assertEquals(9, digest.busiestDayIndex)
        assertEquals(3, digest.daysPlayed)
    }

    @Test
    fun `a month with no play has no busiest day`() {
        val digest = digestWith(MonthlyDigestAssembler.monthDays(september, emptyList()))

        assertNull(digest.busiestDayIndex)
        assertEquals(0, digest.daysPlayed)
    }

    @Test
    fun `a month with one day played has no busiest day`() {
        val digest = digestWith(
            MonthlyDigestAssembler.monthDays(september, listOf(PlayDayTotal("2026-09-09", 300_000L)))
        )

        assertNull(digest.busiestDayIndex)
        assertEquals(1, digest.daysPlayed)
    }

    @Test
    fun `two days played name the longer one busiest`() {
        val digest = digestWith(
            MonthlyDigestAssembler.monthDays(
                september,
                listOf(PlayDayTotal("2026-09-09", 300_000L), PlayDayTotal("2026-09-13", 90_000L))
            )
        )

        assertEquals(8, digest.busiestDayIndex)
    }

    private val noDays = MonthlyDigestAssembler.monthDays(september, emptyList())

    private fun digestWith(days: List<PlayDay>) = MonthlyDigestAssembler.assemble(
        MonthlyRecapCalculator.compute(september, ZoneOffset.UTC, emptyList(), emptyMap(), emptyList()),
        days,
        emptyList(),
        emptyMap(),
        emptyMap()
    )
}
