package com.nendo.argosy.domain.usecase.journey

import com.nendo.argosy.domain.model.AchievementJourney
import com.nendo.argosy.domain.model.JourneyCategory
import com.nendo.argosy.domain.model.JourneyEntry
import com.nendo.argosy.domain.model.JourneyPhase
import com.nendo.argosy.domain.model.JourneyState
import java.time.Instant

/**
 * How the recorded journey stands at its newest unlock: every achievement earned, the main
 * objectives marked finished by a completion achievement, or simply its most significant recent
 * milestone.
 */
sealed interface JourneyClosing {
    val entry: JourneyEntry

    data class Mastered(override val entry: JourneyEntry) : JourneyClosing
    data class MainComplete(override val entry: JourneyEntry) : JourneyClosing
    data class Latest(override val entry: JourneyEntry) : JourneyClosing
}

/** One recorded play session of the game, as the play history stored it. */
data class JourneySessionRecord(
    val start: Instant,
    val end: Instant,
    val activePlayMs: Long,
    val status: String?,
    val statusAt: Instant?
)

/**
 * A play session told back: how long it lasted, the last status the game reported during it and
 * the achievements unlocked inside it, oldest first.
 */
data class JourneySession(
    val start: Instant,
    val playedMs: Long,
    val status: String?,
    val unlocked: List<JourneyEntry>
)

/**
 * The newest status line the record holds: from a play session, or from the moment of an unlock,
 * in which case [unlockTitle] names that achievement.
 */
data class JourneyStatus(
    val text: String,
    val at: Instant,
    val unlockTitle: String?
)

/**
 * The written journey of one game, built only from recorded unlocks and play sessions. [opening]
 * and [closing] are the two overview facts; [highlights] are up to
 * [JourneyStoryComposer.HIGHLIGHT_LIMIT] other milestones, newest first; [leftOff] is the newest
 * recorded game status; [sessions] is the play history, newest first; [chapters] is the timeline.
 */
data class JourneyStory(
    val journey: AchievementJourney,
    val opening: JourneyEntry,
    val closing: JourneyClosing?,
    val highlights: List<JourneyEntry>,
    val leftOff: JourneyStatus?,
    val sessions: List<JourneySession>,
    val chapters: List<JourneyPhase>
)

/**
 * Picks the facts a journey is told with. Deterministic: the same unlocks always give the same
 * story. Progression and completion unlocks outrank the rest, newer outranks older within a rank,
 * and the achievement id settles a tie without implying any order of play. Nothing is stated
 * twice: an unlock named in the overview is not repeated as a highlight.
 */
object JourneyStoryComposer {

    const val HIGHLIGHT_LIMIT = 3

    /** An unlock reported shortly after a session closed still belongs to it. */
    private const val SESSION_GRACE_MS = 120_000L

    fun compose(journey: AchievementJourney, records: List<JourneySessionRecord> = emptyList()): JourneyStory? {
        val entries = journey.entries
        val opening = entries.firstOrNull() ?: return null
        val later = entries.drop(1)
        val closing = closingOf(journey.progress.state, later)
        val stated = setOfNotNull(opening.raId, closing?.entry?.raId)
        val highlights = later
            .filter { it.raId !in stated }
            .sortedWith(byRank())
            .take(HIGHLIGHT_LIMIT)
            .sortedWith(compareByDescending<JourneyEntry> { it.unlockedAt }.thenBy { it.raId })
        return JourneyStory(
            journey = journey,
            opening = opening,
            closing = closing,
            highlights = highlights,
            leftOff = leftOff(entries, records),
            sessions = sessions(entries, records),
            chapters = journey.phases
        )
    }

    private fun leftOff(entries: List<JourneyEntry>, records: List<JourneySessionRecord>): JourneyStatus? {
        val fromUnlock = entries.lastOrNull { it.context != null }
            ?.let { JourneyStatus(it.context.orEmpty(), it.unlockedAt, it.title) }
        val fromSession = records
            .mapNotNull { record ->
                val text = record.status?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                JourneyStatus(text, record.statusAt ?: record.end, unlockTitle = null)
            }
            .maxByOrNull { it.at }
        return listOfNotNull(fromUnlock, fromSession).maxByOrNull { it.at }
    }

    private fun sessions(entries: List<JourneyEntry>, records: List<JourneySessionRecord>): List<JourneySession> =
        records
            .sortedByDescending { it.start }
            .map { record ->
                val until = record.end.plusMillis(SESSION_GRACE_MS)
                JourneySession(
                    start = record.start,
                    playedMs = record.activePlayMs.takeIf { it > 0 }
                        ?: (record.end.toEpochMilli() - record.start.toEpochMilli()).coerceAtLeast(0),
                    status = record.status?.takeIf { it.isNotBlank() },
                    unlocked = entries.filter { !it.unlockedAt.isBefore(record.start) && !it.unlockedAt.isAfter(until) }
                )
            }

    private fun closingOf(state: JourneyState, later: List<JourneyEntry>): JourneyClosing? {
        val newest = later.lastOrNull() ?: return null
        return when (state) {
            JourneyState.MASTERED -> JourneyClosing.Mastered(newest)
            JourneyState.MAIN_COMPLETE -> later.lastOrNull { it.category == JourneyCategory.COMPLETION }
                ?.let { JourneyClosing.MainComplete(it) }
                ?: JourneyClosing.Latest(later.sortedWith(byRank()).first())
            else -> JourneyClosing.Latest(later.sortedWith(byRank()).first())
        }
    }

    private fun byRank(): Comparator<JourneyEntry> =
        compareBy<JourneyEntry> { rank(it) }
            .thenByDescending { it.unlockedAt }
            .thenBy { it.raId }

    private fun rank(entry: JourneyEntry): Int = when (entry.category) {
        JourneyCategory.COMPLETION, JourneyCategory.PROGRESSION -> 0
        else -> 1
    }
}
