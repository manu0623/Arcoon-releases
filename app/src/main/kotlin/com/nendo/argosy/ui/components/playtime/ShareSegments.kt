package com.nendo.argosy.ui.components.playtime

import androidx.compose.ui.graphics.Color

private const val PERCENT = 100

/**
 * One row a share band can show: a named total with an optional platform glyph and badge.
 */
data class ShareEntry(
    val label: String,
    val activeMs: Long,
    val platformSlug: String? = null,
    val badge: String? = null
)

/**
 * The first [limit] of [entries] as coloured segments in [series] order, then one segment named
 * [othersLabel] for the rest when the rest has any time. Shares are of the total across every
 * entry.
 */
fun buildShareSegments(
    entries: List<ShareEntry>,
    limit: Int,
    series: List<Color>,
    othersLabel: String,
    othersColor: Color,
    valueLabel: (Long) -> String,
    shareLabel: (Int) -> String
): List<ShareSegment> {
    val total = entries.sumOf { it.activeMs }
    val named = entries.take(limit).mapIndexed { index, entry ->
        ShareSegment(
            label = entry.label,
            valueMs = entry.activeMs,
            color = series.getOrElse(index) { othersColor },
            valueLabel = valueLabel(entry.activeMs),
            shareLabel = shareLabel(percentOf(entry.activeMs, total)),
            platformSlug = entry.platformSlug,
            badge = entry.badge
        )
    }
    val othersMs = entries.drop(limit).sumOf { it.activeMs }
    if (othersMs <= 0L) return named
    return named + ShareSegment(
        label = othersLabel,
        valueMs = othersMs,
        color = othersColor,
        valueLabel = valueLabel(othersMs),
        shareLabel = shareLabel(percentOf(othersMs, total))
    )
}

private fun percentOf(part: Long, whole: Long): Int =
    if (whole <= 0L) 0 else ((part * PERCENT) / whole).toInt()
