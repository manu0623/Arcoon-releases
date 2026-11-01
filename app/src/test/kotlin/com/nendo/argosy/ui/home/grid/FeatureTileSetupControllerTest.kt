package com.nendo.argosy.ui.home.grid

import com.nendo.argosy.data.model.SourceFilter
import com.nendo.argosy.domain.model.FeatureTileKind
import com.nendo.argosy.domain.model.HomeTile
import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.domain.model.TileRect
import com.nendo.argosy.ui.components.CustomGridState
import com.nendo.argosy.ui.components.FeatureFilterOptions
import com.nendo.argosy.ui.components.FeatureSetupRow
import com.nendo.argosy.ui.components.FeatureTileSetup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TRACKED_GAME_ID = 42L

class FeatureTileSetupControllerTest {

    private var state = CustomGridState()
    private val placed = mutableListOf<HomeTileTargetRef.Feature>()
    private val edited = mutableListOf<Pair<Long, HomeTileTargetRef.Feature>>()
    private var trackRequests = 0

    private fun controller(scope: CoroutineScope) = FeatureTileSetupController(
        scope = scope,
        filterOptions = { FeatureFilterOptions(platforms = emptyList(), genres = emptyList()) },
        read = { state },
        write = { transform -> state = transform(state) },
        onPlace = { target -> placed += target },
        onEdit = { tileId, target -> edited += tileId to target },
        onTrackGame = { trackRequests += 1 }
    )

    @Test
    fun `an RA tile settles on the game the picker named`() = runTest {
        val controller = controller(this)

        controller.begin(kind = FeatureTileKind.RA_SUMMARY)
        controller.confirm(FeatureTileSetup.ROW_MODE_TRACK)
        controller.trackGame(TRACKED_GAME_ID)

        assertEquals(1, trackRequests)
        assertEquals(
            HomeTileTargetRef.Feature(
                kind = FeatureTileKind.RA_SUMMARY,
                pickedGameId = TRACKED_GAME_ID
            ),
            placed.single()
        )
        assertNull(state.featureSetup)
    }

    @Test
    fun `the account row settles an RA tile on no game`() = runTest {
        val controller = controller(this)

        controller.begin(kind = FeatureTileKind.RA_SUMMARY)
        controller.confirm(FeatureTileSetup.ROW_MODE_ACCOUNT)

        assertEquals(0, trackRequests)
        assertEquals(
            HomeTileTargetRef.Feature(kind = FeatureTileKind.RA_SUMMARY, pickedGameId = null),
            placed.single()
        )
        assertNull(state.featureSetup)
    }

    @Test
    fun `a random tile settles with the filters it was answered with`() = runTest {
        val controller = controller(this)

        controller.begin(kind = FeatureTileKind.RANDOM_GAME)
        advanceUntilIdle()
        controller.confirm(rowIndex(FeatureSetupRow.DOWNLOADED_ONLY))
        controller.confirm(rowIndex(FeatureSetupRow.NEVER_PLAYED))
        controller.confirm(rowIndex(FeatureSetupRow.DONE))

        val target = placed.single()
        assertEquals(FeatureTileKind.RANDOM_GAME, target.kind)
        assertNull(target.pickedGameId)
        assertTrue(target.filters.neverPlayed)
        assertFalse(target.filters.downloadedOnly)
        assertNull(state.featureSetup)
    }

    /**
     * A link offers the library's own filter categories and nothing the library cannot apply.
     * A row missing here is a filter the user can never set; a row that should not be here is a
     * control the opened library ignores.
     */
    @Test
    fun `a library link asks the library's filter categories`() = runTest {
        val controller = controller(this)

        controller.begin(kind = FeatureTileKind.LIBRARY_LINK)
        advanceUntilIdle()

        assertEquals(
            listOf(
                FeatureSetupRow.SOURCE,
                FeatureSetupRow.PLATFORMS,
                FeatureSetupRow.GENRES,
                FeatureSetupRow.SERIES,
                FeatureSetupRow.PLAYERS,
                FeatureSetupRow.SORT,
                FeatureSetupRow.DONE
            ),
            state.featureSetup!!.filterRows
        )
    }

    @Test
    fun `a random tile is never asked what the library alone can apply`() = runTest {
        val controller = controller(this)

        controller.begin(kind = FeatureTileKind.RANDOM_GAME)
        advanceUntilIdle()

        val rows = state.featureSetup!!.filterRows
        assertFalse(FeatureSetupRow.SOURCE in rows)
        assertFalse(FeatureSetupRow.SERIES in rows)
        assertFalse(FeatureSetupRow.PLAYERS in rows)
        assertFalse(FeatureSetupRow.SORT in rows)
    }

    @Test
    fun `a library link settles carrying what it was answered with`() = runTest {
        val controller = controller(this)

        controller.begin(kind = FeatureTileKind.LIBRARY_LINK)
        advanceUntilIdle()
        controller.confirm(rowIndex(FeatureSetupRow.SOURCE))
        controller.confirm(rowIndex(FeatureSetupRow.DONE))

        val target = placed.single()
        assertEquals(FeatureTileKind.LIBRARY_LINK, target.kind)
        assertEquals(SourceFilter.PLAYABLE, target.libraryLink?.source)
    }

    /**
     * Done sits at a different index on each kind. A confirm that reached it by a fixed position
     * would settle a library link while the cursor was on another row.
     */
    @Test
    fun `the done row is the last row whatever the kind asks`() = runTest {
        val controller = controller(this)

        controller.begin(kind = FeatureTileKind.LIBRARY_LINK)
        advanceUntilIdle()
        val setup = state.featureSetup!!

        assertEquals(setup.filterRows.lastIndex, setup.indexOf(FeatureSetupRow.DONE))
        assertEquals(FeatureSetupRow.PLATFORMS, setup.rowAt(1))
        assertNull(setup.rowAt(setup.filterRows.size))
    }

    private fun rowIndex(row: FeatureSetupRow): Int = state.featureSetup!!.indexOf(row)

    @Test
    fun `editing an RA tile changes that tile rather than placing another`() = runTest {
        val controller = controller(this)
        val tile = HomeTile(
            id = 7L,
            pageIndex = 0,
            rect = TileRect(0, 0),
            target = HomeTileTargetRef.Feature(FeatureTileKind.RA_SUMMARY)
        )

        controller.begin(existing = tile)
        controller.confirm(FeatureTileSetup.ROW_MODE_ACCOUNT)

        assertTrue(placed.isEmpty())
        assertEquals(
            7L to HomeTileTargetRef.Feature(
                kind = FeatureTileKind.RA_SUMMARY,
                pickedGameId = null
            ),
            edited.single()
        )
    }
}
