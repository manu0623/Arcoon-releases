package com.nendo.argosy.ui.screens.gamedetail.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nendo.argosy.R
import com.nendo.argosy.ui.screens.gamedetail.GameDownloadStatus
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.common.rememberResolvedCoverPath
import com.nendo.argosy.ui.screens.gamedetail.GameDetailUi

/** Layout A ("Header & tabs"): the actions row and the tab bar that sit above the open section. */

val TAB_ACTION_ITEMS: List<MenuItem> = listOf(
    MenuItem.Play, MenuItem.Saves, MenuItem.Favorite,
    MenuItem.Privacy, MenuItem.PerGameSettings, MenuItem.Options
)

fun MenuItem.isTabAction(): Boolean = this in TAB_ACTION_ITEMS

private val PanelColor = Color(0xFF181818)
private val FocusColor = Color(0xFFF2F2F2)
private val InkColor = Color(0xFF0B0B0C)
private val TextColor = Color(0xFFF2F2F2)
private val MutedColor = Color(0xFFA7A7AE)

@Composable
fun GameDetailTabsBar(
    game: GameDetailUi,
    layoutState: MenuLayoutState,
    displayState: GameDetailMenuState,
    downloadProgress: State<Float>,
    focusedItem: MenuItem?,
    activeTab: MenuItem,
    onItemClick: (MenuItem) -> Unit,
    onFocusChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val visible = menuLayout.visibleItems(layoutState)
    val actions = visible.filter { it.isTabAction() }
    val tabs = visible.filter { !it.isTabAction() }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Header: box art, platform and status chips, title, quick facts, then the actions.
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
            AsyncImage(
                model = rememberFileImageModel(rememberResolvedCoverPath(game.id, game.coverPath)),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(96.dp)
                    .height(128.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E1E22))
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Chip(text = game.platformName.uppercase(), bg = Color(0xFF26262B), fg = Color(0xFFC9C9CF))
                    if (displayState.downloadStatus == GameDownloadStatus.DOWNLOADED) {
                        Chip(
                            text = stringResource(R.string.cocoon_details_installed).uppercase(),
                            bg = Color(0xFF1F3A2A),
                            fg = Color(0xFF8FE0B0)
                        )
                    }
                }
                Text(
                    text = game.title,
                    color = TextColor,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 26.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val facts = listOfNotNull(
                    game.playTimeMinutes.takeIf { it > 0 }?.let {
                        stringResource(R.string.cocoon_details_played, formatPlayTime(it))
                    },
                    game.releaseYear?.toString(),
                    game.genre
                )
                if (facts.isNotEmpty()) {
                    Text(
                        text = facts.joinToString("  ·  "),
                        color = MutedColor,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 4.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    actions.forEach { item ->
                        val focused = focusedItem == item
                        val click = {
                            onFocusChange(menuLayout.focusIndexOf(item, layoutState))
                            onItemClick(item)
                        }
                        if (item == MenuItem.Play) {
                            ActionPill(
                                label = playLabel(displayState, downloadProgress),
                                icon = Icons.Filled.PlayArrow,
                                focused = focused,
                                primary = true,
                                onClick = click
                            )
                        } else {
                            val (_, icon) = actionLabel(item, displayState)
                            RoundAction(icon = icon, focused = focused, onClick = click)
                        }
                    }
                }
            }
        }

        // Tabs, switched with L1 / R1.
        val tabListState = rememberLazyListState()
        val focusedTabIndex = tabs.indexOfFirst { it == focusedItem }
        LaunchedEffect(focusedTabIndex, tabs.size) {
            if (focusedTabIndex >= 0) tabListState.animateScrollToItem(focusedTabIndex)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShoulderBadge("L1")
            LazyRow(
                state = tabListState,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(PanelColor),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(tabs, key = { _, item -> item.toString() }) { _, item ->
                    TabChip(
                        label = tabLabel(item),
                        active = item == activeTab,
                        focused = item == focusedItem,
                        onClick = { onFocusChange(menuLayout.focusIndexOf(item, layoutState)) }
                    )
                }
            }
            ShoulderBadge("R1")
        }
    }
}

@Composable
private fun Chip(text: String, bg: Color, fg: Color) {
    Text(
        text = text,
        color = fg,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
private fun ShoulderBadge(text: String) {
    Text(
        text = text,
        color = MutedColor,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .border(1.dp, Color(0xFF3A3A40), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    )
}

@Composable
private fun RoundAction(icon: ImageVector, focused: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (focused) FocusColor else PanelColor, label = "roundBg")
    Box(
        modifier = Modifier
            .border(2.dp, if (focused) FocusColor.copy(alpha = 0.55f) else Color.Transparent, RoundedCornerShape(50))
            .padding(3.dp)
            .size(40.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickableNoFocus(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = if (focused) InkColor else TextColor, modifier = Modifier.size(18.dp))
    }
}

private fun formatPlayTime(minutes: Int): String =
    if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${minutes % 60}m"

/** Layout A's Overview tab: a short description, the facts in the Y-menu panel, a screenshot strip. */
@Composable
fun GameDetailTabsOverview(game: GameDetailUi, onScreenshotTap: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val description = game.description?.takeIf { it.isNotBlank() }
        if (description != null) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = description,
                    color = Color(0xFFD6D6DB),
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShoulderBadge("R1")
                    Text(stringResource(R.string.cocoon_details_full_description), color = MutedColor, fontSize = 11.sp)
                }
            }
        }

        val facts = listOfNotNull(
            game.developer?.let { R.string.cocoon_details_fact_developer to it },
            game.publisher?.takeIf { it != game.developer }?.let { R.string.cocoon_details_fact_publisher to it },
            game.releaseYear?.let { R.string.cocoon_details_fact_released to it.toString() },
            game.genre?.let { R.string.cocoon_details_fact_genre to it },
            game.players?.let { R.string.cocoon_details_fact_players to it }
        )
        if (facts.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(PanelColor)
                    .padding(horizontal = 14.dp)
            ) {
                facts.forEachIndexed { index, (label, value) ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                        Text(stringResource(label), color = MutedColor, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text(
                            value,
                            color = TextColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (index < facts.lastIndex) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.07f)))
                    }
                }
            }
        }

        if (game.screenshots.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                game.screenshots.take(3).forEachIndexed { index, shot ->
                    AsyncImage(
                        model = shot.cachedPath?.let { java.io.File(it) } ?: shot.remoteUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .height(78.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E1E22))
                            .clickableNoFocus(onClick = { onScreenshotTap(index) })
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionPill(
    label: String,
    icon: ImageVector,
    focused: Boolean,
    primary: Boolean,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(if (focused) FocusColor else PanelColor, label = "pillBg")
    val fg by animateColorAsState(if (focused) InkColor else TextColor, label = "pillFg")
    Box(
        modifier = Modifier
            .then(
                if (focused) Modifier.border(2.dp, FocusColor.copy(alpha = 0.55f), RoundedCornerShape(50))
                    .padding(3.dp) else Modifier.padding(3.dp)
            )
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = if (primary) 22.dp else 14.dp, vertical = if (primary) 11.dp else 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(if (primary) 20.dp else 16.dp))
            Text(
                text = label,
                color = fg,
                fontSize = if (primary) 16.sp else 13.sp,
                fontWeight = if (primary || focused) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TabChip(label: String, active: Boolean, focused: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        when {
            focused -> FocusColor
            active -> Color(0xFF2E2E33)
            else -> Color.Transparent
        },
        label = "tabBg"
    )
    val fg = when {
        focused -> InkColor
        active -> TextColor
        else -> MutedColor
    }
    Text(
        text = label,
        color = fg,
        fontSize = 13.sp,
        fontWeight = if (focused || active) FontWeight.Bold else FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun playLabel(display: GameDetailMenuState, progress: State<Float>): String = when (display.downloadStatus) {
    GameDownloadStatus.NO_FILE -> stringResource(R.string.gamedetail_menu_play_button_no_file)
    GameDownloadStatus.EXTRACTING -> stringResource(R.string.gamedetail_menu_play_button_extracting)
    GameDownloadStatus.DOWNLOADING -> if (display.isAwaitingServer) {
        stringResource(R.string.gamedetail_menu_play_button_waiting_for_server)
    } else {
        stringResource(R.string.gamedetail_menu_play_button_progress_percent, (progress.value * 100).toInt())
    }
    GameDownloadStatus.QUEUED -> stringResource(R.string.gamedetail_menu_play_button_queued)
    GameDownloadStatus.WAITING_FOR_STORAGE -> stringResource(R.string.gamedetail_menu_play_button_no_space)
    GameDownloadStatus.PAUSED -> stringResource(R.string.gamedetail_menu_play_button_resume)
    GameDownloadStatus.FAILED -> stringResource(R.string.gamedetail_menu_play_button_retry)
    GameDownloadStatus.DOWNLOADED -> stringResource(R.string.gamedetail_menu_play_button_play)
    GameDownloadStatus.NEEDS_INSTALL -> stringResource(R.string.gamedetail_menu_play_button_install)
    GameDownloadStatus.NOT_DOWNLOADED -> stringResource(R.string.gamedetail_menu_play_button_download)
}

@Composable
private fun actionLabel(item: MenuItem, display: GameDetailMenuState): Pair<String, ImageVector> = when (item) {
    MenuItem.Saves -> if (display.isSyncingSaves) {
        stringResource(R.string.gamedetail_menu_saves_syncing) to Icons.Filled.Sync
    } else {
        stringResource(R.string.cocoon_details_action_saves) to Icons.Filled.Save
    }
    MenuItem.Favorite -> stringResource(R.string.gamedetail_menu_favorite_label) to
        (if (display.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder)
    MenuItem.Privacy -> if (display.isPrivate) {
        stringResource(R.string.gamedetail_menu_privacy_private) to Icons.Outlined.VisibilityOff
    } else {
        stringResource(R.string.gamedetail_menu_privacy_public) to Icons.Outlined.Visibility
    }
    MenuItem.PerGameSettings -> stringResource(R.string.cocoon_details_action_settings) to Icons.Filled.Settings
    MenuItem.Options -> stringResource(R.string.cocoon_details_action_more) to Icons.Filled.MoreHoriz
    else -> tabLabel(item) to Icons.Filled.MoreHoriz
}

@Composable
private fun tabLabel(item: MenuItem): String = when (item) {
    MenuItem.Details -> stringResource(R.string.cocoon_details_tab_overview)
    MenuItem.Description -> stringResource(R.string.gamedetail_menu_description)
    MenuItem.Screenshots -> stringResource(R.string.gamedetail_menu_screenshots)
    MenuItem.Documents -> stringResource(R.string.gamedetail_menu_documents)
    MenuItem.Reviews -> stringResource(R.string.gamedetail_menu_reviews)
    MenuItem.Achievements -> stringResource(R.string.gamedetail_menu_achievements)
    MenuItem.RelatedGames -> stringResource(R.string.gamedetail_menu_related_games)
    else -> ""
}


/** Layout B ("Side menu"): cover and title on top, then every action and section as one labeled list. */
@Composable
fun GameDetailSideMenu(
    game: GameDetailUi,
    layoutState: MenuLayoutState,
    displayState: GameDetailMenuState,
    downloadProgress: State<Float>,
    focusedItem: MenuItem?,
    activeTab: MenuItem,
    onItemClick: (MenuItem) -> Unit,
    onFocusChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val visible = menuLayout.visibleItems(layoutState)
    val listState = rememberLazyListState()
    LaunchedEffect(focusedItem, visible) {
        val position = visible.indexOf(focusedItem)
        if (position < 0) return@LaunchedEffect
        val info = listState.layoutInfo
        val shown = info.visibleItemsInfo.firstOrNull { it.index == position }
        val fully = shown != null && shown.offset >= 0 && shown.offset + shown.size <= info.viewportEndOffset
        if (!fully) listState.animateScrollToItem(position)
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AsyncImage(
                model = rememberFileImageModel(rememberResolvedCoverPath(game.id, game.coverPath)),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(44.dp)
                    .height(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E1E22))
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = game.title,
                    color = TextColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.sp
                )
                Text(text = game.platformName, color = MutedColor, fontSize = 11.sp, maxLines = 1)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(PanelColor)
        ) {
            itemsIndexed(visible, key = { _, item -> item.key }) { index, item ->
                val focused = item == focusedItem
                val active = !focused && item == activeTab
                val nextFocused = visible.getOrNull(index + 1) == focusedItem
                val (label, icon) = when (item) {
                    MenuItem.Play -> playLabel(displayState, downloadProgress) to Icons.Filled.PlayArrow
                    else -> if (item.isTabAction()) actionLabel(item, displayState) else tabLabel(item) to tabIcon(item)
                }
                SideRow(
                    label = label,
                    icon = icon,
                    focused = focused,
                    active = active,
                    onClick = {
                        onFocusChange(menuLayout.focusIndexOf(item, layoutState))
                        if (item.isTabAction()) onItemClick(item)
                    }
                )
                val last = index == visible.lastIndex
                if (!last && !focused && !nextFocused) {
                    val groupBreak = item == MenuItem.Options
                    Box(
                        modifier = Modifier
                            .padding(horizontal = if (groupBreak) 0.dp else 12.dp)
                            .fillMaxWidth()
                            .height(if (groupBreak) 6.dp else 1.dp)
                            .background(if (groupBreak) Color(0xFF0B0B0C) else Color.White.copy(alpha = 0.07f))
                    )
                } else if (!last) {
                    Spacer(modifier = Modifier.height(if (item == MenuItem.Options) 6.dp else 1.dp))
                }
            }
        }
    }
}

@Composable
private fun SideRow(label: String, icon: ImageVector, focused: Boolean, active: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (focused) FocusColor else Color.Transparent, label = "sideBg")
    val fg = when {
        focused -> InkColor
        active -> TextColor
        else -> Color(0xFFD6D6DB)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        Text(
            text = label,
            color = fg,
            fontSize = 13.sp,
            fontWeight = if (focused || active) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (active) {
            Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(TextColor))
        }
    }
}

private fun tabIcon(item: MenuItem): ImageVector = when (item) {
    MenuItem.Details -> Icons.Filled.Info
    MenuItem.Description -> Icons.Filled.Description
    MenuItem.Screenshots -> Icons.Filled.Image
    MenuItem.Documents -> Icons.AutoMirrored.Filled.MenuBook
    MenuItem.Reviews -> Icons.Filled.ThumbUp
    MenuItem.Achievements -> Icons.Filled.EmojiEvents
    MenuItem.RelatedGames -> Icons.Filled.Gamepad
    else -> Icons.Filled.MoreHoriz
}


/** Layout C ("Action tiles"): title, big tiles for the actions, frosted chips for the sections. */
private val FrostColor = Color.White.copy(alpha = 0.08f)
private val FrostEdge = Color.White.copy(alpha = 0.12f)

@Composable
fun GameDetailTilesBar(
    game: GameDetailUi,
    layoutState: MenuLayoutState,
    displayState: GameDetailMenuState,
    downloadProgress: State<Float>,
    focusedItem: MenuItem?,
    activeTab: MenuItem,
    onItemClick: (MenuItem) -> Unit,
    onFocusChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val visible = menuLayout.visibleItems(layoutState)
    val actions = visible.filter { it.isTabAction() }
    val tabs = visible.filter { !it.isTabAction() }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Text(
                text = game.platformName.uppercase(),
                color = Color(0xFFC9C9CF),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                maxLines = 1
            )
            Text(
                text = game.title,
                color = TextColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            actions.forEach { item ->
                val focused = focusedItem == item
                val primary = item == MenuItem.Play
                val (label, icon) = if (primary) {
                    playLabel(displayState, downloadProgress) to Icons.Filled.PlayArrow
                } else {
                    actionLabel(item, displayState)
                }
                ActionTile(
                    label = label,
                    icon = icon,
                    focused = focused,
                    primary = primary,
                    onClick = {
                        onFocusChange(menuLayout.focusIndexOf(item, layoutState))
                        onItemClick(item)
                    }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { item ->
                val focused = item == focusedItem
                val active = item == activeTab
                val bg by animateColorAsState(
                    when {
                        focused -> FocusColor
                        active -> Color.White.copy(alpha = 0.22f)
                        else -> FrostColor
                    },
                    label = "tileTabBg"
                )
                Text(
                    text = tabLabel(item),
                    color = if (focused) InkColor else TextColor,
                    fontSize = 12.sp,
                    fontWeight = if (focused || active) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(bg)
                        .border(1.dp, if (focused) Color.Transparent else FrostEdge, RoundedCornerShape(50))
                        .clickableNoFocus(onClick = { onFocusChange(menuLayout.focusIndexOf(item, layoutState)) })
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun ActionTile(
    label: String,
    icon: ImageVector,
    focused: Boolean,
    primary: Boolean,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        when {
            primary -> FocusColor
            focused -> Color.White.copy(alpha = 0.22f)
            else -> FrostColor
        },
        label = "tileBg"
    )
    val fg = if (primary) InkColor else TextColor
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .border(3.dp, if (focused) FocusColor else Color.Transparent, RoundedCornerShape(22.dp))
            .padding(5.dp)
    ) {
        Column(
            modifier = Modifier
                .width(if (primary) 130.dp else 92.dp)
                .height(100.dp)
                .clip(shape)
                .background(bg)
                .border(1.dp, if (primary) Color.Transparent else FrostEdge, shape)
                .clickableNoFocus(onClick = onClick)
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(if (primary) 26.dp else 22.dp))
            Text(
                text = label,
                color = fg,
                fontSize = if (primary) 16.sp else 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


/**
 * Layout D ("Split screens"): the top screen reads (its info panel stays open), the bottom screen
 * only holds big buttons. Rows: Play and one tile, then up to three tiles, then the small pills.
 */
private val SPLIT_TILES = listOf(
    MenuItem.Saves, MenuItem.Achievements, MenuItem.Screenshots,
    MenuItem.PerGameSettings, MenuItem.Reviews, MenuItem.Documents
)
private val SPLIT_PILLS = listOf(MenuItem.Favorite, MenuItem.Privacy, MenuItem.Options)

fun splitRows(state: MenuLayoutState): List<List<MenuItem>> {
    val visible = menuLayout.visibleItems(state)
    val tiles = SPLIT_TILES.filter { it in visible }.take(4)
    val rows = mutableListOf<List<MenuItem>>()
    rows += listOf(MenuItem.Play) + tiles.take(1)
    if (tiles.size > 1) rows += tiles.drop(1)
    val pills = SPLIT_PILLS.filter { it in visible }
    if (pills.isNotEmpty()) rows += pills
    return rows
}

private fun splitWeight(item: MenuItem): Float = if (item == MenuItem.Play) 2f else 1f

private fun splitCenters(row: List<MenuItem>): List<Float> {
    val total = row.sumOf { splitWeight(it).toDouble() }.toFloat()
    var start = 0f
    return row.map { item ->
        val w = splitWeight(item)
        val center = (start + w / 2f) / total
        start += w
        center
    }
}

/** The item the d-pad lands on from [current] in the split grid, moving [dRow] rows or [dCol] columns. */
fun splitNeighbor(state: MenuLayoutState, current: MenuItem?, dRow: Int, dCol: Int): MenuItem {
    val rows = splitRows(state)
    val r = rows.indexOfFirst { current in it }
    if (r < 0) return MenuItem.Play
    val c = rows[r].indexOf(current)
    if (dCol != 0) return rows[r][(c + dCol).coerceIn(0, rows[r].lastIndex)]
    val target = (r + dRow).coerceIn(0, rows.lastIndex)
    if (target == r) return rows[r][c]
    val from = splitCenters(rows[r])[c]
    val centers = splitCenters(rows[target])
    val best = centers.indices.minByOrNull { kotlin.math.abs(centers[it] - from) } ?: 0
    return rows[target][best]
}

@Composable
fun GameDetailSplitPanel(
    game: GameDetailUi,
    layoutState: MenuLayoutState,
    displayState: GameDetailMenuState,
    downloadProgress: State<Float>,
    focusedItem: MenuItem?,
    onActivate: (MenuItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = splitRows(layoutState)
    val tileRows = rows.filter { row -> row.none { it in SPLIT_PILLS } }
    val pillRow = rows.firstOrNull { row -> row.any { it in SPLIT_PILLS } }

    Column(
        modifier = modifier.background(InkColor).padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = game.title,
                color = TextColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(text = stringResource(R.string.cocoon_details_split_hint), color = MutedColor, fontSize = 11.sp, maxLines = 1)
        }

        tileRows.forEach { row ->
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                row.forEach { item ->
                    val primary = item == MenuItem.Play
                    val (label, icon) = when {
                        primary -> playLabel(displayState, downloadProgress) to Icons.Filled.PlayArrow
                        item.isTabAction() -> actionLabel(item, displayState)
                        else -> tabLabel(item) to tabIcon(item)
                    }
                    SplitTile(
                        label = label,
                        icon = icon,
                        primary = primary,
                        focused = item == focusedItem,
                        onClick = { onActivate(item) },
                        modifier = Modifier.weight(splitWeight(item)).fillMaxHeight()
                    )
                }
            }
        }

        if (pillRow != null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pillRow.forEach { item ->
                    val focused = item == focusedItem
                    val (label, icon) = actionLabel(item, displayState)
                    val bg by animateColorAsState(if (focused) FocusColor else PanelColor, label = "splitPill")
                    val fg = if (focused) InkColor else TextColor
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(50))
                            .background(bg)
                            .clickableNoFocus(onClick = { onActivate(item) }),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = label,
                            color = fg,
                            fontSize = 13.sp,
                            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SplitTile(
    label: String,
    icon: ImageVector,
    primary: Boolean,
    focused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg by animateColorAsState(
        when {
            primary -> FocusColor
            focused -> Color(0xFF2E2E33)
            else -> PanelColor
        },
        label = "splitTile"
    )
    val fg = if (primary) InkColor else TextColor
    Box(
        modifier = modifier
            .border(3.dp, if (focused) FocusColor else Color.Transparent, RoundedCornerShape(24.dp))
            .padding(6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickableNoFocus(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (primary) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(30.dp))
                Text(text = label, color = fg, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(28.dp))
                Text(
                    text = label,
                    color = fg,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
