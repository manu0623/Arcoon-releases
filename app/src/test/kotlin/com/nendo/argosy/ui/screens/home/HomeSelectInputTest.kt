package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.ui.screens.home.delegates.HomeInputActions
import com.nendo.argosy.ui.screens.home.delegates.HomeInputHandler
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSelectInputTest {

    private fun handler(state: HomeUiState): Pair<HomeInputHandler, HomeInputActions> {
        val actions = mockk<HomeInputActions>(relaxed = true)
        every { actions.uiState } returns MutableStateFlow(state)
        return HomeInputHandler(actions, isDefaultView = true, onGameSelect = {}, onNavigateToDefault = {}, onDrawerToggle = {}) to actions
    }

    @Test
    fun `select on the plain home screen waits on the double-tap window instead of acting`() {
        val (handler, actions) = handler(HomeUiState())

        assertTrue(handler.onSelect().handled)

        verify(exactly = 1) { actions.pressSelect(any()) }
        verify(exactly = 0) { actions.toggleGameMenu() }
    }

    @Test
    fun `select with the game menu open closes it at once and never arms the double tap`() {
        val (handler, actions) = handler(HomeUiState(showGameMenu = true))

        handler.onSelect()

        verify(exactly = 0) { actions.pressSelect(any()) }
        verify(exactly = 0) { actions.toggleGameMenu() }
    }

    @Test
    fun `select under the add-to-collection modal is swallowed without arming the double tap`() {
        val (handler, actions) = handler(HomeUiState(showAddToCollectionModal = true))

        assertTrue(handler.onSelect().handled)

        verify(exactly = 0) { actions.pressSelect(any()) }
    }
}
