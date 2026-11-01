package com.nendo.argosy.domain.usecase.recap

import com.nendo.argosy.data.emulator.RaUnlockSnapshots
import com.nendo.argosy.data.local.entity.PlaySessionEntity
import com.nendo.argosy.domain.model.LaunchRecap
import com.nendo.argosy.domain.model.MonthWindow
import com.nendo.argosy.domain.model.PreLaunchRecap
import com.nendo.argosy.domain.model.RecapLastSession
import com.nendo.argosy.domain.model.SessionUnlock
import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

object RecapEligibility {

    val PRE_LAUNCH_MIN_GAP: Duration = Duration.ofDays(7)

    /**
     * The recap to show before launching a game, or null. Only RetroAchievements games qualify
     * ([gameRaId] non-null), and only when [lastSession] ended at least [PRE_LAUNCH_MIN_GAP]
     * before [now]. A session that ended before [notBefore] counts as ending at [notBefore].
     */
    fun preLaunchRecapFor(
        gameRaId: Long?,
        lastSession: PlaySessionEntity?,
        now: Instant,
        enabled: Boolean,
        notBefore: Instant? = null
    ): PreLaunchRecap? {
        if (!enabled || lastSession == null || gameRaId == null) return null
        val countedEnd = notBefore?.takeIf { it.isAfter(lastSession.endTime) } ?: lastSession.endTime
        val gap = Duration.between(countedEnd, now)
        if (gap < PRE_LAUNCH_MIN_GAP) return null
        return PreLaunchRecap(
            gameId = lastSession.gameId,
            raGameId = lastSession.raGameId ?: gameRaId,
            ownerUserId = lastSession.ownerUserId,
            unlockWindowStart = lastSession.startTime,
            unlockWindowEnd = lastSession.endTime,
            lastSession = lastSessionOf(lastSession, now),
            snapshotUnlocks = RaUnlockSnapshots.decode(lastSession.raSnapshotUnlocks)
        )
    }

    /**
     * What the launch shows, or null when there is nothing worth stopping for: no unlocks and no
     * rich-presence line.
     */
    fun launchRecapFor(lastSession: RecapLastSession?, gameTitle: String, unlocks: List<SessionUnlock>): LaunchRecap? {
        val hasPresence = !lastSession?.richPresence.isNullOrBlank()
        if (unlocks.isEmpty() && !hasPresence) return null
        return LaunchRecap(gameTitle = gameTitle, lastSession = lastSession, unlocks = unlocks)
    }

    fun lastSessionOf(session: PlaySessionEntity, now: Instant): RecapLastSession = RecapLastSession(
        start = session.startTime,
        end = session.endTime,
        activePlayMs = session.activePlayMs,
        richPresence = session.richPresence,
        daysSince = Duration.between(session.endTime, now).coerceAtLeast(Duration.ZERO).toDays()
    )

    /**
     * The month whose digest is due at [now], or null. The digest covers the month before the
     * current one in [zone], shows once per month, and skips a month with no sessions.
     */
    fun monthlyDigestDue(
        now: Instant,
        zone: ZoneId,
        lastShownMonth: YearMonth?,
        hadSessionsLastMonth: Boolean,
        enabled: Boolean
    ): YearMonth? {
        if (!enabled || !hadSessionsLastMonth) return null
        val digestMonth = digestMonthAt(now, zone)
        if (lastShownMonth != null && !lastShownMonth.isBefore(digestMonth)) return null
        return digestMonth
    }

    fun digestMonthAt(now: Instant, zone: ZoneId): YearMonth =
        YearMonth.from(now.atZone(zone)).minusMonths(1)

    fun monthWindow(month: YearMonth, zone: ZoneId): MonthWindow = MonthWindow(
        start = month.atDay(1).atStartOfDay(zone).toInstant(),
        end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()
    )
}
