package com.nendo.argosy.domain.model

/**
 * One choice on the Players filter: games that support at least [minPlayers]. The name is the
 * stored and compared value; the label lives in `ui/common/PlayerCountUi.kt`, because this layer
 * must not import `R`.
 */
enum class PlayerCountBucket(val minPlayers: Int) {
    ONE(1),
    TWO(2),
    THREE(3),
    FOUR_PLUS(4);

    /**
     * A game with no parsable count is never admitted while a bucket is chosen.
     */
    fun admits(count: PlayerCount?): Boolean = count != null && count.reaches(minPlayers)

    companion object {
        fun fromName(name: String?): PlayerCountBucket? =
            if (name == null) null else entries.find { it.name == name }
    }
}
