package com.nendo.argosy.domain.model

import java.time.Instant

/**
 * What an unlocked achievement records about the player's playthrough. [UNCLASSIFIED] is the honest
 * answer for an achievement the text cues do not place, and such an entry keeps its own words.
 */
enum class JourneyCategory {
    PROGRESSION,
    EXPLORATION,
    COMBAT,
    COLLECTION,
    MASTERY,
    COMPLETION,
    UNCLASSIFIED;

    companion object {
        const val KEY_SEPARATOR = "|"

        fun fromToken(token: String?): JourneyCategory? = entries.firstOrNull { it.name == token }
    }
}

/**
 * One accomplishment confirmed by an unlock. [unlockedAt] is the first moment either mode recorded
 * it, and [isHardcore] says whether a hardcore unlock exists for it. [context] is the rich presence
 * line captured at the moment of the unlock, null for an unlock Argosy did not witness. [subject]
 * is the name an achievement description states outright, null when it names nothing.
 */
data class JourneyEntry(
    val raId: Long,
    val title: String,
    val description: String?,
    val points: Int,
    val category: JourneyCategory,
    val isHardcore: Boolean,
    val unlockedAt: Instant,
    val context: String? = null,
    val subject: String? = null
)

/**
 * A stretch of the journey spent on one kind of accomplishment, oldest entry first. [category] is
 * the dominant category of the stretch, never a claim about single entries inside it.
 */
data class JourneyPhase(
    val category: JourneyCategory,
    val entries: List<JourneyEntry>
) {
    val points: Int get() = entries.sumOf { it.points }
    val firstUnlock: Instant get() = entries.first().unlockedAt
    val lastUnlock: Instant get() = entries.last().unlockedAt
}

/**
 * How far the achievement record reaches. [MASTERED] means every achievement the game defines is
 * unlocked; [MAIN_COMPLETE] means at least one completion achievement is; [EARLY] means the record
 * holds [EARLY_LIMIT] unlocks or fewer.
 */
enum class JourneyState {
    EARLY,
    IN_PROGRESS,
    MAIN_COMPLETE,
    MASTERED;

    companion object {
        const val EARLY_LIMIT = 3
    }
}

/**
 * Achievement counts for the game: [unlocked] and [unlockedPoints] count only earned rows, while
 * [total] and [totalPoints] count every row the game defines.
 */
data class JourneyProgress(
    val unlocked: Int,
    val total: Int,
    val unlockedPoints: Int,
    val totalPoints: Int,
    val state: JourneyState
) {
    val percent: Int get() = if (total > 0) unlocked * 100 / total else 0
}

/**
 * The reconstructed journey of one game. [entries] and [phases] are chronological, [milestones] is
 * the short list worth naming, and [evidenceKey] identifies the unlock set this was built from.
 */
data class AchievementJourney(
    val gameId: Long,
    val raGameId: Long?,
    val gameTitle: String,
    val platformName: String?,
    val entries: List<JourneyEntry>,
    val phases: List<JourneyPhase>,
    val milestones: List<JourneyEntry>,
    val progress: JourneyProgress,
    val generatedAt: Instant,
    val evidenceKey: String
) {
    val isEmpty: Boolean get() = entries.isEmpty()

    /**
     * Names the achievement descriptions state outright, oldest first and without repeats. It is
     * the only identification the journey draws from written evidence, never an inference.
     */
    val namedSubjects: List<String> get() = entries.mapNotNull { it.subject }.distinct()

    val capturedContexts: List<JourneyEntry> get() = entries.filter { it.context != null }

    /**
     * The anchor rows the journey view scrolls through: the opening, one row per phase, the
     * milestone row and the closing summary.
     */
    val rowCount: Int get() = phases.size + EXTRA_ROWS

    private companion object {
        const val EXTRA_ROWS = 3
    }
}
