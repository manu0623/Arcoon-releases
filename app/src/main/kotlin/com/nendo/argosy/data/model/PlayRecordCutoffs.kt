package com.nendo.argosy.data.model

import java.time.Instant

/**
 * The instant before which one account deleted each game's play history. A session [blocks]
 * when it started before its game's cutoff; games with no cutoff block nothing.
 */
data class PlayRecordCutoffs(
    val deletedBeforeByGameId: Map<Long, Instant>
) {
    fun blocks(gameId: Long, startTime: Instant): Boolean =
        deletedBeforeByGameId[gameId]?.let { startTime.isBefore(it) } == true

    companion object {
        val NONE = PlayRecordCutoffs(emptyMap())
    }
}
