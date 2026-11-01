package com.nendo.argosy.domain.usecase.recap

import com.nendo.argosy.data.local.entity.PlaySessionEntity
import com.nendo.argosy.data.remote.romm.RomMRAGameProgression
import com.nendo.argosy.data.repository.RaGameAward
import com.nendo.argosy.data.repository.RaMetadataSource.Companion.toGameAward
import com.nendo.argosy.domain.model.MonthlyRecap
import com.nendo.argosy.domain.model.RecapAward
import com.nendo.argosy.domain.model.RecapAwardKind
import com.nendo.argosy.domain.model.RecapFavorite
import com.nendo.argosy.domain.model.RecapGameTotal
import com.nendo.argosy.domain.model.RecapMonthUnlock
import com.nendo.argosy.util.parseTimestamp
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

object MonthlyRecapCalculator {

    const val FAVORITES_TOP = 3
    const val DEFAULT_TOP_GAMES = 5

    /**
     * Sessions belong to the month their start falls in, in [zone]. [sessions] must cover [month]
     * and the month before it; [firstPlayed] is each game's earliest session across all history.
     * Unlocks come from [progression], awards from [awards]; an undated award is left out.
     */
    fun compute(
        month: YearMonth,
        zone: ZoneId,
        sessions: List<PlaySessionEntity>,
        firstPlayed: Map<Long, Instant>,
        progression: List<RomMRAGameProgression>,
        topGamesLimit: Int = DEFAULT_TOP_GAMES,
        awards: List<RaGameAward> = progression.mapNotNull { it.toGameAward() }
    ): MonthlyRecap {
        val window = RecapEligibility.monthWindow(month, zone)
        val monthStart = window.start
        val monthEnd = window.end
        val inMonth = sessions.inRange(monthStart, monthEnd)
        val previousStart = RecapEligibility.monthWindow(month.minusMonths(1), zone).start
        val previousTop = gameTotals(sessions.inRange(previousStart, monthStart))
            .take(FAVORITES_TOP).map { it.gameId }.toSet()
        val totals = gameTotals(inMonth)

        return MonthlyRecap(
            month = month,
            totalActiveMs = inMonth.sumOf { it.activePlayMs },
            sessionCount = inMonth.size,
            longestSessionMs = inMonth.maxOfOrNull { it.activePlayMs } ?: 0L,
            topGames = totals.take(topGamesLimit),
            newFavorites = totals.take(FAVORITES_TOP)
                .filter { it.gameId !in previousTop }
                .map { total ->
                    RecapFavorite(
                        gameId = total.gameId,
                        gameTitle = total.gameTitle,
                        activeMs = total.activeMs,
                        isFirstPlay = firstPlayed[total.gameId]?.let { !it.isBefore(monthStart) } ?: true
                    )
                },
            unlocks = monthUnlocks(progression, monthStart, monthEnd),
            awards = monthAwards(awards, monthStart, monthEnd)
        )
    }

    private fun List<PlaySessionEntity>.inRange(start: Instant, end: Instant): List<PlaySessionEntity> =
        filter { !it.startTime.isBefore(start) && it.startTime.isBefore(end) }

    private fun gameTotals(sessions: List<PlaySessionEntity>): List<RecapGameTotal> =
        sessions.groupBy { it.gameId }
            .map { (gameId, rows) ->
                RecapGameTotal(
                    gameId = gameId,
                    gameTitle = rows.maxBy { it.startTime }.gameTitle,
                    activeMs = rows.sumOf { it.activePlayMs },
                    sessionCount = rows.size
                )
            }
            .sortedWith(compareByDescending<RecapGameTotal> { it.activeMs }.thenBy { it.gameId })

    private fun monthUnlocks(
        progression: List<RomMRAGameProgression>,
        monthStart: Instant,
        monthEnd: Instant
    ): List<RecapMonthUnlock> {
        val lastInstant = monthEnd.minusMillis(1)
        return progression.flatMap { game ->
            val raGameId = game.romRaId ?: return@flatMap emptyList()
            UnlockWindow.unlocksWithin(game.earnedAchievements, monthStart, lastInstant)
                .map { RecapMonthUnlock(raGameId, it) }
        }.sortedBy { it.unlock.unlockedAt }
    }

    private fun monthAwards(
        awards: List<RaGameAward>,
        monthStart: Instant,
        monthEnd: Instant
    ): List<RecapAward> =
        awards.mapNotNull { award ->
            val kind = RecapAwardKind.fromToken(award.kind) ?: return@mapNotNull null
            val awardedAt = award.awardedAt?.let { parseTimestamp(it) }?.let { Instant.ofEpochMilli(it) }
                ?: return@mapNotNull null
            if (awardedAt.isBefore(monthStart) || !awardedAt.isBefore(monthEnd)) return@mapNotNull null
            RecapAward(award.raGameId, kind, awardedAt)
        }.sortedBy { it.awardedAt }
}
