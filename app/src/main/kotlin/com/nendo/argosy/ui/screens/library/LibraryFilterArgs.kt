package com.nendo.argosy.ui.screens.library

import com.nendo.argosy.data.model.ActiveSort
import com.nendo.argosy.data.model.SortOption
import com.nendo.argosy.data.model.SourceFilter
import com.nendo.argosy.domain.model.LibraryLinkFilters
import com.nendo.argosy.domain.model.PlayerCountBucket
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Carries a home tile's saved filters through the library route. Every value is percent-encoded
 * before the list is joined, so a genre or series name holding a separator, a space or a non-Latin
 * script survives the trip intact.
 */
object LibraryFilterArgs {

    private const val SOURCE = "src"
    private const val PLATFORMS = "p"
    private const val GENRES = "g"
    private const val SERIES = "se"
    private const val PLAYERS = "pl"
    private const val SORT = "so"
    private const val SORT_DESCENDING = "sd"
    private const val PAIR_SEPARATOR = ";"
    private const val VALUE_SEPARATOR = ","
    private const val FIELD_SEPARATOR = "="
    private const val CHARSET = "UTF-8"

    fun encode(filters: LibraryLinkFilters): String? {
        val parts = buildList {
            if (filters.source != SourceFilter.ALL) add(pair(SOURCE, filters.source.name))
            if (filters.platformIds.isNotEmpty()) {
                add(pair(PLATFORMS, filters.platformIds.sorted().joinToString(VALUE_SEPARATOR)))
            }
            if (filters.genres.isNotEmpty()) add(pair(GENRES, encodeAll(filters.genres)))
            if (filters.series.isNotEmpty()) add(pair(SERIES, encodeAll(filters.series)))
            filters.players?.let { add(pair(PLAYERS, it.name)) }
            if (filters.sort.option != SortOption.TITLE ||
                filters.sort.descending != SortOption.TITLE.defaultDescending
            ) {
                add(pair(SORT, filters.sort.option.name))
                add(pair(SORT_DESCENDING, filters.sort.descending.toString()))
            }
        }
        return parts.joinToString(PAIR_SEPARATOR).takeIf { it.isNotEmpty() }
    }

    /**
     * The filters [encode] wrote, or null when the argument is absent or holds nothing usable.
     * An unreadable field falls back to its default and the rest still decode.
     */
    fun decode(encoded: String?): LibraryLinkFilters? {
        if (encoded.isNullOrBlank()) return null
        var filters = LibraryLinkFilters()
        var sortOption: SortOption? = null
        var descending: Boolean? = null
        var matched = false
        encoded.split(PAIR_SEPARATOR).forEach { part ->
            val field = part.substringBefore(FIELD_SEPARATOR)
            val value = part.substringAfter(FIELD_SEPARATOR, "")
            if (value.isEmpty()) return@forEach
            when (field) {
                SOURCE -> SourceFilter.fromString(value)?.let {
                    filters = filters.copy(source = it)
                    matched = true
                }
                PLATFORMS -> {
                    val ids = value.split(VALUE_SEPARATOR).mapNotNull { it.toLongOrNull() }.toSet()
                    if (ids.isNotEmpty()) {
                        filters = filters.copy(platformIds = ids)
                        matched = true
                    }
                }
                GENRES -> decodeAll(value).takeIf { it.isNotEmpty() }?.let {
                    filters = filters.copy(genres = it)
                    matched = true
                }
                SERIES -> decodeAll(value).takeIf { it.isNotEmpty() }?.let {
                    filters = filters.copy(series = it)
                    matched = true
                }
                PLAYERS -> PlayerCountBucket.entries.find { it.name == value }?.let {
                    filters = filters.copy(players = it)
                    matched = true
                }
                SORT -> SortOption.entries.find { it.name == value }?.let {
                    sortOption = it
                    matched = true
                }
                SORT_DESCENDING -> descending = value.toBooleanStrictOrNull()
            }
        }
        sortOption?.let { option ->
            filters = filters.copy(
                sort = ActiveSort(option, descending ?: option.defaultDescending)
            )
        }
        return filters.takeIf { matched }
    }

    private fun pair(field: String, value: String) = field + FIELD_SEPARATOR + value

    private fun encodeAll(values: Set<String>): String =
        values.sorted().joinToString(VALUE_SEPARATOR) { URLEncoder.encode(it, CHARSET) }

    private fun decodeAll(value: String): Set<String> =
        value.split(VALUE_SEPARATOR)
            .mapNotNull { runCatching { URLDecoder.decode(it, CHARSET) }.getOrNull() }
            .filter { it.isNotBlank() }
            .toSet()
}
