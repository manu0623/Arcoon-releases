package com.nendo.argosy.data.cache

private val TITLE_NOISE = Regex("[^a-z0-9]+")
private val BRACKETED = Regex("\\([^)]*\\)|\\[[^\\]]*\\]")
private val WHITESPACE = Regex("\\s+")
private const val MIN_LEADING_LENGTH = 3
private const val MIN_FUZZY_LENGTH = 6
private const val MIN_LENGTH_RATIO = 0.6f
private const val SIMILARITY_THRESHOLD = 0.82f

fun normalizeTitle(raw: String): String =
    WHITESPACE.replace(TITLE_NOISE.replace(BRACKETED.replace(raw.lowercase(), " "), " "), " ").trim()

/**
 * A search hit is applied without asking when it names the game: an exact match after cleaning,
 * the same leading segment, one title inside the other, or a high bigram similarity.
 * [alternatives] are the other names RomM already holds for the game.
 */
fun isConfidentTitleMatch(gameTitle: String, alternatives: List<String>, matchedTitle: String): Boolean =
    (listOf(gameTitle) + alternatives).any { title -> isConfidentTitleMatch(title, matchedTitle) }

fun isConfidentTitleMatch(gameTitle: String, matchedTitle: String): Boolean {
    val game = normalizeTitle(gameTitle)
    val matched = normalizeTitle(matchedTitle)
    if (game.isEmpty() || matched.isEmpty()) return false
    if (game == matched) return true
    val gameLead = leadingSegment(gameTitle)
    if (gameLead.length >= MIN_LEADING_LENGTH && gameLead == matched) return true
    val matchedLead = leadingSegment(matchedTitle)
    if (matchedLead.length >= MIN_LEADING_LENGTH && matchedLead == game) return true
    return isSimilarEnough(game, matched)
}

fun parseAlternativeNames(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    val cleaned = raw.trim().removePrefix("[").removeSuffix("]")
    val parts = if (cleaned.contains('"')) {
        cleaned.split(Regex("\",\\s*\""))
    } else {
        cleaned.split('|', ',', ';')
    }
    return parts.map { it.trim().trim('"', '\'', '[', ']') }.filter { it.isNotBlank() }
}

private fun isSimilarEnough(game: String, matched: String): Boolean {
    if (minOf(game.length, matched.length) < MIN_FUZZY_LENGTH) return false
    val ratio = minOf(game.length, matched.length).toFloat() / maxOf(game.length, matched.length)
    if (ratio < MIN_LENGTH_RATIO) return false
    if (game.contains(matched) || matched.contains(game)) return true
    return bigramSimilarity(game, matched) >= SIMILARITY_THRESHOLD
}

private fun bigramSimilarity(first: String, second: String): Float {
    val grams = HashMap<String, Int>()
    for (index in 0 until first.length - 1) {
        val gram = first.substring(index, index + 2)
        grams[gram] = (grams[gram] ?: 0) + 1
    }
    var shared = 0
    for (index in 0 until second.length - 1) {
        val gram = second.substring(index, index + 2)
        val count = grams[gram] ?: 0
        if (count > 0) {
            shared++
            grams[gram] = count - 1
        }
    }
    val total = (first.length - 1) + (second.length - 1)
    return if (total == 0) 0f else (2f * shared) / total
}

private fun leadingSegment(raw: String): String {
    val marks = listOf(" - ", ": ", " (")
    val cut = marks.map { raw.indexOf(it) }.filter { it > 0 }.minOrNull()
    val head = if (cut != null) raw.substring(0, cut) else raw
    return normalizeTitle(head)
}
