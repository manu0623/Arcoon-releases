package com.nendo.argosy.ui.home.grid

import android.content.Context
import com.nendo.argosy.domain.model.FeatureTileKind
import com.nendo.argosy.domain.model.HomeTile
import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.domain.model.TileRect
import com.nendo.argosy.ui.components.CustomGridState
import com.nendo.argosy.ui.components.RaTileStatus
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val RA_TILE_ID = 1L
private const val GAME_TILE_ID = 2L
private const val BROWSE_COUNT = 4

class CustomGridCoordinatorStepEngagedTest {

    private var state = CustomGridState()

    private val coordinator = CustomGridCoordinator(
        context = mockk<Context>(relaxed = true),
        scope = CoroutineScope(Dispatchers.Unconfined),
        repository = null,
        ownerUserId = { null },
        pickerEntries = { _, _, _ -> emptyList() },
        read = { state },
        write = { transform -> state = transform(state) }
    )

    private val raTile = HomeTile(
        id = RA_TILE_ID,
        pageIndex = 0,
        rect = TileRect(0, 0),
        target = HomeTileTargetRef.Feature(FeatureTileKind.RA_SUMMARY)
    )

    private val gameTile = HomeTile(
        id = GAME_TILE_ID,
        pageIndex = 0,
        rect = TileRect(1, 0),
        target = HomeTileTargetRef.Game(gameId = 9L)
    )

    private fun engaged(tileId: Long, index: Int) {
        state = CustomGridState(
            tiles = listOf(raTile, gameTile),
            columns = 4,
            rows = 2,
            engagedTileId = tileId,
            engagedIndex = index,
            raTile = RaTileStatus(signedIn = true, browseCount = BROWSE_COUNT)
        )
    }

    @Test
    fun `stepping forward off the last badge lands on the first`() {
        engaged(RA_TILE_ID, index = BROWSE_COUNT - 1)

        assertTrue(coordinator.stepEngaged(1))
        assertEquals(0, state.engagedIndex)
    }

    @Test
    fun `stepping back off the first badge lands on the last`() {
        engaged(RA_TILE_ID, index = 0)

        assertTrue(coordinator.stepEngaged(-1))
        assertEquals(BROWSE_COUNT - 1, state.engagedIndex)
    }

    @Test
    fun `a tile that is not the RA one keeps its own d-pad`() {
        engaged(GAME_TILE_ID, index = 0)

        assertFalse(coordinator.stepEngaged(1))
        assertEquals(0, state.engagedIndex)
    }

    @Test
    fun `an RA tile with nothing to browse does not step`() {
        engaged(RA_TILE_ID, index = 0)
        state = state.copy(raTile = RaTileStatus(signedIn = true, browseCount = 0))

        assertFalse(coordinator.stepEngaged(1))
        assertEquals(0, state.engagedIndex)
    }
}
