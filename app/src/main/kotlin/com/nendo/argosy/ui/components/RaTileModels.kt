package com.nendo.argosy.ui.components

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.nendo.argosy.domain.model.RaLockedAchievement
import com.nendo.argosy.domain.model.RaTileContent
import com.nendo.argosy.domain.model.RaUnlock
import com.nendo.argosy.ui.screens.home.HomeGameUi

/**
 * The words the RetroAchievements tile draws, as the resource ids the surface composing it owns.
 * The phone and the companion keep separate keys for the same wording; the tile code does not.
 */
data class RaTileLabels(
    @StringRes val signedOut: Int,
    @StringRes val empty: Int,
    @StringRes val latestHeading: Int,
    @StringRes val nextHeading: Int,
    @StringRes val mastered: Int,
    @StringRes val locked: Int,
    @PluralsRes val points: Int,
    @PluralsRes val unlocks: Int,
    @StringRes val unlockMeta: Int,
    @StringRes val accountTally: Int,
    @StringRes val progress: Int,
    @StringRes val gameProgress: Int
)

/**
 * One badge in the tile's browse list. Unlocks come first and carry the time they were earned;
 * locked achievements follow with the art the site shows for them, dimmed by the badge itself.
 */
sealed interface RaBrowseEntry {
    val raId: Long
    val title: String
    val description: String?
    val points: Int
    val badgePath: String?
    val tier: AchievementBadgeTier

    data class Unlocked(val unlock: RaUnlock) : RaBrowseEntry {
        override val raId: Long get() = unlock.raId
        override val title: String get() = unlock.title
        override val description: String? get() = unlock.description
        override val points: Int get() = unlock.points
        override val badgePath: String? get() = unlock.badgePath
        override val tier: AchievementBadgeTier
            get() = if (unlock.hardcore) AchievementBadgeTier.HARDCORE else AchievementBadgeTier.SOFTCORE
    }

    data class Locked(val locked: RaLockedAchievement) : RaBrowseEntry {
        override val raId: Long get() = locked.raId
        override val title: String get() = locked.title
        override val description: String? get() = locked.description
        override val points: Int get() = locked.points
        override val badgePath: String? get() = locked.badgeLockPath
        override val tier: AchievementBadgeTier get() = AchievementBadgeTier.LOCKED
    }
}

/**
 * What the RetroAchievements tile draws. [content] is null while nobody is signed in.
 * [groundGame] is the game whose cover sits behind the tile: the tracked game, or the game of
 * the latest unlock, resolved the same way a game tile's is so cover and gradient match.
 * [newSince] is the moment before which an unlock no longer counts as new.
 */
data class RaTileUi(
    val content: RaTileContent?,
    val groundGame: HomeGameUi?,
    val labels: RaTileLabels,
    val entries: List<RaBrowseEntry>,
    val newSince: Long
)
