package com.nendo.argosy.ui.screens.library

import android.content.Context
import com.nendo.argosy.R
import com.nendo.argosy.data.model.ActiveSort
import com.nendo.argosy.data.model.SortOption
import com.nendo.argosy.data.model.SourceFilter
import com.nendo.argosy.domain.model.PlayerCountBucket
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ActiveFilters.entries] is the one answer to "which filters are on": the count, the summary,
 * the modal's tab markers and the header chips all read it. The summary wording predates the
 * list and must read exactly as it did before being derived from it.
 */
class ActiveFiltersEntriesTest {

    private val context = mockk<Context>(relaxed = true).also {
        every { it.getString(R.string.source_filter_all) } returns "All Games"
        every { it.getString(R.string.source_filter_playable) } returns "Playable"
        every { it.getString(R.string.sort_rating) } returns "Rating"
        every { it.getString(R.string.library_filter_players_two) } returns "2"
        every {
            it.resources.getQuantityString(R.plurals.library_filter_active_count, 2, 2)
        } returns "2 filters"
        every {
            it.resources.getQuantityString(R.plurals.library_filter_active_count, 3, 3)
        } returns "3 filters"
    }

    @Test
    fun `nothing active yields no entries and reads as all games`() {
        val filters = ActiveFilters()

        assertTrue(filters.entries.isEmpty())
        assertEquals(0, filters.activeCount)
        assertEquals("All Games", filters.summary(context))
        FilterCategory.entries.forEach { assertFalse(filters.isActive(it)) }
    }

    @Test
    fun `a search term is one entry carrying the term and is summarised in quotes`() {
        val filters = ActiveFilters(searchQuery = "mario")

        assertEquals(listOf(ActiveFilterEntry(FilterCategory.SEARCH, text = "mario")), filters.entries)
        assertEquals(1, filters.activeCount)
        assertEquals("\"mario\"", filters.summary(context))
        assertTrue(filters.isActive(FilterCategory.SEARCH))
    }

    @Test
    fun `a source other than all carries its label`() {
        val filters = ActiveFilters(source = SourceFilter.PLAYABLE)

        assertEquals(
            listOf(ActiveFilterEntry(FilterCategory.SOURCE, labelRes = R.string.source_filter_playable)),
            filters.entries
        )
        assertEquals(1, filters.activeCount)
        assertEquals("Playable", filters.summary(context))
    }

    @Test
    fun `a sort other than title carries the option label`() {
        val filters = ActiveFilters(sort = ActiveSort(SortOption.RATING))

        assertEquals(
            listOf(ActiveFilterEntry(FilterCategory.SORT, labelRes = R.string.sort_rating)),
            filters.entries
        )
        assertEquals(1, filters.activeCount)
        assertEquals("Rating", filters.summary(context))
    }

    @Test
    fun `the title sort is the default and never counts`() {
        val filters = ActiveFilters(sort = ActiveSort(SortOption.TITLE, descending = true))

        assertTrue(filters.entries.isEmpty())
        assertFalse(filters.isActive(FilterCategory.SORT))
    }

    @Test
    fun `a player bucket is one entry carrying its label and counting once`() {
        val filters = ActiveFilters(players = PlayerCountBucket.TWO)

        assertEquals(
            listOf(ActiveFilterEntry(FilterCategory.PLAYERS, labelRes = R.string.library_filter_players_two)),
            filters.entries
        )
        assertEquals(1, filters.activeCount)
        assertEquals("2", filters.summary(context))
        assertTrue(filters.isActive(FilterCategory.PLAYERS))
    }

    @Test
    fun `a lone platform is summarised by its name`() {
        val filters = ActiveFilters(platforms = setOf(PlatformRef(4L, "SNES")))

        assertEquals(listOf(ActiveFilterEntry(FilterCategory.PLATFORM, text = "SNES", count = 1)), filters.entries)
        assertEquals("SNES", filters.summary(context))
    }

    /**
     * Two platforms can carry one display name, and a chip reads the name while a match reads the
     * id. Counting by name would report one filter where two are set.
     */
    @Test
    fun `two platforms sharing a name are still two filters`() {
        val filters = ActiveFilters(
            platforms = setOf(PlatformRef(4L, "Arcade"), PlatformRef(9L, "Arcade"))
        )

        assertEquals(2, filters.activeCount)
        assertEquals(2, filters.entries.single().count)
    }

    @Test
    fun `a multi-select category is one entry counting its selections`() {
        val filters = ActiveFilters(genres = setOf("RPG", "Action"))

        assertEquals(listOf(ActiveFilterEntry(FilterCategory.GENRE, text = "RPG", count = 2)), filters.entries)
        assertEquals(2, filters.activeCount)
        assertEquals("2 filters", filters.summary(context))
        assertTrue(filters.isActive(FilterCategory.GENRE))
        assertFalse(filters.isActive(FilterCategory.PLAYERS))
    }

    @Test
    fun `a lone region names itself and marks only the region tab`() {
        val filters = ActiveFilters(regions = setOf("Japan"))

        assertEquals(listOf(ActiveFilterEntry(FilterCategory.REGION, text = "Japan", count = 1)), filters.entries)
        assertEquals("Japan", filters.summary(context))
        assertTrue(filters.isActive(FilterCategory.REGION))
        assertFalse(filters.isActive(FilterCategory.GENRE))
    }

    @Test
    fun `several categories sum their counts and follow the tab order`() {
        val filters = ActiveFilters(
            searchQuery = "zelda",
            source = SourceFilter.FAVORITES,
            series = setOf("Zelda")
        )

        assertEquals(
            listOf(FilterCategory.SEARCH, FilterCategory.SOURCE, FilterCategory.SERIES),
            filters.entries.map { it.category }
        )
        assertEquals(3, filters.activeCount)
        assertEquals("3 filters", filters.summary(context))
    }
}
