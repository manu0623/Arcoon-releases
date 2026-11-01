package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.PlayerCount
import com.nendo.argosy.domain.model.PlayerCountBucket

/**
 * Glyph for a player-count range such as "1-8": one head for a solo game, two for a pair, a
 * group for three or more or an open-ended range.
 */
fun playerCountGlyph(players: String): ImageVector {
    val count = PlayerCount.parse(players)
    return when {
        count == null || count.unbounded || count.max > 2 -> Icons.Default.Groups
        count.max == 2 -> Icons.Default.People
        else -> Icons.Default.Person
    }
}

/**
 * The label on the library's Players tab. `PlayerCountBucket` lives in `domain/` and must not
 * import `R`, so the label is attached here, following the shape of [CompletionStatusUi].
 */
@get:StringRes
val PlayerCountBucket.labelRes: Int
    get() = when (this) {
        PlayerCountBucket.ONE -> R.string.library_filter_players_one
        PlayerCountBucket.TWO -> R.string.library_filter_players_two
        PlayerCountBucket.THREE -> R.string.library_filter_players_three
        PlayerCountBucket.FOUR_PLUS -> R.string.library_filter_players_four_plus
    }

/**
 * The same label on the companion's filter panel, which owns its own keys.
 */
@get:StringRes
val PlayerCountBucket.dualLabelRes: Int
    get() = when (this) {
        PlayerCountBucket.ONE -> R.string.dual_filter_players_one
        PlayerCountBucket.TWO -> R.string.dual_filter_players_two
        PlayerCountBucket.THREE -> R.string.dual_filter_players_three
        PlayerCountBucket.FOUR_PLUS -> R.string.dual_filter_players_four_plus
    }
