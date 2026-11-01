package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.model.SteamLauncherLinkPrompt
import com.nendo.argosy.data.model.SteamMarkOption
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

@Composable
fun SteamLauncherLinkModal(
    prompt: SteamLauncherLinkPrompt,
    focusIndex: Int,
    markOptions: List<SteamMarkOption>,
    onMarkAsInstalled: (launcherPackage: String) -> Unit,
    onDismiss: () -> Unit
) {
    Modal(
        title = prompt.title.uppercase(),
        onDismiss = onDismiss
    ) {
        val listState = rememberLazyListState()
        FocusedScroll(listState = listState, focusedIndex = focusIndex)

        LazyColumn(state = listState) {
            itemsIndexed(markOptions, key = { _, option -> option.launcherPackage }) { index, option ->
                SteamLauncherLinkRow(
                    label = stringResource(R.string.ui_steam_location_mark_installed),
                    subtitle = stringResource(
                        R.string.ui_steam_location_managed_by,
                        option.displayName
                    ),
                    isFocused = focusIndex == index,
                    onClick = { onMarkAsInstalled(option.launcherPackage) }
                )
            }
        }
    }
}

@Composable
private fun SteamLauncherLinkRow(
    label: String,
    subtitle: String?,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val focusAccent = LocalArgosyTheme.current.focusAccent
    val contentColor = if (isFocused) lerp(focusAccent, Color.White, 0.45f)
        else MaterialTheme.colorScheme.onSurface
    val subtitleColor = if (isFocused) lerp(focusAccent, Color.White, 0.45f).copy(alpha = 0.8f)
        else MaterialTheme.colorScheme.onSurfaceVariant
    val background = if (isFocused) focusAccent.copy(alpha = 0.15f) else Color.Transparent

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(background, RoundedCornerShape(Dimens.radiusMd))
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.radiusLg, vertical = Dimens.spacingSm)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor
            )
        }
    }
}
