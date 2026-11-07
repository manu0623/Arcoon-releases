package com.nendo.argosy.domain.usecase.journey

import com.nendo.argosy.domain.model.AchievementJourney
import com.nendo.argosy.domain.model.JourneyCategory
import com.nendo.argosy.domain.model.JourneyEntry
import com.nendo.argosy.domain.model.JourneyPhase
import com.nendo.argosy.domain.model.JourneyState

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

/**
 * The written journey of one game, built only from recorded unlocks. [opening] and [closing] are
 * the two overview facts; [highlights] are up to [JourneyStoryComposer.HIGHLIGHT_LIMIT] other
 * milestones, newest first; [leftOff] is the newest unlock that carried a captured game status;
 * [chapters] is the whole timeline.
 */
data class JourneyStory(
    val journey: AchievementJourney,
    val opening: JourneyEntry,
    val closing: JourneyClosing?,
    val highlights: List<JourneyEntry>,
    val leftOff: JourneyEntry?,
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

    fun compose(journey: AchievementJourney): JourneyStory? {
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
            leftOff = entries.lastOrNull { it.context != null },
            chapters = journey.phases
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
