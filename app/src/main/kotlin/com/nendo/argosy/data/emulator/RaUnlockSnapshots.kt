package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.repository.RetroAchievementsRepository.GameUnlocks
import com.nendo.argosy.domain.model.SnapshotUnlock
import java.time.Instant

/**
 * The unlock snapshot taken at the launch of the session for [gameId] that started at [startTime].
 */
data class LaunchUnlockSnapshot(
    val gameId: Long,
    val startTime: Instant,
    val raGameId: Long,
    val unlocks: GameUnlocks
)

object RaUnlockSnapshots {

    private const val HARDCORE_PREFIX = "h:"
    private const val SOFTCORE_PREFIX = "s:"
    private const val SEPARATOR = ","
    private const val FIELD_SEPARATOR = "|"
    private const val LAUNCH_FIELDS = 5

    fun encodeLaunch(snapshot: LaunchUnlockSnapshot): String = listOf(
        snapshot.gameId.toString(),
        snapshot.startTime.toString(),
        snapshot.raGameId.toString(),
        snapshot.unlocks.unlockedIds.sorted().joinToString(SEPARATOR),
        snapshot.unlocks.hardcoreUnlockedIds.sorted().joinToString(SEPARATOR)
    ).joinToString(FIELD_SEPARATOR)

    /**
     * The launch snapshot [encoded] holds, or null when nothing was stored or it does not parse.
     */
    fun decodeLaunch(encoded: String?): LaunchUnlockSnapshot? {
        val fields = encoded?.split(FIELD_SEPARATOR)?.takeIf { it.size == LAUNCH_FIELDS } ?: return null
        val gameId = fields[0].toLongOrNull() ?: return null
        val startTime = runCatching { Instant.parse(fields[1]) }.getOrNull() ?: return null
        val raGameId = fields[2].toLongOrNull() ?: return null
        val unlocked = idsOf(fields[3]) ?: return null
        val hardcore = idsOf(fields[4]) ?: return null
        return LaunchUnlockSnapshot(gameId, startTime, raGameId, GameUnlocks(unlocked, hardcore))
    }

    private fun idsOf(field: String): Set<Long>? {
        val parts = field.split(SEPARATOR).filter { it.isNotBlank() }
        val ids = parts.mapNotNull { it.toLongOrNull() }
        return if (ids.size == parts.size) ids.toSet() else null
    }

    /**
     * The achievements unlocked between [atLaunch] and [atEnd]. An id new to the hardcore set is a
     * hardcore unlock, one new only to the softcore set a softcore unlock. Null when [atLaunch] is
     * missing.
     */
    fun diff(atLaunch: GameUnlocks?, atEnd: GameUnlocks): List<SnapshotUnlock>? {
        if (atLaunch == null) return null
        val newHardcore = atEnd.hardcoreUnlockedIds - atLaunch.hardcoreUnlockedIds
        val newSoftcore = atEnd.unlockedIds - atLaunch.unlockedIds - atLaunch.hardcoreUnlockedIds - newHardcore
        return (newHardcore.map { SnapshotUnlock(it, isHardcore = true) } +
            newSoftcore.map { SnapshotUnlock(it, isHardcore = false) })
            .sortedBy { it.achievementId }
    }

    fun encode(unlocks: List<SnapshotUnlock>): String =
        unlocks.joinToString(SEPARATOR) { (if (it.isHardcore) HARDCORE_PREFIX else SOFTCORE_PREFIX) + it.achievementId }

    /**
     * The unlocks [encoded] holds, or null when nothing was stored. Entries that do not parse are
     * left out.
     */
    fun decode(encoded: String?): List<SnapshotUnlock>? {
        if (encoded == null) return null
        return encoded.split(SEPARATOR).filter { it.isNotBlank() }.mapNotNull { entry ->
            val isHardcore = when {
                entry.startsWith(HARDCORE_PREFIX) -> true
                entry.startsWith(SOFTCORE_PREFIX) -> false
                else -> return@mapNotNull null
            }
            entry.drop(HARDCORE_PREFIX.length).toLongOrNull()?.let { SnapshotUnlock(it, isHardcore) }
        }
    }
}
