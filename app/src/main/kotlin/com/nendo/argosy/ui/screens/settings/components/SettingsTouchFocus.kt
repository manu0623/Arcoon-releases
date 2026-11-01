package com.nendo.argosy.ui.screens.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import com.nendo.argosy.ui.util.observeTap

/**
 * Touch state the settings screen hands to its section lists. [onRowTap] receives the focus
 * index of a tapped row. [onExitTouchMode] ends touch mode ahead of a tap that asks the list to
 * scroll, such as a section rail entry. The default is inert, for lists rendered outside the
 * settings screen.
 */
@Immutable
data class SettingsTouchFocus(
    val isTouchMode: Boolean = false,
    val onRowTap: (Int) -> Unit = {},
    val onExitTouchMode: () -> Unit = {}
)

val LocalSettingsTouchFocus = compositionLocalOf { SettingsTouchFocus() }

/**
 * Moves settings focus to [focusIndex] when the row is tapped, without taking the tap from the
 * row's own controls. A negative [focusIndex] marks a row that takes no focus.
 */
fun Modifier.settingsRowTap(focusIndex: Int): Modifier = composed {
    val onRowTap = LocalSettingsTouchFocus.current.onRowTap
    if (focusIndex < 0) Modifier else Modifier.observeTap { onRowTap(focusIndex) }
}

/**
 * Stacks [content] the way a lazy list item stacks its children, inside a [settingsRowTap] area.
 */
@Composable
fun SettingsRowTapArea(focusIndex: Int, content: @Composable () -> Unit) {
    Column(modifier = Modifier.settingsRowTap(focusIndex)) {
        content()
    }
}

/**
 * A lazy list item whose tap moves settings focus to [focusIndex], per [settingsRowTap].
 */
fun LazyListScope.settingsRowItem(
    key: Any? = null,
    focusIndex: Int,
    content: @Composable LazyItemScope.() -> Unit
) {
    item(key = key) {
        val itemScope = this
        SettingsRowTapArea(focusIndex) { itemScope.content() }
    }
}

fun <T> LazyListScope.settingsRowItems(
    items: List<T>,
    key: (T) -> Any,
    focusIndexOf: (T) -> Int,
    itemContent: @Composable LazyItemScope.(T) -> Unit
) {
    items(count = items.size, key = { key(items[it]) }) { index ->
        val item = items[index]
        val itemScope = this
        SettingsRowTapArea(focusIndexOf(item)) { itemScope.itemContent(item) }
    }
}
