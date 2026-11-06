package com.nendo.argosy.ui.screens.gamedetail.modals

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.primitives.cocoonBrackets
import com.nendo.argosy.ui.theme.CocoonSurface
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VisibilityOff
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.GameTitle
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.screens.gamedetail.GameDetailUi
import com.nendo.argosy.ui.screens.gamedetail.MoreOptionAction
import com.nendo.argosy.ui.screens.gamedetail.MoreOptionsContext
import com.nendo.argosy.ui.screens.gamedetail.buildMoreOptions
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem

private sealed interface MoreMenuEntry {
    data class Option(
        val icon: ImageVector? = null,
        val label: String,
        val value: String? = null,
        val isDangerous: Boolean = false,
        val action: MoreOptionAction
    ) : MoreMenuEntry

    data object Divider : MoreMenuEntry
}

private fun MoreOptionAction.toMenuEntry(
    game: GameDetailUi,
    context: Context,
    launchDisplayNumber: Int?
): MoreMenuEntry.Option = when (this) {
    MoreOptionAction.LaunchOnDisplay -> MoreMenuEntry.Option(
        Icons.Default.Tv,
        context.getString(R.string.gamedetail_more_options_launch_on_display),
        value = launchDisplayNumber?.let {
            context.getString(R.string.gamedetail_more_options_launch_on_display_value, it)
        },
        action = this
    )
    MoreOptionAction.ManageSaves -> MoreMenuEntry.Option(
        Icons.Default.Save,
        context.getString(R.string.gamedetail_more_options_manage_saves),
        action = this
    )
    MoreOptionAction.RatingsStatus -> MoreMenuEntry.Option(
        Icons.Default.Star,
        context.getString(R.string.gamedetail_more_options_ratings_status),
        action = this
    )
    MoreOptionAction.ChangeSteamLauncher -> MoreMenuEntry.Option(
        label = context.getString(R.string.gamedetail_more_options_change_launcher),
        value = if (game.steamLauncherSetManually) game.steamLauncherName
            else context.getString(R.string.gamedetail_steam_launcher_auto),
        action = this
    )
    MoreOptionAction.SpeedrunSplits -> MoreMenuEntry.Option(
        Icons.Default.Timer,
        context.getString(R.string.gamedetail_more_options_speedrun_splits),
        action = this
    )
    MoreOptionAction.RefreshTitleId -> MoreMenuEntry.Option(
        Icons.Default.Tag,
        context.getString(R.string.gamedetail_more_options_title_id),
        value = game.titleId
            ?: context.getString(R.string.gamedetail_more_options_title_id_missing),
        action = this
    )
    MoreOptionAction.SelectDisc -> MoreMenuEntry.Option(
        Icons.Default.Album,
        context.getString(R.string.gamedetail_more_options_select_disc),
        action = this
    )
    MoreOptionAction.SelectVariant -> MoreMenuEntry.Option(
        Icons.Default.SwapHoriz,
        context.getString(R.string.gamedetail_more_options_select_variant),
        action = this
    )
    MoreOptionAction.ActiveVariant -> MoreMenuEntry.Option(
        Icons.Default.Layers,
        context.getString(R.string.gamedetail_more_options_active_variant),
        action = this
    )
    MoreOptionAction.Files -> MoreMenuEntry.Option(
        Icons.Default.Checklist,
        context.getString(R.string.gamedetail_more_options_files),
        action = this
    )
    MoreOptionAction.RefreshData -> MoreMenuEntry.Option(
        Icons.Default.Refresh,
        context.getString(R.string.gamedetail_more_options_refresh_data),
        action = this
    )
    MoreOptionAction.AddToCollection -> MoreMenuEntry.Option(
        Icons.Default.FolderSpecial,
        context.getString(R.string.gamedetail_more_options_add_to_collection),
        action = this
    )
    MoreOptionAction.Artwork -> MoreMenuEntry.Option(
        Icons.Default.Image,
        context.getString(R.string.gamedetail_more_options_artwork),
        action = this
    )
    MoreOptionAction.Jingle -> MoreMenuEntry.Option(
        androidx.compose.material.icons.Icons.Filled.MusicNote,
        context.getString(R.string.cocoon_jingle),
        action = this
    )
    MoreOptionAction.Delete -> MoreMenuEntry.Option(
        icon = Icons.Default.DeleteOutline,
        label = when {
            game.isAndroidApp -> context.getString(R.string.gamedetail_more_options_uninstall)
            game.isExternallyManaged -> context.getString(
                R.string.gamedetail_more_options_unlink_from_launcher,
                game.managingLauncherDisplayName ?: context.getString(
                    R.string.gamedetail_more_options_unlink_launcher_fallback
                )
            )
            else -> context.getString(R.string.gamedetail_more_options_delete_download)
        },
        isDangerous = !game.isExternallyManaged,
        action = this
    )
    MoreOptionAction.RemoveFromLibrary -> MoreMenuEntry.Option(
        icon = Icons.Default.RemoveCircleOutline,
        label = context.getString(R.string.gamedetail_more_options_remove_from_library),
        isDangerous = true,
        action = this
    )
    MoreOptionAction.ToggleHide -> MoreMenuEntry.Option(
        icon = if (game.isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
        label = if (game.isHidden) {
            context.getString(R.string.gamedetail_more_options_show)
        } else {
            context.getString(R.string.gamedetail_more_options_hide)
        },
        isDangerous = !game.isHidden,
        action = this
    )
    else -> MoreMenuEntry.Option(label = "", action = this)
}

@Composable
fun MoreOptionsModal(
    game: GameDetailUi,
    focusIndex: Int,
    isDownloaded: Boolean,
    hasVariants: Boolean = false,
    hasSiblingGroup: Boolean = false,
    updateCount: Int = 0,
    hasManageableFiles: Boolean = false,
    launchDisplayNumbers: List<Int> = emptyList(),
    launchDisplayIndex: Int = 0,
    onAction: (MoreOptionAction) -> Unit,
    onDismiss: () -> Unit
) {
    val actions = buildMoreOptions(
        MoreOptionsContext(
            isDownloaded = isDownloaded,
            isRommGame = game.isRommGame,
            isAndroidApp = game.isAndroidApp,
            isSteamGame = game.isSteamGame,
            canRemoveFromLibrary = game.canRemoveFromLibrary,
            canManageSaves = game.canManageSaves,
            canManageStates = game.canManageStates,
            isMultiDisc = game.isMultiDisc,
            hasVariants = hasVariants,
            hasSiblingGroup = hasSiblingGroup,
            hasUpdates = updateCount > 0,
            hasManageableFiles = hasManageableFiles,
            platformSlug = game.platformSlug,
            launchDisplayCount = launchDisplayNumbers.size
        )
    )

    val context = LocalContext.current
    val entries = buildList<MoreMenuEntry> {
        var dividerAdded = false
        actions.forEach { action ->
            val isTailAction = action is MoreOptionAction.Delete ||
                action is MoreOptionAction.RemoveFromLibrary ||
                action is MoreOptionAction.ToggleHide
            if (isTailAction && !dividerAdded) {
                add(MoreMenuEntry.Divider)
                dividerAdded = true
            }
            add(action.toMenuEntry(game, context, launchDisplayNumbers.getOrNull(launchDisplayIndex)))
        }
    }

    val listState = rememberLazyListState()

    val listIndex = run {
        var focus = 0
        entries.indexOfFirst { entry ->
            if (entry is MoreMenuEntry.Option) {
                if (focus == focusIndex) return@indexOfFirst true
                focus++
            }
            false
        }.coerceAtLeast(0)
    }

    FocusedScroll(listState = listState, focusedIndex = listIndex)

    // Cocoon's "Edit Game" sheet: a dark rounded panel with a B / back chip and the title, rows
    // as cards with bracket focus.
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f))
            .clickableNoFocus(onClick = onDismiss),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .widthIn(max = 620.dp)
                .fillMaxHeight(0.9f)
                .background(CocoonSurface.Panel, RoundedCornerShape(24.dp))
                .clickableNoFocus(enabled = false) {}
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                        .clickableNoFocus(onClick = onDismiss)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    com.nendo.argosy.ui.primitives.InputGlyph(
                        button = com.nendo.argosy.ui.components.InputButton.B,
                        size = 16.dp,
                        tint = CocoonSurface.TextSecondary
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(4.dp))
                    androidx.compose.material3.Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = CocoonSurface.TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(10.dp))
                androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                    androidx.compose.material3.Text(
                        text = context.getString(R.string.cocoon_edit_game),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = CocoonSurface.TextPrimary
                    )
                    androidx.compose.material3.Text(
                        text = game.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = CocoonSurface.TextSecondary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(entries) { _, entry ->
                    when (entry) {
                        is MoreMenuEntry.Divider -> androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(6.dp))
                        is MoreMenuEntry.Option -> {
                            val optionFocusIndex = run {
                                var count = 0
                                for (e in entries) {
                                    if (e === entry) break
                                    if (e is MoreMenuEntry.Option) count++
                                }
                                count
                            }
                            CocoonSheetRow(
                                entry = entry,
                                focused = focusIndex == optionFocusIndex,
                                onClick = { onAction(entry.action) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CocoonSheetRow(entry: MoreMenuEntry.Option, focused: Boolean, onClick: () -> Unit) {
    val tint = if (entry.isDangerous) androidx.compose.ui.graphics.Color(0xFFFF6B6B) else CocoonSurface.TextPrimary
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .cocoonBrackets(focused = focused, outset = 3.dp)
            .background(CocoonSurface.Chip, RoundedCornerShape(14.dp))
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        entry.icon?.let {
            androidx.compose.material3.Icon(it, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(12.dp))
        }
        androidx.compose.material3.Text(
            text = entry.label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            color = tint,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        entry.value?.let {
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
            androidx.compose.material3.Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = CocoonSurface.TextSecondary,
                maxLines = 1
            )
        }
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(6.dp))
        androidx.compose.material3.Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = CocoonSurface.TextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}
