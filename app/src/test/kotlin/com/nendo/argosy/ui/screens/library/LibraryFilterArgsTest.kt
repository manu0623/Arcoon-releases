package com.nendo.argosy.ui.screens.library

import com.nendo.argosy.data.model.ActiveSort
import com.nendo.argosy.data.model.SortOption
import com.nendo.argosy.data.model.SourceFilter
import com.nendo.argosy.domain.model.LibraryLinkFilters
import com.nendo.argosy.domain.model.PlayerCountBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryFilterArgsTest {

    /**
     * Every category the library can apply has to survive the route. A field dropped here is a
     * filter the setup offers and the opened library silently ignores.
     */
    @Test
    fun `every filter category survives the trip`() {
        val filters = LibraryLinkFilters(
            source = SourceFilter.FAVORITES,
            platformIds = setOf(7L, 2L),
            genres = setOf("Shooter", "Role-playing (RPG)"),
            series = setOf("Mega Man", "The Legend of Zelda"),
            players = PlayerCountBucket.FOUR_PLUS,
            sort = ActiveSort(SortOption.RELEASE_YEAR, descending = false)
        )

        val decoded = LibraryFilterArgs.decode(LibraryFilterArgs.encode(filters))

        assertEquals(filters, decoded)
    }

    @Test
    fun `a value holding separators survives the trip`() {
        val genres = setOf("Hack, slash & beat 'em up", "アクション", "Puzzle=Strategy;Sim")
        val series = setOf("Tom Clancy's Splinter Cell", "Ys")

        val decoded = LibraryFilterArgs.decode(
            LibraryFilterArgs.encode(LibraryLinkFilters(genres = genres, series = series))
        )

        assertEquals(genres, decoded?.genres)
        assertEquals(series, decoded?.series)
    }

    @Test
    fun `an unfiltered link encodes to nothing`() {
        assertNull(LibraryFilterArgs.encode(LibraryLinkFilters()))
        assertNull(LibraryFilterArgs.decode(null))
        assertNull(LibraryFilterArgs.decode(""))
    }

    /**
     * Sort direction is a separate field from the option, so a descending pick must not read back
     * as the option's own default.
     */
    @Test
    fun `a sort direction against the option's default survives`() {
        val ascendingByRating = ActiveSort(SortOption.RATING, descending = false)

        val decoded = LibraryFilterArgs.decode(
            LibraryFilterArgs.encode(LibraryLinkFilters(sort = ascendingByRating))
        )

        assertEquals(ascendingByRating, decoded?.sort)
    }

    @Test
    fun `an unreadable field leaves the rest decodable`() {
        val decoded = LibraryFilterArgs.decode("p=notanumber,4;g=Shooter;src=NOPE;zz=1")

        assertEquals(setOf(4L), decoded?.platformIds)
        assertEquals(setOf("Shooter"), decoded?.genres)
        assertEquals(SourceFilter.ALL, decoded?.source)
    }
}
