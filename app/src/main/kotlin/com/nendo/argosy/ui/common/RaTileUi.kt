package com.nendo.argosy.ui.common

import com.nendo.argosy.domain.model.RaTileContent
import com.nendo.argosy.ui.components.RaBrowseEntry
import com.nendo.argosy.ui.components.RaTileStatus

/**
 * The badges the tile can step through, unlocked first. The account tile browses its recent
 * unlocks; a tracked game browses its latest unlock and then what is still locked.
 */
val RaTileContent?.browseEntries: List<RaBrowseEntry>
    get() = when (this) {
        null -> emptyList()
        is RaTileContent.Account -> recentUnlocks.map { RaBrowseEntry.Unlocked(it) }
        is RaTileContent.TrackedGame ->
            listOfNotNull(latestUnlock).map { RaBrowseEntry.Unlocked(it) } +
                nextLocked.map { RaBrowseEntry.Locked(it) }
    }

/**
 * The game whose cover stands behind the tile, resolved into the same game map a game tile reads
 * so the cover and its gradient are the ones every other tile would draw.
 */
val RaTileContent.groundGameId: Long?
    get() = when (this) {
        is RaTileContent.Account -> latestUnlock?.gameId
        is RaTileContent.TrackedGame -> gameId
    }

/**
 * The game a press on the [index]th browse entry opens: that unlock's game on the account tile,
 * the tracked game itself otherwise.
 */
fun RaTileContent.browseGameId(index: Int): Long? = when (this) {
    is RaTileContent.Account -> recentUnlocks.getOrNull(index)?.gameId
    is RaTileContent.TrackedGame -> gameId
}

fun RaTileContent?.toGridStatus(): RaTileStatus = RaTileStatus(
    signedIn = this != null,
    tracksGame = this is RaTileContent.TrackedGame,
    browseCount = browseEntries.size
)
