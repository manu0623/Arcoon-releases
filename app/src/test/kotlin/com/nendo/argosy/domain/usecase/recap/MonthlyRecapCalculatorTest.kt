package com.nendo.argosy.domain.usecase.recap

import com.nendo.argosy.data.local.entity.PlaySessionEntity
import com.nendo.argosy.data.remote.romm.RomMEarnedAchievement
import com.nendo.argosy.data.remote.romm.RomMRAGameProgression
import com.nendo.argosy.data.repository.RaGameAward
import com.nendo.argosy.domain.model.RecapAwardKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class MonthlyRecapCalculatorTest {

    private val october = YearMonth.of(2026, 10)
    private val utc: ZoneId = ZoneOffset.UTC

    private fun at(text: String): Instant = Instant.parse(text)

    private fun compute(
        sessions: List<PlaySessionEntity>,
        firstPlayed: Map<Long, Instant> = sessions.groupBy { it.gameId }
            .mapValues { (_, rows) -> rows.minOf { it.startTime } },
        progression: List<RomMRAGameProgression> = emptyList(),
        zone: ZoneId = utc
    ) = MonthlyRecapCalculator.compute(october, zone, sessions, firstPlayed, progression)

    @Test
    fun `totals count only sessions starting inside the month`() {
        val recap = compute(
            listOf(
                session(1L, at("2026-09-30T23:00:00Z"), 60),
                session(1L, at("2026-10-01T00:00:00Z"), 30),
                session(2L, at("2026-10-01T20:00:00Z"), 45),
                session(2L, at("2026-10-15T20:00:00Z"), 15),
                session(3L, at("2026-11-01T00:00:00Z"), 90)
            )
        )
        assertEquals(90L * 60_000, recap.totalActiveMs)
        assertEquals(3, recap.sessionCount)
        assertEquals(45L * 60_000, recap.longestSessionMs)
    }

    @Test
    fun `month boundaries follow the zone`() {
        val newYork = ZoneId.of("America/New_York")
        val recap = compute(
            listOf(
                session(1L, at("2026-11-01T02:00:00Z"), 30),
                session(2L, at("2026-10-01T02:00:00Z"), 30)
            ),
            zone = newYork
        )
        assertEquals(1, recap.sessionCount)
        assertEquals(1L, recap.topGames.single().gameId)
    }

    @Test
    fun `top games are ordered by active time with ties broken by game id`() {
        val recap = compute(
            listOf(
                session(5L, at("2026-10-02T10:00:00Z"), 30),
                session(4L, at("2026-10-03T10:00:00Z"), 30),
                session(9L, at("2026-10-04T10:00:00Z"), 90)
            )
        )
        assertEquals(listOf(9L, 4L, 5L), recap.topGames.map { it.gameId })
    }

    @Test
    fun `top games are capped`() {
        val sessions = (1L..8L).map { session(it, at("2026-10-02T10:00:00Z").plusSeconds(it * 3600), it) }
        assertEquals(MonthlyRecapCalculator.DEFAULT_TOP_GAMES, compute(sessions).topGames.size)
    }

    @Test
    fun `a top three game that was also in last month's top three is not a new favorite`() {
        val recap = compute(
            listOf(
                session(1L, at("2026-09-05T10:00:00Z"), 120),
                session(1L, at("2026-10-05T10:00:00Z"), 120)
            )
        )
        assertTrue(recap.newFavorites.isEmpty())
    }

    @Test
    fun `a top three game missing from last month's top three is a new favorite`() {
        val recap = compute(
            listOf(
                session(1L, at("2026-08-05T10:00:00Z"), 10),
                session(2L, at("2026-09-05T10:00:00Z"), 120),
                session(3L, at("2026-09-06T10:00:00Z"), 110),
                session(4L, at("2026-09-07T10:00:00Z"), 100),
                session(1L, at("2026-09-08T10:00:00Z"), 5),
                session(1L, at("2026-10-05T10:00:00Z"), 120)
            )
        )
        val favorite = recap.newFavorites.single()
        assertEquals(1L, favorite.gameId)
        assertFalse(favorite.isFirstPlay)
    }

    @Test
    fun `a game first played this month is a new favorite marked as a first play`() {
        val recap = compute(listOf(session(6L, at("2026-10-05T10:00:00Z"), 60)))
        assertTrue(recap.newFavorites.single().isFirstPlay)
    }

    @Test
    fun `a first play outside the month's top three is not a favorite`() {
        val recap = compute(
            listOf(
                session(1L, at("2026-10-01T10:00:00Z"), 300),
                session(2L, at("2026-10-02T10:00:00Z"), 200),
                session(3L, at("2026-10-03T10:00:00Z"), 100),
                session(4L, at("2026-10-04T10:00:00Z"), 5)
            )
        )
        assertEquals(listOf(1L, 2L, 3L), recap.newFavorites.map { it.gameId })
    }

    @Test
    fun `a game fourth last month and top three this month is a new favorite`() {
        val recap = compute(
            listOf(
                session(1L, at("2026-09-02T10:00:00Z"), 300),
                session(2L, at("2026-09-03T10:00:00Z"), 200),
                session(3L, at("2026-09-04T10:00:00Z"), 100),
                session(4L, at("2026-09-05T10:00:00Z"), 50),
                session(4L, at("2026-10-05T10:00:00Z"), 400),
                session(1L, at("2026-10-06T10:00:00Z"), 10)
            )
        )
        assertEquals(listOf(4L), recap.newFavorites.map { it.gameId })
        assertFalse(recap.newFavorites.single().isFirstPlay)
    }

    @Test
    fun `play at the last instant of the previous month counts toward its top three`() {
        val recap = compute(
            listOf(
                session(1L, at("2026-09-30T23:59:59Z"), 60),
                session(1L, at("2026-10-01T00:00:00Z"), 60)
            )
        )
        assertTrue(recap.newFavorites.isEmpty())
    }

    @Test
    fun `play before the previous month never counts toward its top three`() {
        val recap = compute(
            listOf(
                session(1L, at("2026-08-31T23:59:59Z"), 600),
                session(1L, at("2026-10-01T00:00:00Z"), 60)
            )
        )
        assertEquals(listOf(1L), recap.newFavorites.map { it.gameId })
    }

    @Test
    fun `new favorites keep this month's order by active time`() {
        val recap = compute(
            listOf(
                session(7L, at("2026-10-02T10:00:00Z"), 30),
                session(8L, at("2026-10-03T10:00:00Z"), 90),
                session(9L, at("2026-10-04T10:00:00Z"), 60)
            )
        )
        assertEquals(listOf(8L, 9L, 7L), recap.newFavorites.map { it.gameId })
    }

    @Test
    fun `unlocks inside the month are kept with their mode and games without an ra id are skipped`() {
        val recap = compute(
            sessions = emptyList(),
            progression = listOf(
                RomMRAGameProgression(
                    romRaId = 10L,
                    earnedAchievements = listOf(
                        RomMEarnedAchievement("before", date = "2026-09-30 23:59:59"),
                        RomMEarnedAchievement("first", date = "2026-10-01 00:00:00"),
                        RomMEarnedAchievement(
                            "hard", date = "2026-10-10 10:00:00", dateHardcore = "2026-10-10 10:00:00"
                        ),
                        RomMEarnedAchievement("last", date = "2026-10-31 23:59:59"),
                        RomMEarnedAchievement("after", date = "2026-11-01 00:00:00")
                    )
                ),
                RomMRAGameProgression(
                    romRaId = null,
                    earnedAchievements = listOf(RomMEarnedAchievement("orphan", date = "2026-10-10 10:00:00"))
                )
            )
        )
        assertEquals(listOf("first", "hard", "last"), recap.unlocks.map { it.unlock.badgeId })
        assertEquals(listOf(false, true, false), recap.unlocks.map { it.unlock.isHardcore })
        assertTrue(recap.unlocks.all { it.raGameId == 10L })
    }

    @Test
    fun `awards dated inside the month are kept and every other award is dropped`() {
        val recap = MonthlyRecapCalculator.compute(
            october, utc, emptyList(), emptyMap(), emptyList(),
            awards = listOf(
                RaGameAward(1L, "mastered", "2026-10-20T10:00:00+00:00"),
                RaGameAward(2L, "beaten-hardcore", "2026-10-02 08:00:00"),
                RaGameAward(3L, "completed", "2026-09-20 10:00:00"),
                RaGameAward(4L, "beaten-softcore", null),
                RaGameAward(5L, "legendary", "2026-10-20 10:00:00"),
                RaGameAward(6L, null, "2026-10-20 10:00:00")
            )
        )
        assertEquals(listOf(2L, 1L), recap.awards.map { it.raGameId })
        assertEquals(listOf(RecapAwardKind.BEATEN_HARDCORE, RecapAwardKind.MASTERED), recap.awards.map { it.kind })
    }

    @Test
    fun `a RomM mastery is dated by its most recent unlock when every achievement is hardcore`() {
        val recap = compute(
            sessions = emptyList(),
            progression = listOf(
                RomMRAGameProgression(
                    romRaId = 1L, maxPossible = 20, numAwarded = 20, numAwardedHardcore = 20,
                    mostRecentAwardedDate = "2026-10-20 10:00:00", highestAwardKind = "mastered"
                )
            )
        )
        val award = recap.awards.single()
        assertEquals(RecapAwardKind.MASTERED, award.kind)
        assertEquals(at("2026-10-20T10:00:00Z"), award.awardedAt)
    }

    @Test
    fun `a RomM mastery short of the full set is not shown`() {
        val recap = compute(
            sessions = emptyList(),
            progression = listOf(
                RomMRAGameProgression(
                    romRaId = 1L, maxPossible = 20, numAwarded = 20, numAwardedHardcore = 19,
                    mostRecentAwardedDate = "2026-10-20 10:00:00", highestAwardKind = "mastered"
                ),
                RomMRAGameProgression(
                    romRaId = 2L, maxPossible = 20, numAwarded = 18, numAwardedHardcore = 18,
                    mostRecentAwardedDate = "2026-10-21 10:00:00", highestAwardKind = "completed"
                )
            )
        )
        assertTrue(recap.awards.isEmpty())
    }

    @Test
    fun `RomM beaten awards are skipped`() {
        val recap = compute(
            sessions = emptyList(),
            progression = listOf(
                RomMRAGameProgression(
                    romRaId = 1L, maxPossible = 20, numAwarded = 20, numAwardedHardcore = 20,
                    mostRecentAwardedDate = "2026-10-20 10:00:00", highestAwardKind = "beaten-hardcore"
                ),
                RomMRAGameProgression(
                    romRaId = 2L, maxPossible = 20, numAwarded = 5,
                    mostRecentAwardedDate = "2026-10-21 10:00:00", highestAwardKind = "beaten-softcore"
                )
            )
        )
        assertTrue(recap.awards.isEmpty())
    }

    @Test
    fun `the longest session ignores sessions outside the month`() {
        val recap = compute(
            listOf(
                session(1L, at("2026-09-30T10:00:00Z"), 500),
                session(1L, at("2026-10-05T10:00:00Z"), 200),
                session(2L, at("2026-10-06T10:00:00Z"), 20),
                session(3L, at("2026-11-01T00:00:00Z"), 900)
            )
        )
        assertEquals(200L * 60_000, recap.longestSessionMs)
    }

    @Test
    fun `a month with no sessions has no totals`() {
        val recap = compute(listOf(session(1L, at("2026-09-05T10:00:00Z"), 60)))
        assertEquals(0L, recap.longestSessionMs)
        assertEquals(0, recap.sessionCount)
        assertTrue(recap.topGames.isEmpty())
        assertTrue(recap.newFavorites.isEmpty())
    }
}
