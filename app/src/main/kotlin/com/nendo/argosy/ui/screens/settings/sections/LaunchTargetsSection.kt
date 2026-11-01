package com.nendo.argosy.ui.screens.settings.sections

import android.content.Context
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.ScreenRole
import com.nendo.argosy.hardware.DisplayBadgeSize
import com.nendo.argosy.ui.coil.AppIconData
import com.nendo.argosy.ui.components.EnumPickerModal
import com.nendo.argosy.ui.components.PlatformIconAssets
import com.nendo.argosy.ui.components.PreferenceResetButton
import com.nendo.argosy.ui.components.ScreenNumberChip
import com.nendo.argosy.ui.components.preferenceContentColor
import com.nendo.argosy.ui.components.preferenceModifier
import com.nendo.argosy.ui.components.preferenceSecondaryColor
import com.nendo.argosy.ui.primitives.RuledSectionHeader
import com.nendo.argosy.ui.screens.settings.LaunchTargetAppEntry
import com.nendo.argosy.ui.screens.settings.LaunchTargetBlock
import com.nendo.argosy.ui.screens.settings.LaunchTargetEntry
import com.nendo.argosy.ui.screens.settings.LaunchTargetPlatformEntry
import com.nendo.argosy.ui.screens.settings.LaunchTargetScreen
import com.nendo.argosy.ui.screens.settings.LaunchTargetsRow
import com.nendo.argosy.ui.screens.settings.LaunchTargetsState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.launchTargetsLayout
import com.nendo.argosy.ui.screens.settings.pickerSelection
import com.nendo.argosy.ui.screens.settings.screenRole
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.Motion
import kotlinx.coroutines.flow.drop

@Composable
fun LaunchTargetsSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val state = uiState.launchTargets
    val context = LocalContext.current
    val layout = remember(state) { launchTargetsLayout(state) }
    val hereDisplayId = remember(context) { ContextCompat.getDisplayOrDefault(context).displayId }
    val sections = remember(layout, context) {
        layout.sections.mapIndexed { index, section ->
            section.copy(name = layout.groups.getOrNull(index)?.let { railLabel(context, it.screen) })
        }
    }

    DisposableEffect(Unit) {
        val manager = DualScreenManagerHolder.instance
        manager?.showScreenNumbers(DisplayBadgeSize.SMALL)
        onDispose { manager?.hideScreenNumbers() }
    }
    LaunchedEffect(Unit) {
        DualScreenManagerHolder.instance?.isRolesSwapped?.drop(1)?.collect {
            viewModel.reloadLaunchTargetEntries()
        }
    }

    SectionPaneLayout(
        items = layout.rows,
        sections = sections,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { layout.listIndexOf(it) },
        itemKey = { it.key },
        isNavItem = { it is LaunchTargetsRow.Gap },
        isHeader = { it is LaunchTargetsRow.Heading },
        onSectionTap = { section ->
            if (section.focusEndIndex >= section.focusStartIndex) viewModel.setFocusIndex(section.focusStartIndex)
        },
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.listGap),
        headerLock = true
    ) { row ->
        when (row) {
            LaunchTargetsRow.Intro -> Text(
                text = stringResource(R.string.settings_launch_targets_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            is LaunchTargetsRow.Gap -> Spacer(modifier = Modifier.height(Dimens.spacingMd))
            is LaunchTargetsRow.Heading -> ScreenHeading(row.screen, isHere = row.screen.displayId == hereDisplayId)
            is LaunchTargetsRow.BlockLabel -> BlockLabel(row.block, row.count)
            is LaunchTargetsRow.Loading -> StatusLine(stringResource(R.string.settings_launch_targets_loading_apps))
            is LaunchTargetsRow.Empty -> StatusLine(stringResource(R.string.settings_launch_targets_empty))
            is LaunchTargetsRow.Entry -> {
                val focusIndex = layout.focusIndexOf(row.entry.key)
                EntryRow(
                    entry = row.entry,
                    isFocused = uiState.focusedIndex == focusIndex,
                    onClick = { viewModel.openLaunchTargetPickerAt(focusIndex) },
                    onReset = {
                        viewModel.setFocusIndex(focusIndex)
                        viewModel.resetLaunchTarget(row.entry.key)
                    }
                )
            }
        }
    }

    state.pickerEntry?.let { entry ->
        val options = remember(state, entry, context) { pickerOptions(context, state, entry) }
        EnumPickerModal(
            title = entry.label,
            options = options,
            selectedIndex = state.pickerSelection(entry),
            onSelect = { viewModel.pickLaunchTarget(it) },
            onDismiss = { viewModel.closeLaunchTargetPicker() },
            visible = state.pickerOpen
        )
    }
}

@Composable
private fun roleTitle(role: ScreenRole): String = stringResource(screenRoleTitleRes(role))

private fun roleTitle(context: Context, role: ScreenRole): String = context.getString(screenRoleTitleRes(role))

private fun railLabel(context: Context, screen: LaunchTargetScreen): String =
    context.getString(R.string.settings_launch_targets_rail, roleTitle(context, screen.role), screen.number)

private fun pickerOptions(context: Context, state: LaunchTargetsState, entry: LaunchTargetEntry): List<String> =
    when (entry) {
        is LaunchTargetPlatformEntry ->
            listOf(
                context.getString(
                    R.string.settings_launch_targets_use_default_role,
                    roleTitle(context, state.platformDefaultRole)
                )
            ) + state.screens.map {
                context.getString(R.string.settings_launch_targets_option_role, roleTitle(context, it.role), it.number)
            }
        is LaunchTargetAppEntry ->
            listOf(context.getString(R.string.settings_launch_targets_use_default_screen, state.appDefaultNumber)) +
                state.screens.map {
                    context.getString(
                        R.string.settings_launch_targets_option_screen,
                        it.number,
                        roleTitle(context, it.role)
                    )
                }
    }

@Composable
private fun ScreenHeading(screen: LaunchTargetScreen, isHere: Boolean) {
    val theme = LocalArgosyTheme.current
    val role = roleTitle(screen.role)
    val description = stringResource(R.string.settings_launch_targets_heading_description, screen.number, role)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.spacingXs)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically
    ) {
        ScreenNumberChip(number = screen.number)
        Spacer(modifier = Modifier.width(Dimens.spacingSm))
        RuledSectionHeader(
            title = role,
            modifier = Modifier.weight(1f),
            trailing = {
                if (isHere) {
                    CoreTag(text = stringResource(R.string.settings_launch_targets_this_screen), color = theme.textDim)
                }
            }
        )
    }
}

@Composable
private fun BlockLabel(block: LaunchTargetBlock, count: Int) {
    val label = when (block) {
        LaunchTargetBlock.PLATFORMS -> stringResource(R.string.settings_launch_targets_platforms)
        LaunchTargetBlock.APPS -> stringResource(R.string.settings_launch_targets_apps)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = LocalArgosyTheme.current.textDim
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = LocalArgosyTheme.current.textDim
        )
    }
}

@Composable
private fun StatusLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = LocalArgosyTheme.current.textDim
    )
}

@Composable
private fun entrySubtitles(entry: LaunchTargetEntry): List<String> = when (entry) {
    is LaunchTargetPlatformEntry -> listOfNotNull(
        if (entry.isSet) {
            stringResource(R.string.settings_launch_targets_platform_set, roleTitle(entry.stored.screenRole))
        } else {
            null
        },
        if (entry.gameOverrides > 0) {
            pluralStringResource(
                R.plurals.settings_launch_targets_platform_game_overrides,
                entry.gameOverrides,
                entry.gameOverrides
            )
        } else {
            null
        }
    )
    is LaunchTargetAppEntry -> {
        val pinned = entry.pinnedScreen
        when {
            !entry.isSet -> emptyList()
            pinned == null -> listOf(stringResource(R.string.settings_launch_targets_app_missing))
            pinned.role == ScreenRole.OFF -> listOf(stringResource(R.string.settings_launch_targets_app_off, pinned.number))
            else -> listOf(stringResource(R.string.settings_launch_targets_app_set, pinned.number))
        }
    }
}

@Composable
private fun EntryRow(
    entry: LaunchTargetEntry,
    isFocused: Boolean,
    onClick: () -> Unit,
    onReset: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val subtitles = entrySubtitles(entry)
    val rowHeight = if (subtitles.isEmpty()) Dimens.menuRowHeight else Dimens.menuRowHeightLg
    Row(
        modifier = Modifier
            .animateContentSize(animationSpec = tween(Motion.durationMicro, easing = Motion.argosyEase))
            .heightIn(min = rowHeight)
            .then(preferenceModifier(isFocused, onClick = onClick)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EntryIcon(entry)
        Spacer(modifier = Modifier.width(Dimens.spacingMd))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.label,
                style = MaterialTheme.typography.titleMedium,
                color = preferenceContentColor(isFocused),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            subtitles.forEach { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = preferenceSecondaryColor(isFocused),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(Dimens.spacingSm))
        Crossfade(
            targetState = entry.isSet,
            animationSpec = tween(Motion.durationMicro, easing = Motion.argosyEase),
            label = "launch-target-tag"
        ) { isSet ->
            if (isSet) {
                CoreTag(text = stringResource(R.string.settings_launch_targets_tag_set), color = theme.focusAccent)
            } else {
                CoreTag(text = stringResource(R.string.settings_launch_targets_tag_default), color = theme.textDim)
            }
        }
        if (entry.isSet) {
            Spacer(modifier = Modifier.width(Dimens.spacingSm))
            PreferenceResetButton(onReset)
        }
    }
}

@Composable
private fun EntryIcon(entry: LaunchTargetEntry) {
    val context = LocalContext.current
    val iconModifier = Modifier.size(Dimens.iconMd).clip(RoundedCornerShape(Dimens.radiusSm))
    when (entry) {
        is LaunchTargetPlatformEntry -> {
            val uri = remember(entry.slug) { PlatformIconAssets.resolveAssetUri(context, entry.slug) }
            if (uri != null) {
                AsyncImage(model = uri, contentDescription = null, modifier = iconModifier)
            } else {
                Icon(
                    imageVector = Icons.Outlined.SportsEsports,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = iconModifier
                )
            }
        }
        is LaunchTargetAppEntry -> AsyncImage(
            model = AppIconData(entry.packageName),
            contentDescription = null,
            modifier = iconModifier
        )
    }
}
