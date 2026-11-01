package com.nendo.argosy.ui.screens.settings.components

import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.screens.settings.touchFocused
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SectionPaneRowFocusTest {

    private val items = listOf("header:a", "toggle", "stepper", "header:b", "cycle", "nav")
    private val layout = SettingsLayout<String, Unit>(
        allItems = items,
        isFocusable = { !it.startsWith("header") },
        visibleWhen = { _, _ -> true }
    )

    private fun byKey(): Map<Any, Int> = focusIndexByItemKey(
        items = layout.visibleItems(Unit),
        focusToListIndex = { layout.focusToListIndex(it, Unit) },
        itemKey = { it },
        isUnfocusable = { it.startsWith("header") }
    )

    @Test
    fun `every focusable row maps to the focus index the gamepad gives it`() {
        val map = byKey()

        for (row in listOf("toggle", "stepper", "cycle", "nav")) {
            assertEquals(row, layout.focusIndexOf(row, Unit), map[row])
        }
    }

    @Test
    fun `headers take no focus from a tap`() {
        val map = byKey()

        assertNull(map["header:a"])
        assertNull(map["header:b"])
    }

    @Test
    fun `a tap on a trailing unfocusable row leaves focus where it was`() {
        val withFooter = items + "footer"
        val footerLayout = SettingsLayout<String, Unit>(
            allItems = withFooter,
            isFocusable = { !it.startsWith("header") && it != "footer" },
            visibleWhen = { _, _ -> true }
        )

        val map = focusIndexByItemKey(
            items = footerLayout.visibleItems(Unit),
            focusToListIndex = { footerLayout.focusToListIndex(it, Unit) },
            itemKey = { it },
            isUnfocusable = { it.startsWith("header") }
        )

        val footerFocus = map.getValue("footer")
        val tapped = SettingsUiState(focusedIndex = 1)
            .touchFocused(footerFocus, footerLayout.maxFocusIndex(Unit))

        assertEquals(1, tapped.focusedIndex)
    }
}
