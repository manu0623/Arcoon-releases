package com.nendo.argosy.ui.dualscreen

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.FilterVintage
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.R
import com.nendo.argosy.ui.cocoon.CocoonHomeStore
import com.nendo.argosy.ui.cocoon.cocoonHandleIconDp
import com.nendo.argosy.ui.components.AppDrawerEntry
import com.nendo.argosy.ui.components.CompanionAppDrawer
import com.nendo.argosy.ui.components.ScreenNumberChip
import com.nendo.argosy.ui.components.SystemStatusBar
import com.nendo.argosy.ui.theme.CocoonSurface
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * The in-game screen's chrome: which screen this is and the game's achievements at the top start,
 * the clock with its network and battery indicators at the top end, and the session's controls
 * along the bottom.
 *
 * [lit] is the settle stage's inverse: 1 while the screen is lit, 0 once it has settled, the whole
 * chrome following it out and leaving the resting screen without a bright fixed element.
 *
 * The bottom controls are touch targets while the session runs, as the dock's are: the emulator on
 * the other screen owns the pad for the length of the game.
 *
 * The Home button is the launcher's one on-screen home control. Home Symbol in the game's settings
 * owns it: Size its glyph, Clarity the backing it sits on, Position which end of the row it joins,
 * and its hide row takes it off the screen for that core.
 */
@Composable
fun BoxScope.InGameChrome(
    lit: Float,
    earnedAchievements: Int = 0,
    achievements: Int = 0
) {
    val alpha = lit.coerceIn(0f, 1f)
    if (alpha <= 0f) return
    val manager = DualScreenManagerHolder.instance
    val context = LocalContext.current
    // A game screen can be the first surface in this process, so the saved Home Symbol values are
    // read here rather than inherited from a board that never composed.
    LaunchedEffect(Unit) { CocoonHomeStore.ensureLoaded(context) }
    val ownDisplay = LocalView.current.display?.displayId
    val screenNumber = remember(ownDisplay) {
        manager?.focusableDisplays()?.firstOrNull { it.displayId == ownDisplay }?.number
    }
    val hiddenHomeFlow = remember { kotlinx.coroutines.flow.MutableStateFlow(false) }
    val hideHome by (manager?.hideInGameChromeHome ?: hiddenHomeFlow).collectAsState()
    // Home Symbol ▸ Position picks the end of the row the button joins, and Size and Clarity are
    // the glyph and the chip behind it.
    val homeAtEnd = CocoonHomeStore.handleX >= 0.5f
    val homeGlyph = cocoonHandleIconDp().dp
    val homeChipAlpha = CocoonHomeStore.handleClarity
    val onHome: () -> Unit = {
        val quickActions = manager?.sessionQuickActions
        if (quickActions != null) quickActions.suspendSession() else manager?.quitSession()
    }
    var drawerOpen by remember { mutableStateOf(false) }
    var drawerApps by remember { mutableStateOf(emptyList<AppDrawerEntry>()) }
    LaunchedEffect(drawerOpen) {
        if (!drawerOpen || drawerApps.isNotEmpty()) return@LaunchedEffect
        drawerApps = manager?.installedAppLabels()
            .orEmpty()
            .map { (packageName, label) -> AppDrawerEntry(packageName, label) }
    }
    Row(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(Dimens.spacingLg)
            .graphicsLayer { this.alpha = alpha },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        if (screenNumber != null) {
            ScreenNumberChip(screenNumber)
        }
        if (achievements > 0) {
            AchievementChromeChip(earned = earnedAchievements, total = achievements)
        }
    }
    SystemStatusBar(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(Dimens.spacingLg)
            .graphicsLayer { this.alpha = alpha },
        contentColor = CocoonSurface.TextPrimary
    )
    Row(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingXl, vertical = Dimens.spacingLg)
            .graphicsLayer { this.alpha = alpha },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg)
        ) {
            if (!hideHome && !homeAtEnd) {
                ChromeButton(
                    icon = Icons.Rounded.Home,
                    labelRes = R.string.dual_ingame_chrome_home,
                    glyphSize = homeGlyph,
                    chipAlpha = homeChipAlpha,
                    onClick = onHome
                )
            }
            ChromeButton(Icons.Rounded.Apps, R.string.dual_ingame_chrome_apps) {
                drawerOpen = true
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg)
        ) {
            if (!hideHome && homeAtEnd) {
                ChromeButton(
                    icon = Icons.Rounded.Home,
                    labelRes = R.string.dual_ingame_chrome_home,
                    glyphSize = homeGlyph,
                    chipAlpha = homeChipAlpha,
                    onClick = onHome
                )
            }
            ChromeButton(Icons.Rounded.FilterVintage, R.string.dual_ingame_chrome_tools) {
                manager?.sessionQuickActions?.openGameSettings()
            }
            ChromeButton(Icons.Filled.EmojiEvents, R.string.dual_ingame_chrome_trophies) {
                manager?.sessionQuickActions?.openAchievements()
            }
        }
    }
    if (drawerOpen && manager != null) {
        CompanionAppDrawer(
            apps = drawerApps,
            onLaunch = { packageName ->
                drawerOpen = false
                manager.launchCompanionApp(packageName)
            },
            onLongPress = null,
            onDismiss = { drawerOpen = false }
        )
    }
}

/**
 * One bottom control. The Home button is the same shape as the rest, with the glyph and the chip
 * coming from Home Symbol so the player's size and clarity are honoured on the one control they can
 * style.
 */
@Composable
private fun ChromeButton(
    icon: ImageVector,
    @StringRes labelRes: Int,
    glyphSize: Dp = Dimens.iconXl - Dimens.spacingSm * 2,
    chipAlpha: Float = 1f,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(Dimens.iconXl)
            .clip(CircleShape)
            .background(CocoonSurface.Chip.copy(alpha = chipAlpha))
            .clickableNoFocus(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(labelRes),
            tint = CocoonSurface.TextPrimary,
            modifier = Modifier.size(glyphSize)
        )
    }
}

@Composable
private fun AchievementChromeChip(earned: Int, total: Int) {
    Row(
        modifier = Modifier
            .background(CocoonSurface.Chip, RoundedCornerShape(percent = 50))
            .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.EmojiEvents,
            contentDescription = stringResource(R.string.dual_dashboard_rail_trophies),
            tint = CocoonSurface.TextPrimary,
            modifier = Modifier.size(Dimens.iconXs)
        )
        Spacer(modifier = Modifier.width(Dimens.spacingXs))
        Text(
            text = "$earned/$total",
            style = MaterialTheme.typography.labelMedium,
            color = CocoonSurface.TextPrimary
        )
    }
}
