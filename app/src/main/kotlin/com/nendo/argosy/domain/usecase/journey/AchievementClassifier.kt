package com.nendo.argosy.domain.usecase.journey

import com.nendo.argosy.domain.model.JourneyCategory
import java.util.Locale

/**
 * Places one achievement into a [JourneyCategory] from its RetroAchievements type token and its own
 * wording. A type token decides first, because it is the achievement author's own statement. Text
 * cues decide only when the token says nothing, and an achievement no cue matches stays
 * [JourneyCategory.UNCLASSIFIED] rather than being forced into a story.
 */
object AchievementClassifier {

    private const val WIN_CONDITION_TOKEN = "win_condition"
    private const val PROGRESSION_TOKEN = "progression"

    private val COMPLETION_CUES = listOf(
        "beat the game",
        "complete the game",
        "finish the game",
        "see the ending",
        "reach the ending",
        "get the ending",
        "view the credits",
        "roll the credits",
        "true ending",
        "final ending",
        "all achievements",
        "every achievement",
        "100 percent"
    )

    private val MASTERY_CUES = listOf(
        "without taking damage",
        "without damage",
        "without dying",
        "without losing",
        "no damage",
        "no continues",
        "one life",
        "flawless",
        "perfect",
        "perfectly",
        "speedrun",
        "speed run",
        "highest difficulty",
        "hard mode",
        "expert"
    )

    private val COMBAT_CUES = listOf(
        "defeat",
        "defeated",
        "boss",
        "kill",
        "slay",
        "slain",
        "battle",
        "battles",
        "fight",
        "enemy",
        "enemies",
        "duel"
    )

    private val COLLECTION_CUES = listOf(
        "collect",
        "collected",
        "gather",
        "obtain",
        "acquired",
        "purchase",
        "buy",
        "every item",
        "all items",
        "inventory",
        "treasure"
    )

    private val EXPLORATION_CUES = listOf(
        "explore",
        "explored",
        "discover",
        "discovered",
        "find",
        "found",
        "hidden",
        "secret",
        "area",
        "room",
        "cave",
        "shortcut",
        "map"
    )

    private val PROGRESSION_CUES = listOf(
        "complete",
        "completed",
        "finish",
        "finished",
        "stage",
        "level",
        "chapter",
        "reach",
        "arrive",
        "enter",
        "clear",
        "first",
        "begin"
    )

    fun classify(type: String?, title: String, description: String?): JourneyCategory {
        if (type == WIN_CONDITION_TOKEN) return JourneyCategory.COMPLETION
        if (type == PROGRESSION_TOKEN) return JourneyCategory.PROGRESSION
        val text = "$title ${description.orEmpty()}".lowercase(Locale.ROOT)
        return when {
            matches(text, COMPLETION_CUES) -> JourneyCategory.COMPLETION
            matches(text, MASTERY_CUES) -> JourneyCategory.MASTERY
            matches(text, COMBAT_CUES) -> JourneyCategory.COMBAT
            matches(text, COLLECTION_CUES) -> JourneyCategory.COLLECTION
            matches(text, EXPLORATION_CUES) -> JourneyCategory.EXPLORATION
            matches(text, PROGRESSION_CUES) -> JourneyCategory.PROGRESSION
            else -> JourneyCategory.UNCLASSIFIED
        }
    }

    private fun matches(text: String, cues: List<String>): Boolean = cues.any { cue -> containsCue(text, cue) }

    /**
     * True when [cue] appears in [text] as a whole word, or as a phrase when it holds a space, so
     * "finish" does not fire on "finishing move" and "area" does not fire on "arena".
     */
    private fun containsCue(text: String, cue: String): Boolean {
        if (' ' in cue) return text.contains(cue)
        var start = text.indexOf(cue)
        while (start >= 0) {
            val beforeBoundary = start == 0 || !text[start - 1].isLetterOrDigit()
            val end = start + cue.length
            val afterBoundary = end == text.length || !text[end].isLetterOrDigit()
            if (beforeBoundary && afterBoundary) return true
            start = text.indexOf(cue, start + 1)
        }
        return false
    }
}
