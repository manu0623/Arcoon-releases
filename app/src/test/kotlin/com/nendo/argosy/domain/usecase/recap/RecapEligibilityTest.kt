package com.nendo.argosy.domain.usecase.recap

import com.nendo.argosy.domain.model.SessionUnlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class RecapEligibilityTest {

    private val now = Instant.parse("2026-10-13T12:00:00Z")
    private val utc: ZoneId = ZoneOffset.UTC

    private fun endedAgo(gap: Duration, raGameId: Long? = null) =
        session(gameId = 1L, start = now.minus(gap).minusSeconds(3600), activeMinutes = 60, end = now.minus(gap), raGameId = raGameId)

    @Test
    fun `an ra game last played exactly seven days ago gets a recap`() {
        val recap = RecapEligibility.preLaunchRecapFor(50L, endedAgo(Duration.ofDays(7)), now, enabled = true)
        assertNotNull(recap)
        assertEquals(7L, recap!!.lastSession.daysSince)
    }

    @Test
    fun `an ra game last played just under seven days ago gets none`() {
        assertNull(
            RecapEligibility.preLaunchRecapFor(
                50L, endedAgo(Duration.ofDays(7).minusSeconds(1)), now, enabled = true
            )
        )
    }

    @Test
    fun `an ra game last played long ago gets a recap`() {
        assertEquals(
            40L,
            RecapEligibility.preLaunchRecapFor(50L, endedAgo(Duration.ofDays(40)), now, enabled = true)!!.lastSession.daysSince
        )
    }

    @Test
    fun `a recap reads its unlocks from the last session's span`() {
        val session = endedAgo(Duration.ofDays(9))
        val recap = RecapEligibility.preLaunchRecapFor(50L, session, now, enabled = true)!!
        assertEquals(session.startTime, recap.unlockWindowStart)
        assertEquals(session.endTime, recap.unlockWindowEnd)
        assertEquals(session.ownerUserId, recap.ownerUserId)
    }

    @Test
    fun `last session facts never count negative days`() {
        assertEquals(0L, RecapEligibility.lastSessionOf(endedAgo(Duration.ofDays(-1)), now).daysSince)
        assertEquals(0L, RecapEligibility.lastSessionOf(endedAgo(Duration.ofHours(2)), now).daysSince)
    }

    @Test
    fun `a recap with neither unlocks nor rich presence is skipped`() {
        val recap = RecapEligibility.preLaunchRecapFor(50L, endedAgo(Duration.ofDays(9)), now, enabled = true)!!
        assertNull(RecapEligibility.launchRecapFor(recap.lastSession, "Game", emptyList()))
    }

    @Test
    fun `a recap with no session and no unlocks is skipped`() {
        assertNull(RecapEligibility.launchRecapFor(null, "Game", emptyList()))
    }

    @Test
    fun `a blank rich presence line does not count as something to show`() {
        val session = session(gameId = 1L, start = now.minus(Duration.ofDays(9)), activeMinutes = 60, richPresence = "  ")
        val recap = RecapEligibility.preLaunchRecapFor(50L, session, now, enabled = true)!!
        assertNull(RecapEligibility.launchRecapFor(recap.lastSession, "Game", emptyList()))
    }

    @Test
    fun `rich presence alone is worth showing`() {
        val session = session(gameId = 1L, start = now.minus(Duration.ofDays(9)), activeMinutes = 60, richPresence = "World 2-1")
        val recap = RecapEligibility.preLaunchRecapFor(50L, session, now, enabled = true)!!
        val shown = RecapEligibility.launchRecapFor(recap.lastSession, "Game", emptyList())
        assertEquals("World 2-1", shown!!.lastSession!!.richPresence)
    }

    @Test
    fun `unlocks alone are worth showing`() {
        val unlock = SessionUnlock(
            "1",
            "First",
            5,
            "/badge.png",
            isHardcore = true,
            unlockedAt = now.minus(Duration.ofDays(9)),
            description = "Finish the first level",
            type = null,
            gameTitle = null
        )
        assertEquals(listOf(unlock), RecapEligibility.launchRecapFor(null, "Game", listOf(unlock))!!.unlocks)
    }

    @Test
    fun `a game without an ra id never gets a recap`() {
        assertNull(RecapEligibility.preLaunchRecapFor(null, endedAgo(Duration.ofDays(30), raGameId = 50L), now, enabled = true))
    }

    @Test
    fun `a game never played gets none`() {
        assertNull(RecapEligibility.preLaunchRecapFor(50L, null, now, enabled = true))
    }

    @Test
    fun `the setting off removes the recap`() {
        assertNull(RecapEligibility.preLaunchRecapFor(50L, endedAgo(Duration.ofDays(30)), now, enabled = false))
    }

    @Test
    fun `a session that ends in the future gets none`() {
        assertNull(RecapEligibility.preLaunchRecapFor(50L, endedAgo(Duration.ofDays(-1)), now, enabled = true))
    }

    @Test
    fun `the session's own ra id wins over the game's`() {
        val recap = RecapEligibility.preLaunchRecapFor(50L, endedAgo(Duration.ofDays(9), raGameId = 49L), now, enabled = true)
        assertEquals(49L, recap!!.raGameId)
    }

    @Test
    fun `the digest for last month is due when it has not been shown`() {
        assertEquals(
            YearMonth.of(2026, 9),
            RecapEligibility.monthlyDigestDue(now, utc, YearMonth.of(2026, 8), hadSessionsLastMonth = true, enabled = true)
        )
    }

    @Test
    fun `the digest is due on a fresh install with sessions last month`() {
        assertEquals(
            YearMonth.of(2026, 9),
            RecapEligibility.monthlyDigestDue(now, utc, null, hadSessionsLastMonth = true, enabled = true)
        )
    }

    @Test
    fun `the digest already shown this month is not due again`() {
        assertNull(RecapEligibility.monthlyDigestDue(now, utc, YearMonth.of(2026, 9), hadSessionsLastMonth = true, enabled = true))
    }

    @Test
    fun `a shown month later than last month is not due`() {
        assertNull(RecapEligibility.monthlyDigestDue(now, utc, YearMonth.of(2026, 10), hadSessionsLastMonth = true, enabled = true))
    }

    @Test
    fun `a month with no play skips the digest`() {
        assertNull(RecapEligibility.monthlyDigestDue(now, utc, YearMonth.of(2026, 8), hadSessionsLastMonth = false, enabled = true))
    }

    @Test
    fun `the setting off removes the digest`() {
        assertNull(RecapEligibility.monthlyDigestDue(now, utc, null, hadSessionsLastMonth = true, enabled = false))
    }

    @Test
    fun `the digest month follows the zone at a month boundary`() {
        val firstOfMonthUtc = Instant.parse("2026-11-01T02:00:00Z")
        assertEquals(YearMonth.of(2026, 10), RecapEligibility.digestMonthAt(firstOfMonthUtc, utc))
        assertEquals(YearMonth.of(2026, 9), RecapEligibility.digestMonthAt(firstOfMonthUtc, ZoneId.of("America/New_York")))
    }

    @Test
    fun `january's digest covers december of the previous year`() {
        assertEquals(YearMonth.of(2026, 12), RecapEligibility.digestMonthAt(Instant.parse("2027-01-02T00:00:00Z"), utc))
    }
}
