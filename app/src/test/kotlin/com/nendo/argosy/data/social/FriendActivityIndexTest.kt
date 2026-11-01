package com.nendo.argosy.data.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendActivityIndexTest {

    private fun friend(
        id: String,
        presence: PresenceStatus?,
        igdbId: Int?,
        status: String = "accepted",
        title: String = "Some Game"
    ) = Friend(
        id = id,
        username = id,
        displayName = id.replaceFirstChar { it.uppercase() },
        avatarColor = "#336699",
        status = status,
        presence = presence,
        currentGame = PresenceGameInfo(title = title, igdbId = igdbId)
    )

    @Test
    fun `friends in the same game are grouped under its id`() {
        val index = liveFriendActivity(
            listOf(
                friend("alex", PresenceStatus.IN_GAME, igdbId = 1020),
                friend("sam", PresenceStatus.IN_GAME, igdbId = 1020),
                friend("kim", PresenceStatus.IN_GAME, igdbId = 77)
            )
        )

        assertEquals(listOf("alex", "sam"), index[1020]?.map { it.friendId })
        assertEquals(listOf("kim"), index[77]?.map { it.friendId })
        assertTrue(index.values.flatten().all { it.playingNow })
    }

    @Test
    fun `a friend not in a game is left out even with a game on record`() {
        val index = liveFriendActivity(
            listOf(
                friend("alex", PresenceStatus.ONLINE, igdbId = 1020),
                friend("sam", PresenceStatus.OFFLINE, igdbId = 1020),
                friend("kim", PresenceStatus.WATCHING, igdbId = 1020)
            )
        )

        assertTrue(index.isEmpty())
    }

    @Test
    fun `a game without an igdb id matches nothing even when the title does`() {
        val index = liveFriendActivity(
            listOf(friend("alex", PresenceStatus.IN_GAME, igdbId = null, title = "Chrono Trigger"))
        )

        assertTrue(index.isEmpty())
    }

    private val now = java.time.Instant.parse("2026-09-23T12:00:00Z").toEpochMilli()

    private fun daysAgo(days: Long): String =
        java.time.Instant.ofEpochMilli(now).minus(java.time.Duration.ofDays(days)).toString()

    @Test
    fun `plays inside fourteen days count and older ones do not`() {
        val alex = friend("alex", PresenceStatus.OFFLINE, igdbId = null)
        val recent = recentFriendActivity(
            friends = listOf(alex),
            playsByFriendId = mapOf(
                "alex" to listOf(
                    ActiveGameRow(igdbId = 1, lastPlayed = daysAgo(2)),
                    ActiveGameRow(igdbId = 2, lastPlayed = daysAgo(14)),
                    ActiveGameRow(igdbId = 3, lastPlayed = daysAgo(15)),
                    ActiveGameRow(igdbId = 4, lastPlayed = null)
                )
            ),
            nowMillis = now
        )

        assertEquals(setOf(1, 2), recent.keys)
        assertTrue(recent.values.flatten().none { it.playingNow })
    }

    @Test
    fun `plays of someone no longer a friend are dropped`() {
        val recent = recentFriendActivity(
            friends = listOf(friend("alex", PresenceStatus.OFFLINE, igdbId = null, status = "pending")),
            playsByFriendId = mapOf("alex" to listOf(ActiveGameRow(igdbId = 1, lastPlayed = daysAgo(1)))),
            nowMillis = now
        )

        assertTrue(recent.isEmpty())
    }

    @Test
    fun `a friend playing now is listed once, ahead of recent players ordered newest first`() {
        val alex = friend("alex", PresenceStatus.IN_GAME, igdbId = 9)
        val sam = friend("sam", PresenceStatus.OFFLINE, igdbId = null)
        val kim = friend("kim", PresenceStatus.OFFLINE, igdbId = null)
        val friends = listOf(alex, sam, kim)
        val plays = mapOf(
            "alex" to listOf(ActiveGameRow(igdbId = 9, lastPlayed = daysAgo(1))),
            "sam" to listOf(ActiveGameRow(igdbId = 9, lastPlayed = daysAgo(5))),
            "kim" to listOf(ActiveGameRow(igdbId = 9, lastPlayed = daysAgo(2)))
        )

        val merged = mergeFriendActivity(
            live = liveFriendActivity(friends),
            recent = recentFriendActivity(friends, plays, now)
        )

        assertEquals(listOf("alex", "kim", "sam"), merged[9]?.map { it.friendId })
        assertEquals(listOf(true, false, false), merged[9]?.map { it.playingNow })
    }

    @Test
    fun `pending requests are not friends yet`() {
        val index = liveFriendActivity(
            listOf(friend("alex", PresenceStatus.IN_GAME, igdbId = 1020, status = "pending"))
        )

        assertTrue(index.isEmpty())
    }
}
