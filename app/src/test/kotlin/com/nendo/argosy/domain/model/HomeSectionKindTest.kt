package com.nendo.argosy.domain.model

import com.nendo.argosy.ui.screens.home.HomeRow
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The home row list is built from its own repositories, so the only thing keeping it in the order
 * the rest of the app assumes is this enum.
 */
class HomeSectionKindTest {

    @Test
    fun `leading order is the one both surfaces build against`() {
        assertEquals(
            listOf(
                HomeSectionKind.CONTINUE,
                HomeSectionKind.RECOMMENDATIONS,
                HomeSectionKind.FAVORITES,
                HomeSectionKind.ANDROID,
                HomeSectionKind.STEAM
            ),
            HomeSectionKind.LEADING
        )
    }

    @Test
    fun `every leading kind is claimed by a row on the single screen`() {
        val rows = listOf(
            HomeRow.Continue,
            HomeRow.Recommendations,
            HomeRow.Favorites,
            HomeRow.Android,
            HomeRow.Steam
        )
        assertEquals(HomeSectionKind.LEADING, rows.map { it.kind })
    }

    @Test
    fun `the repeating kinds sit between the two fixed runs`() {
        assertEquals(
            listOf(
                HomeSectionKind.PLATFORM,
                HomeSectionKind.PINNED_REGULAR,
                HomeSectionKind.PINNED_VIRTUAL,
                HomeSectionKind.MEDIA_LIBRARY
            ),
            HomeSectionKind.REPEATING
        )
    }

    /**
     * Next Up closes the listing because the row cursor wraps: last is one backwards press from
     * first, which is the whole reason that row is placed there rather than among the leading rows.
     */
    @Test
    fun `the trailing run closes with next up`() {
        assertEquals(
            listOf(HomeSectionKind.CONTINUE_WATCHING, HomeSectionKind.NEXT_UP),
            HomeSectionKind.TRAILING
        )
        assertEquals(HomeSectionKind.NEXT_UP, HomeSectionKind.entries.last())
    }

    @Test
    fun `the three runs account for every kind exactly once and in declared order`() {
        assertEquals(
            HomeSectionKind.entries.toList(),
            HomeSectionKind.LEADING + HomeSectionKind.REPEATING + HomeSectionKind.TRAILING
        )
    }

    @Test
    fun `the media kinds are the ones a signed-out account removes`() {
        assertEquals(
            setOf(
                HomeSectionKind.MEDIA_LIBRARY,
                HomeSectionKind.CONTINUE_WATCHING,
                HomeSectionKind.NEXT_UP
            ),
            HomeSectionKind.MEDIA
        )
    }

    /**
     * A repeating kind is one no single row object can stand for, so each is modelled with the thing
     * it repeats over carried on the row. A kind drifting out of that shape is what turns a
     * per-library row back into one aggregate rail.
     */
    @Test
    fun `every repeating kind is claimed by a row carrying what it repeats over`() {
        val rows = listOf(
            HomeRow.Platform(0),
            HomeRow.PinnedRegular(pinId = 1L, collectionId = 2L, name = "Pinned"),
            HomeRow.PinnedVirtual(
                pinId = 3L,
                type = com.nendo.argosy.domain.usecase.collection.CategoryType.GENRE,
                name = "Virtual"
            ),
            HomeRow.MediaLibrary(0)
        )
        assertEquals(HomeSectionKind.REPEATING, rows.map { it.kind })
    }
}
