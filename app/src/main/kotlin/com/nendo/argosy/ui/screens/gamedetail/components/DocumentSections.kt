package com.nendo.argosy.ui.screens.gamedetail.components

import com.nendo.argosy.data.repository.DocumentHighlight

private const val MAX_SECTION_REACH = 30
private const val MIN_RULE_LENGTH = 3
private val RULE_CHARS = setOf('=', '-', '*', '_', '#', '~', '+', '.')

/**
 * The block of text around [lineIndex]: the run of lines bounded by blank lines or divider rules
 * such as `=====`, reaching at most [MAX_SECTION_REACH] lines each way. A blank line resolves to
 * the block that starts below it. Null when the touched line is a divider or nothing follows it.
 */
fun sectionAt(lines: List<String>, lineIndex: Int): IntRange? {
    if (lineIndex !in lines.indices) return null
    val anchor = if (lines[lineIndex].isBlank()) {
        (lineIndex until lines.size).firstOrNull { !lines[it].isBlank() } ?: return null
    } else {
        lineIndex
    }
    if (isBoundary(lines[anchor])) return null
    var first = anchor
    while (first > 0 && anchor - first < MAX_SECTION_REACH && !isBoundary(lines[first - 1])) first--
    var last = anchor
    while (last < lines.lastIndex && last - anchor < MAX_SECTION_REACH && !isBoundary(lines[last + 1])) last++
    return first..last
}

/**
 * [highlights] with [section] added, or with every highlight covering [touchedLine] removed when
 * one already does.
 */
fun toggleHighlight(
    highlights: List<DocumentHighlight>,
    section: IntRange,
    touchedLine: Int
): List<DocumentHighlight> {
    val covering = highlights.filter { touchedLine in it.lines }
    if (covering.isNotEmpty()) return highlights - covering.toSet()
    return (
        highlights.filterNot { it.lines.first >= section.first && it.lines.last <= section.last } +
            DocumentHighlight(section)
        ).sortedBy { it.lines.first }
}

/**
 * [highlights] with the one starting at [firstLine] moved to the next of [paletteSize] colours.
 */
fun cycleHighlightColor(
    highlights: List<DocumentHighlight>,
    firstLine: Int,
    paletteSize: Int
): List<DocumentHighlight> = highlights.map {
    if (it.lines.first == firstLine) it.copy(colorIndex = (it.colorIndex + 1).mod(paletteSize)) else it
}

private fun isBoundary(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return true
    return trimmed.length >= MIN_RULE_LENGTH && trimmed.all { it in RULE_CHARS || it == ' ' }
}
