package com.nendo.argosy.ui.screens.gamedetail

import com.nendo.argosy.ui.screens.gamedetail.components.MenuItem
import com.nendo.argosy.ui.screens.gamedetail.components.menuLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameDetailMenuLayoutTest {

    @Test
    fun `a game with a manual shows the documents row`() {
        val state = GameDetailUiState(documents = listOf(GameDocument("manual.pdf", "Manual", "manual")))

        assertTrue(state.menuLayoutState.hasDocuments)
    }

    @Test
    fun `every row resolves back to itself from its focus index when documents are present`() {
        val layout = GameDetailUiState(documents = listOf(GameDocument("manual.pdf", "Manual", "manual")))
            .menuLayoutState
            .copy(hasAchievements = true, hasRelated = true)

        listOf(MenuItem.Documents, MenuItem.Achievements, MenuItem.RelatedGames).forEach { item ->
            assertEquals(item, menuLayout.itemAtFocusIndex(menuLayout.focusIndexOf(item, layout), layout))
        }
    }
}
