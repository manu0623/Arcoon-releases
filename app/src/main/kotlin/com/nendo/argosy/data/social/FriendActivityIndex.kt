package com.nendo.argosy.data.social

import java.time.Instant
import java.util.concurrent.TimeUnit

internal val RECENT_FRIEND_PLAY_WINDOW_MILLIS: Long = TimeUnit.DAYS.toMillis(14)

internal fun liveFriendActivity(friends: List<Friend>): Map<Int, List<FriendActivity>> =
    friends.asSequence()
        .filter { it.isAccepted && it.presence == PresenceStatus.IN_GAME }
        .mapNotNull { friend ->
            friend.currentGame?.igdbId?.let { igdbId -> igdbId to friend.toActivity(playingNow = true) }
        }
        .groupBy(keySelector = { it.first }, valueTransform = { it.second })

/**
 * Games each accepted friend in [friends] played inside the recent-play window ending at
 * [nowMillis], from the rows [playsByFriendId] holds for them.
 */
internal fun recentFriendActivity(
    friends: List<Friend>,
    playsByFriendId: Map<String, List<ActiveGameRow>>,
    nowMillis: Long
): Map<Int, List<FriendActivity>> {
    val since = nowMillis - RECENT_FRIEND_PLAY_WINDOW_MILLIS
    return friends.asSequence()
        .filter { it.isAccepted }
        .flatMap { friend ->
            playsByFriendId[friend.id].orEmpty().asSequence().mapNotNull { row ->
                val playedAt = row.lastPlayed?.let { parseInstantMillis(it) } ?: return@mapNotNull null
                if (playedAt < since || playedAt > nowMillis) return@mapNotNull null
                row.igdbId to friend.toActivity(playingNow = false, lastPlayedAtMillis = playedAt)
            }
        }
        .groupBy(keySelector = { it.first }, valueTransform = { it.second })
}

/**
 * One list per game: friends playing now first, then friends who played recently, most recent
 * first. A friend in both keeps only the playing-now entry.
 */
internal fun mergeFriendActivity(
    live: Map<Int, List<FriendActivity>>,
    recent: Map<Int, List<FriendActivity>>
): Map<Int, List<FriendActivity>> = (live.keys + recent.keys).associateWith { igdbId ->
    val playing = live[igdbId].orEmpty()
    val playingIds = playing.mapTo(HashSet()) { it.friendId }
    val played = recent[igdbId].orEmpty()
        .filter { it.friendId !in playingIds }
        .sortedByDescending { it.lastPlayedAtMillis }
    playing + played
}

private fun parseInstantMillis(value: String): Long? =
    runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()

private fun Friend.toActivity(playingNow: Boolean, lastPlayedAtMillis: Long? = null): FriendActivity =
    FriendActivity(
        friendId = id,
        displayName = displayName.ifBlank { username },
        avatarColor = avatarColor,
        quayPassAvatar = quayPassAvatar,
        playingNow = playingNow,
        lastPlayedAtMillis = lastPlayedAtMillis
    )
