package com.nendo.argosy.ui.cocoon

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.primitives.InputGlyph
import com.nendo.argosy.ui.primitives.cocoonBrackets
import com.nendo.argosy.ui.theme.CocoonSurface
import com.nendo.argosy.ui.util.clickableNoFocus
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Which half of the two-pane settings root holds the cursor. Left is the category list, right the
 * rows of the open category; the focused row itself stays in the settings ViewModel.
 */
object CocoonSettingsFocus {
    val paneFocused = MutableStateFlow(false)
}

/** A left-nav category of the settings root, keyed by the section tag Argosy's menu items carry. */
enum class CocoonSettingsCategory(
    val sectionKey: String,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int?,
    val icon: ImageVector,
) {
    PERSONALIZATION("launcher", R.string.cocoon_settings_personalization, R.string.cocoon_settings_personalization_sub, Icons.Filled.Palette),
    LIBRARY("library", R.string.cocoon_settings_library, null, Icons.Filled.VideoLibrary),
    GAMEPLAY("gameplay", R.string.cocoon_settings_gameplay, R.string.cocoon_settings_gameplay_sub, Icons.Filled.SportsEsports),
    INTEGRATIONS("connections", R.string.cocoon_settings_integrations, R.string.cocoon_settings_integrations_sub, Icons.Filled.People),
    SYSTEM("system", R.string.cocoon_settings_system, R.string.cocoon_settings_system_sub, Icons.Filled.Settings),
    ABOUT("about", R.string.cocoon_settings_about, null, Icons.Filled.Info);

    companion object {
        fun forKey(key: String): CocoonSettingsCategory? = entries.firstOrNull { it.sectionKey == key }
    }
}

/** "B ← Title" header used by Cocoon's settings and sub-pages. */
@Composable
fun CocoonBackHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    rightContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .background(CocoonSurface.Chip, RoundedCornerShape(50))
                .clickableNoFocus(onClick = onBack)
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InputGlyph(button = InputButton.B, size = 16.dp, tint = CocoonSurface.TextSecondary)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = CocoonSurface.TextPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            // Argosy's section names are stored in capitals; Cocoon writes them in title case.
            text = cocoonTitleCase(title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = CocoonSurface.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        rightContent?.invoke()
    }
}

internal fun cocoonTitleCase(title: String): String {
    if (title.none { it.isLowerCase() } && title.any { it.isLetter() }) {
        return title.lowercase().split(' ').joinToString(" ") { word ->
            if (word.length <= 2 && word != title.lowercase()) word else word.replaceFirstChar { it.uppercaseChar() }
        }
    }
    return title
}

/** One category in the left navigation. */
@Composable
fun CocoonNavRow(
    category: CocoonSettingsCategory,
    selected: Boolean,
    focused: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .cocoonBrackets(focused = focused, outset = 3.dp)
            .background(if (selected) CocoonSurface.Chip else Color.Transparent, RoundedCornerShape(14.dp))
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cocoon: the open category's icon sits on its own small rounded tile.
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(if (selected) Color.White.copy(alpha = 0.10f) else Color.Transparent, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                category.icon,
                contentDescription = null,
                tint = CocoonSurface.TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = androidx.compose.ui.res.stringResource(category.titleRes),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (selected) CocoonSurface.TextSecondary else CocoonSurface.TextPrimary,
                // Two lines rather than an ellipsis: a category the player cannot read the end of
                // is a category they will not find again.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (selected && category.subtitleRes != null) {
                Text(
                    text = androidx.compose.ui.res.stringResource(category.subtitleRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = CocoonSurface.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** One row in the right pane: icon, title, description, and a ▸ when it opens a page. */
@Composable
fun CocoonSettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    focused: Boolean,
    onClick: () -> Unit,
    badge: String? = null,
    isFirst: Boolean = true,
    isLast: Boolean = true,
) {
    // Cocoon groups a category's rows into one rounded panel split by hairlines.
    val big = 16.dp
    val small = 3.dp
    val shape = RoundedCornerShape(
        topStart = if (isFirst) big else small,
        topEnd = if (isFirst) big else small,
        bottomStart = if (isLast) big else small,
        bottomEnd = if (isLast) big else small
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .cocoonBrackets(focused = focused, outset = 3.dp)
            .background(if (focused) CocoonSurface.Chip.copy(alpha = 1f) else CocoonSurface.Chip, shape)
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(icon, contentDescription = null, tint = CocoonSurface.TextPrimary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = CocoonSurface.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = CocoonSurface.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (badge != null) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 6.dp)
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = CocoonSurface.TextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}
