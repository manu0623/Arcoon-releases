package com.nendo.argosy.data.repository

import com.nendo.argosy.util.DiscSheets
import com.nendo.argosy.util.FileNames

data class ClaimCandidate(
    val name: String,
    val isDirectory: Boolean
)

data class ClaimTarget(
    val gameId: Long,
    val rommFileName: String?,
    val title: String
)

private val PLAYLIST_SUFFIXES = setOf("m3u", "m3u8")

private fun stemOf(name: String): String = name.substringBeforeLast('.')

/**
 * Directory names are compared verbatim because a title may legitimately contain a period
 * ("Mr. Do!"), so only a playlist suffix is stripped -- that suffix is a container marker
 * under the ES-DE folder convention, never part of the name.
 */
private fun folderNames(name: String): List<String> =
    if (name.substringAfterLast('.', "").lowercase() in PLAYLIST_SUFFIXES) {
        listOf(name, name.substringBeforeLast('.'))
    } else {
        listOf(name)
    }

private fun normalize(name: String): String = FileNames.normalizeForMatch(name)

private enum class Tier { EXACT_FILE, EXACT_FOLDER, STEM_FILE, STEM_FOLDER, TITLE_FILE, TITLE_FOLDER }

private fun matches(tier: Tier, target: ClaimTarget, candidate: ClaimCandidate): Boolean {
    val romm = target.rommFileName?.takeIf { it.isNotBlank() }
    return when (tier) {
        Tier.EXACT_FILE ->
            !candidate.isDirectory && romm != null && candidate.name.equals(romm, ignoreCase = true)
        Tier.EXACT_FOLDER ->
            candidate.isDirectory && romm != null &&
                folderNames(candidate.name).any { it.equals(romm, ignoreCase = true) }
        Tier.STEM_FILE ->
            !candidate.isDirectory && romm != null &&
                stemOf(candidate.name).equals(stemOf(romm), ignoreCase = true)
        Tier.STEM_FOLDER ->
            candidate.isDirectory && romm != null &&
                folderNames(candidate.name).any { it.equals(stemOf(romm), ignoreCase = true) }
        Tier.TITLE_FILE ->
            !candidate.isDirectory && romm == null &&
                normalize(stemOf(candidate.name)) == normalize(target.title)
        Tier.TITLE_FOLDER ->
            candidate.isDirectory &&
                folderNames(candidate.name).any { normalize(it) == normalize(target.title) }
    }
}

/**
 * Assigns on-disk entries to games tier by tier across a whole platform; a contested entry, or a
 * game matching several files in one tier, claims nothing unless exactly one match is a disc sheet.
 * Candidates are compared by name and kind, so a caller listing several directories passes each
 * name once, keeping the most specific directory's copy.
 */
fun claimLocalEntries(
    targets: List<ClaimTarget>,
    candidates: List<ClaimCandidate>
): Map<Long, ClaimCandidate> {
    val claims = mutableMapOf<Long, ClaimCandidate>()
    val taken = mutableSetOf<ClaimCandidate>()

    for (tier in Tier.entries) {
        val proposals = mutableMapOf<ClaimCandidate, MutableList<Long>>()
        for (target in targets) {
            if (claims.containsKey(target.gameId)) continue
            val hits = candidates.filter { it !in taken && matches(tier, target, it) }
            val only = hits.singleOrNull()
                ?: hits.singleOrNull { !it.isDirectory && DiscSheets.isLoader(it.name) }
                ?: continue
            proposals.getOrPut(only) { mutableListOf() }.add(target.gameId)
        }
        for ((candidate, contenders) in proposals) {
            val winner = contenders.singleOrNull() ?: continue
            claims[winner] = candidate
            taken += candidate
        }
    }
    return claims
}
