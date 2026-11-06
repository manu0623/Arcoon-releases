package com.nendo.argosy.domain.usecase.journey

import com.nendo.argosy.data.local.entity.AchievementEntity
import com.nendo.argosy.domain.model.AchievementJourney
import com.nendo.argosy.domain.model.JourneyCategory
import com.nendo.argosy.domain.model.JourneyEntry
import com.nendo.argosy.domain.model.JourneyPhase
import com.nendo.argosy.domain.model.JourneyProgress
import com.nendo.argosy.domain.model.JourneyState
import java.time.Instant

/**
 * Turns the achievement rows already stored for a game into a chronological journey. Every entry is
 * an unlock the player actually earned; a locked row only contributes to the totals. [contexts]
 * carries the rich presence line captured at each unlock, and an entry without one stays without
 * context rather than borrowing the state of another moment.
 */
object JourneyReconstruction {

    const val MAX_PHASES = 6
    private const val MILESTONE_LIMIT = 4

    /**
     * The entry an unlocked [row] stands for, dated at the earlier of its two unlock moments, or
     * null for a row that is still locked.
     */
    fun toEntry(row: AchievementEntity): JourneyEntry? {
        val unlockedAt = listOfNotNull(row.unlockedAt, row.unlockedHardcoreAt).minOrNull() ?: return null
        return JourneyEntry(
            raId = row.raId,
            title = row.title,
            description = row.description,
            points = row.points,
            category = AchievementClassifier.classify(row.type, row.title, row.description),
            isHardcore = row.unlockedHardcoreAt != null,
            unlockedAt = Instant.ofEpochMilli(unlockedAt),
            subject = AchievementSubject.of(row.description)
        )
    }

    fun build(
        gameId: Long,
        gameTitle: String,
        platformName: String?,
        raGameId: Long?,
        rows: List<AchievementEntity>,
        contexts: Map<Long, String> = emptyMap(),
        now: Instant = Instant.now()
    ): AchievementJourney {
        val entries = rows
            .mapNotNull(::toEntry)
            .map { entry -> entry.copy(context = contexts[entry.raId]) }
            .sortedWith(compareBy({ it.unlockedAt }, { it.raId }))
        return AchievementJourney(
            gameId = gameId,
            raGameId = raGameId,
            gameTitle = gameTitle,
            platformName = platformName,
            entries = entries,
            phases = phases(entries),
            milestones = milestones(entries),
            progress = JourneyProgress(
                unlocked = entries.size,
                total = rows.size,
                unlockedPoints = entries.sumOf { it.points },
                totalPoints = rows.sumOf { it.points },
                state = stateOf(entries, rows)
            ),
            generatedAt = now,
            evidenceKey = evidenceKey(entries)
        )
    }

    /**
     * Chronological runs of one category, merged until [MAX_PHASES] remain. A merge folds the
     * smallest neighbouring pair together and labels the result with whichever category that pair
     * holds most, the earlier one on a tie.
     */
    fun phases(entries: List<JourneyEntry>): List<JourneyPhase> {
        if (entries.isEmpty()) return emptyList()
        val runs = mutableListOf<MutableList<JourneyEntry>>()
        entries.forEach { entry ->
            val current = runs.lastOrNull()
            if (current != null && current.first().category == entry.category) {
                current.add(entry)
            } else {
                runs.add(mutableListOf(entry))
            }
        }
        while (runs.size > MAX_PHASES) {
            var smallestIndex = 0
            var smallestPoints = Int.MAX_VALUE
            for (index in 0 until runs.size - 1) {
                val combined = runs[index].sumOf { it.points } + runs[index + 1].sumOf { it.points }
                if (combined < smallestPoints) {
                    smallestPoints = combined
                    smallestIndex = index
                }
            }
            runs[smallestIndex].addAll(runs.removeAt(smallestIndex + 1))
        }
        return runs.map { JourneyPhase(dominantCategory(it), it.toList()) }
    }

    /**
     * The first unlock plus the completion entries plus the highest-value unlocks, chronological
     * and without repeats. More than [MILESTONE_LIMIT] worth naming is cut to the earliest.
     */
    fun milestones(entries: List<JourneyEntry>): List<JourneyEntry> {
        if (entries.isEmpty()) return emptyList()
        val picked = buildList {
            add(entries.first())
            entries.filter { it.category == JourneyCategory.COMPLETION }.forEach { add(it) }
            entries.sortedWith(compareByDescending<JourneyEntry> { it.points }.thenBy { it.unlockedAt })
                .forEach { add(it) }
        }
        return picked.distinctBy { it.raId }
            .sortedWith(compareBy({ it.unlockedAt }, { it.raId }))
            .take(MILESTONE_LIMIT)
    }

    /**
     * Identifies the unlock set behind a journey: the entry count plus a hash of every id and
     * unlock second. Two rebuilds of an unchanged set produce the same key.
     */
    fun evidenceKey(entries: List<JourneyEntry>): String {
        val content = entries.joinToString(JourneyCategory.KEY_SEPARATOR) { "${it.raId}@${it.unlockedAt.toEpochMilli()}" }
        return "${entries.size}-${Integer.toHexString(content.hashCode())}"
    }

    private fun stateOf(entries: List<JourneyEntry>, rows: List<AchievementEntity>): JourneyState = when {
        rows.isNotEmpty() && rows.all { it.isUnlocked } -> JourneyState.MASTERED
        entries.any { it.category == JourneyCategory.COMPLETION } -> JourneyState.MAIN_COMPLETE
        entries.size <= JourneyState.EARLY_LIMIT -> JourneyState.EARLY
        else -> JourneyState.IN_PROGRESS
    }

    private fun dominantCategory(run: List<JourneyEntry>): JourneyCategory =
        run.groupingBy { it.category }.eachCount().entries
            .maxWithOrNull(compareBy({ it.value }, { -it.key.ordinal }))
            ?.key
            ?: JourneyCategory.UNCLASSIFIED
}
