package com.nendo.argosy.domain.model

/**
 * The most players a game's free-text player count admits. RomM stores the count as it was
 * typed ("1", "1-2", "2-4", "4+", sometimes words), so [max] is the largest number in the text
 * and [unbounded] is a plus after it, which admits any number of players from [max] up.
 */
data class PlayerCount(
    val max: Int,
    val unbounded: Boolean
) {
    fun reaches(minPlayers: Int): Boolean = unbounded || max >= minPlayers

    companion object {
        private val NUMBER = Regex("\\d+")

        /**
         * Null when the text carries no number at all, including null and blank text.
         */
        fun parse(text: String?): PlayerCount? {
            if (text.isNullOrBlank()) return null
            val numbers = NUMBER.findAll(text).toList()
            val max = numbers.mapNotNull { it.value.toIntOrNull() }.maxOrNull() ?: return null
            val lastDigit = numbers.last().range.last
            return PlayerCount(max = max, unbounded = text.lastIndexOf('+') > lastDigit)
        }
    }
}
