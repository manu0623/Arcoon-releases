package com.nendo.argosy.domain.usecase.recap

import com.nendo.argosy.data.remote.romm.RomMEarnedAchievement
import com.nendo.argosy.domain.model.WindowUnlock
import com.nendo.argosy.util.parseTimestamp
import java.time.Instant
import java.time.temporal.ChronoUnit

object UnlockWindow {

    /**
     * The achievements of [earned] unlocked inside [start, end], both ends inclusive, oldest first.
     * RetroAchievements dates carry whole seconds, so [start] is compared at second precision. An
     * entry with no parseable date inside the window is left out.
     */
    fun unlocksWithin(earned: List<RomMEarnedAchievement>, start: Instant, end: Instant): List<WindowUnlock> {
        val from = start.truncatedTo(ChronoUnit.SECONDS)
        fun inside(at: Instant): Boolean = !at.isBefore(from) && !at.isAfter(end)
        return earned.mapNotNull { achievement ->
            val hardcoreAt = achievement.dateHardcore.toInstantOrNull()?.takeIf { inside(it) }
            if (hardcoreAt != null) return@mapNotNull WindowUnlock(achievement.id, hardcoreAt, isHardcore = true)
            val softcoreAt = achievement.date.toInstantOrNull()?.takeIf { inside(it) } ?: return@mapNotNull null
            WindowUnlock(achievement.id, softcoreAt, isHardcore = false)
        }.sortedBy { it.unlockedAt }
    }

    private fun String?.toInstantOrNull(): Instant? =
        this?.let { parseTimestamp(it) }?.let { Instant.ofEpochMilli(it) }
}
