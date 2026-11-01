package com.nendo.argosy.domain.usecase.recap

import com.nendo.argosy.data.local.dao.PlayDayTotal
import com.nendo.argosy.data.model.PlayDay
import com.nendo.argosy.data.remote.ra.RAWebEarnedAchievement
import com.nendo.argosy.data.repository.RA_BADGE_BASE_URL
import com.nendo.argosy.domain.model.DigestAward
import com.nendo.argosy.domain.model.DigestGame
import com.nendo.argosy.domain.model.MonthWindow
import com.nendo.argosy.domain.model.MonthlyDigest
import com.nendo.argosy.domain.model.MonthlyRecap
import com.nendo.argosy.domain.model.SessionUnlock
import com.nendo.argosy.util.parseTimestamp
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

object MonthlyDigestAssembler {

    private const val BADGE_EXTENSION = ".png"

    /**
     * The web unlocks dated inside [window], one per achievement: a hardcore unlock wins over a
     * softcore one, and the earliest of the kept mode dates it. Entries without a badge or a
     * readable date are left out. Oldest first.
     */
    fun webUnlocks(earned: List<RAWebEarnedAchievement>, window: MonthWindow): List<SessionUnlock> =
        earned.mapNotNull { entry ->
            val badge = entry.badgeName?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val at = entry.date?.let { parseTimestamp(it) }?.let { Instant.ofEpochMilli(it) } ?: return@mapNotNull null
            if (at !in window) return@mapNotNull null
            SessionUnlock(
                badgeId = badge,
                title = entry.title,
                points = entry.points,
                badgePath = "$RA_BADGE_BASE_URL$badge$BADGE_EXTENSION",
                isHardcore = entry.isHardcore,
                unlockedAt = at,
                description = entry.description,
                type = entry.type,
                gameTitle = entry.gameTitle?.takeIf { it.isNotBlank() }
            ) to (entry.achievementId?.toString() ?: badge)
        }
            .groupBy({ it.second }, { it.first })
            .values
            .map { rows -> rows.filter { it.isHardcore }.minByOrNull { it.unlockedAt } ?: rows.minBy { it.unlockedAt } }
            .sortedBy { it.unlockedAt }

    /**
     * [unlocks] with one entry per badge id, hardcore first, oldest first.
     */
    fun onePerBadge(unlocks: List<SessionUnlock>): List<SessionUnlock> =
        unlocks.sortedWith(compareByDescending<SessionUnlock> { it.isHardcore }.thenBy { it.unlockedAt })
            .distinctBy { it.badgeId }
            .sortedBy { it.unlockedAt }

    fun webGameTitles(earned: List<RAWebEarnedAchievement>): Map<Long, String> =
        earned.mapNotNull { entry ->
            val gameId = entry.gameId ?: return@mapNotNull null
            val title = entry.gameTitle?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            gameId to title
        }.toMap()

    /**
     * Every date of [month] in order with its total from [totals], zero where none is given.
     * Totals dated outside the month or unreadable are left out.
     */
    fun monthDays(month: YearMonth, totals: List<PlayDayTotal>): List<PlayDay> {
        val byDate = totals.mapNotNull { total ->
            runCatching { LocalDate.parse(total.day) }.getOrNull()?.let { it to total.activeMs }
        }.toMap()
        return (1..month.lengthOfMonth()).map { dayOfMonth ->
            val date = month.atDay(dayOfMonth)
            PlayDay(date = date, activeMs = byDate[date] ?: 0L)
        }
    }

    /**
     * The card's content from [recap]. Awards whose game has no title in [gameTitles] are left out.
     */
    fun assemble(
        recap: MonthlyRecap,
        days: List<PlayDay>,
        unlocks: List<SessionUnlock>,
        covers: Map<Long, String?>,
        gameTitles: Map<Long, String>
    ): MonthlyDigest {
        val newFavorites = recap.newFavorites.map { it.gameId }.toSet()
        return MonthlyDigest(
            month = recap.month,
            totalActiveMs = recap.totalActiveMs,
            sessionCount = recap.sessionCount,
            longestSessionMs = recap.longestSessionMs,
            days = days,
            topGames = recap.topGames.map { total ->
                DigestGame(
                    gameId = total.gameId,
                    gameTitle = total.gameTitle,
                    coverPath = covers[total.gameId],
                    activeMs = total.activeMs,
                    isNewFavorite = total.gameId in newFavorites
                )
            },
            unlocks = unlocks,
            awards = recap.awards.mapNotNull { award ->
                val title = gameTitles[award.raGameId] ?: return@mapNotNull null
                DigestAward(award.raGameId, title, award.kind, award.awardedAt)
            }
        )
    }
}
