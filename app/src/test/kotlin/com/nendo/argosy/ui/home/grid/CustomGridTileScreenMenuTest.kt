package com.nendo.argosy.ui.home.grid

import android.content.Context
import com.nendo.argosy.domain.model.GridCell
import com.nendo.argosy.domain.model.HomeTile
import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.domain.model.TileRect
import com.nendo.argosy.ui.components.AppMenuRow
import com.nendo.argosy.ui.components.CustomGridState
import com.nendo.argosy.ui.components.CustomTileMenuAction
import com.nendo.argosy.ui.components.TileEditMode
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomGridTileScreenMenuTest {

    private val app = HomeTileTargetRef.App("com.example.app")

    private var state = CustomGridState(
        tiles = listOf(HomeTile(id = 1L, pageIndex = 0, rect = TileRect(0, 0), target = app)),
        page = 0,
        cell = GridCell(0, 0),
        columns = 4,
        rows = 4,
        storedPages = 2
    )

    private val launched = mutableListOf<Pair<HomeTileTargetRef, AppMenuRow.OpenOnScreen>>()

    private val coordinator = CustomGridCoordinator(
        context = mockk<Context>(relaxed = true),
        scope = CoroutineScope(Dispatchers.Unconfined),
        repository = null,
        ownerUserId = { null },
        pickerEntries = { _, _, _ -> emptyList() },
        onOpenOnScreen = { target, screen -> launched += target to screen },
        read = { state },
        write = { transform -> state = transform(state) }
    )

    private val screens = listOf(
        AppMenuRow.OpenOnScreen(displayId = 0, number = 1, screenKey = "a"),
        AppMenuRow.OpenOnScreen(displayId = 4, number = 2, screenKey = "b")
    )

    @Test
    fun `screen rows sit above the tile's own actions`() {
        coordinator.openMenu(screens)

        assertTrue(state.showMenu)
        assertEquals(screens.size + state.menuActions.size, state.menuEntryCount)
        assertEquals(screens[0], state.menuScreenAt(0))
        assertEquals(state.menuActions.first(), state.menuActionAt(screens.size))
        assertNull(state.menuActionAt(0))
    }

    @Test
    fun `the destructive fence moves down by the screen rows`() {
        coordinator.openMenu(screens)

        assertEquals(
            screens.size + state.menuActions.indexOf(CustomTileMenuAction.REMOVE),
            state.menuDangerFromIndex
        )
    }

    @Test
    fun `confirming a screen row launches the tile there and closes the menu`() {
        coordinator.openMenu(screens)
        coordinator.moveMenuFocus(1)
        coordinator.confirmMenu()

        assertEquals(listOf<Pair<HomeTileTargetRef, AppMenuRow.OpenOnScreen>>(app to screens[1]), launched)
        assertFalse(state.showMenu)
        assertTrue(state.menuScreens.isEmpty())
    }

    @Test
    fun `confirming the first action after the screens runs that action`() {
        coordinator.openMenu(screens)
        coordinator.moveMenuFocus(screens.size)
        assertEquals(CustomTileMenuAction.ARRANGE, state.menuActionAt(state.menuFocusIndex))

        coordinator.confirmMenu()

        assertTrue(launched.isEmpty())
        assertEquals(TileEditMode.MOVE, state.editMode)
    }

    @Test
    fun `a menu opened without screens starts on the tile's first action`() {
        coordinator.openMenu()

        assertTrue(state.menuScreens.isEmpty())
        assertEquals(state.menuActions.first(), state.menuActionAt(0))
        assertNull(state.menuScreenAt(0))
    }
}
