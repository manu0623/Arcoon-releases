package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.domain.model.HomeLayoutKind
import com.nendo.argosy.ui.components.AppMenuRow
import com.nendo.argosy.ui.components.CustomGridState
import com.nendo.argosy.ui.screens.home.delegates.HomeInputActions
import com.nendo.argosy.ui.screens.home.delegates.HomeInputHandler
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTileMenuInputTest {

    private fun handler(state: HomeUiState): Pair<HomeInputHandler, HomeInputActions> {
        val actions = mockk<HomeInputActions>(relaxed = true)
        every { actions.uiState } returns MutableStateFlow(state)
        return HomeInputHandler(actions, true, {}, {}, {}) to actions
    }

    @Test
    fun `hold confirm on a grid tile opens the tile menu`() {
        val (handler, actions) = handler(HomeUiState(layoutKind = HomeLayoutKind.CUSTOM_GRID))

        assertTrue(handler.onLongConfirm().handled)
        verify(exactly = 1) { actions.openTileMenu() }
    }

    @Test
    fun `an open tile menu with screen rows routes the d-pad and confirm to the menu`() {
        val screens = listOf(AppMenuRow.OpenOnScreen(0, 1, "a"), AppMenuRow.OpenOnScreen(4, 2, "b"))
        val (handler, actions) = handler(
            HomeUiState(
                layoutKind = HomeLayoutKind.CUSTOM_GRID,
                customGrid = CustomGridState(showMenu = true, menuScreens = screens)
            )
        )

        assertTrue(handler.onDown().handled)
        verify(exactly = 1) { actions.moveTileMenuFocus(1) }
        assertTrue(handler.onUp().handled)
        verify(exactly = 1) { actions.moveTileMenuFocus(-1) }
        assertTrue(handler.onConfirm().handled)
        verify(exactly = 1) { actions.confirmTileMenu() }
        verify(exactly = 0) { actions.launchTileApp(any()) }
        assertTrue(handler.onBack().handled)
        verify(exactly = 1) { actions.closeTileMenu() }
        verify(exactly = 0) { actions.moveCustomGridFocus(any()) }
    }
}
