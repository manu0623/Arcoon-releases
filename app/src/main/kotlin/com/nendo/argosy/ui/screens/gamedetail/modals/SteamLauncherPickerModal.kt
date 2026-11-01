package com.nendo.argosy.ui.screens.gamedetail.modals

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.launcher.SteamLauncher
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem

@Composable
fun SteamLauncherPickerModal(
    availableLaunchers: List<SteamLauncher>,
    currentLauncherName: String?,
    currentLauncherPackage: String?,
    isAutomatic: Boolean,
    focusIndex: Int,
    onSelectLauncher: (SteamLauncher?) -> Unit,
    onDismiss: () -> Unit
) {
    Modal(
        title = stringResource(R.string.gamedetail_launcher_picker_title),
        onDismiss = onDismiss
    ) {
        val listState = rememberLazyListState()
        FocusedScroll(listState = listState, focusedIndex = focusIndex)

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            item {
                OptionItem(
                    label = stringResource(R.string.gamedetail_steam_launcher_auto),
                    subtext = if (isAutomatic && currentLauncherName != null) {
                        stringResource(R.string.gamedetail_steam_launcher_auto_uses, currentLauncherName)
                    } else null,
                    isFocused = focusIndex == 0,
                    isSelected = isAutomatic,
                    onClick = { onSelectLauncher(null) }
                )
            }
            itemsIndexed(availableLaunchers, key = { _, launcher -> launcher.packageName }) { index, launcher ->
                OptionItem(
                    label = launcher.displayName,
                    isFocused = focusIndex == index + 1,
                    isSelected = !isAutomatic && launcher.packageName == currentLauncherPackage,
                    onClick = { onSelectLauncher(launcher) }
                )
            }
        }
    }
}
