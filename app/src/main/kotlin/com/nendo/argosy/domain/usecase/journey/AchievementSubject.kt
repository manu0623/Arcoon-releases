package com.nendo.argosy.domain.usecase.journey

import java.util.Locale

/**
 * Reads the name an achievement description states outright, as in "Defeat Brock" or "Collect the
 * Silver Key". The phrase must follow an action verb, carry no determiner and hold a concrete noun.
 * An achievement whose wording names nothing returns null, which keeps the journal from crediting
 * an opponent the record never mentions.
 */
object AchievementSubject {

    private val VERBS = listOf(
        "defeat",
        "beat",
        "destroy",
        "kill",
        "slay",
        "obtain",
        "collect",
        "find",
        "reach",
        "complete"
    )

    private val DETERMINERS = setOf("the", "a", "an", "your", "his", "her", "their", "its")

    private val QUANTIFIERS = setOf(
        "first",
        "second",
        "third",
        "last",
        "final",
        "all",
        "every",
        "each",
        "any",
        "one",
        "two",
        "three",
        "some",
        "most",
        "another"
    )

    private val STOP_WORDS = setOf(
        "in",
        "at",
        "on",
        "with",
        "from",
        "before",
        "after",
        "without",
        "during",
        "for",
        "to",
        "of",
        "and",
        "while",
        "by",
        "using",
        "within"
    )

    private const val MAX_WORDS = 4

    fun of(description: String?): String? {
        val text = description?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val lower = text.lowercase(Locale.ROOT)
        val verb = VERBS.firstOrNull { lower.startsWith("$it ") } ?: return null
        val words = text.substring(verb.length + 1)
            .split(' ')
            .map { it.trim(',', '.', '!', ';', ':', '"', '\'') }
            .filter { it.isNotEmpty() }
        val withoutDeterminer = words.dropWhile { it.lowercase(Locale.ROOT) in DETERMINERS }
        if (withoutDeterminer.isEmpty()) return null
        if (withoutDeterminer.first().lowercase(Locale.ROOT) in QUANTIFIERS) return null
        val phrase = withoutDeterminer
            .takeWhile { it.lowercase(Locale.ROOT) !in STOP_WORDS }
            .take(MAX_WORDS)
        return phrase.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }
}
