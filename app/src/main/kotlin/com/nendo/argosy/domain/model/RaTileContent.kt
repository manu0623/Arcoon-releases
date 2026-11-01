package com.nendo.argosy.domain.model

/**
 * How many of the newest unlocks decide the account's featured mode: hardcore anywhere in this
 * window features hardcore, a window with none features softcore.
 */
const val RA_FEATURED_MODE_WINDOW = 10

const val RA_RECENT_UNLOCK_CAP = 8

const val RA_NEXT_LOCKED_CAP = 4

enum class RaFeaturedMode {
    HARDCORE,
    SOFTCORE
}

/**
 * One earned achievement as the tile draws it. [unlockedAt] is the hardcore time when there is
 * one, else the softcore time; [badgePath] is the cached file when one exists, else the remote url.
 */
data class RaUnlock(
    val raId: Long,
    val title: String,
    val description: String?,
    val points: Int,
    val badgePath: String?,
    val unlockedAt: Long,
    val hardcore: Boolean,
    val gameId: Long,
    val gameTitle: String,
    val gameCoverPath: String?
)

data class RaLockedAchievement(
    val raId: Long,
    val title: String,
    val description: String?,
    val points: Int,
    val badgeLockPath: String?
)

/**
 * What the RetroAchievements home tile shows, built from Argosy's own achievement rows rather than
 * the site's totals, which no client endpoint reports.
 *
 * [points] and [unlocks] are tallied in [featuredMode]: hardcore counts only hardcore unlocks,
 * softcore counts every unlock, since a hardcore unlock is also earned in softcore. For the
 * account they span the library; for a tracked game they are that game's.
 */
sealed interface RaTileContent {
    val username: String
    val featuredMode: RaFeaturedMode
    val points: Int
    val unlocks: Int
    val latestUnlock: RaUnlock?

    /**
     * [recentUnlocks] is newest first and holds at most [RA_RECENT_UNLOCK_CAP]. [latestGameEarned]
     * and [latestGameTotal] describe the game of [latestUnlock] and are zero when there is none.
     */
    data class Account(
        override val username: String,
        override val featuredMode: RaFeaturedMode,
        override val points: Int,
        override val unlocks: Int,
        override val latestUnlock: RaUnlock?,
        val recentUnlocks: List<RaUnlock>,
        val latestGameEarned: Int,
        val latestGameTotal: Int
    ) : RaTileContent

    /**
     * [nextLocked] is the cheapest achievements still locked in [featuredMode], at most
     * [RA_NEXT_LOCKED_CAP]. [mastered] is every achievement earned in that mode.
     */
    data class TrackedGame(
        override val username: String,
        override val featuredMode: RaFeaturedMode,
        override val points: Int,
        override val unlocks: Int,
        override val latestUnlock: RaUnlock?,
        val gameId: Long,
        val gameTitle: String,
        val gameCoverPath: String?,
        val total: Int,
        val nextLocked: List<RaLockedAchievement>,
        val mastered: Boolean
    ) : RaTileContent
}

/**
 * The mode the tile features for [unlocks]: hardcore when any of the [window] newest was earned in
 * hardcore, softcore only when that window holds none. Order of the input does not matter; a
 * tracked game passes its whole unlock list as the window.
 */
fun featuredModeFor(unlocks: List<RaUnlock>, window: Int = RA_FEATURED_MODE_WINDOW): RaFeaturedMode {
    val recent = unlocks.sortedByDescending { it.unlockedAt }.take(window)
    return if (recent.any { it.hardcore }) RaFeaturedMode.HARDCORE else RaFeaturedMode.SOFTCORE
}
