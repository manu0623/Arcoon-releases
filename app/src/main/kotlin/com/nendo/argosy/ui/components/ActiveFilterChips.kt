package com.nendo.argosy.ui.components

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

enum class ActiveFilterKind { SORT, SEARCH, SOURCE, PLATFORM, GENRE, REGION, PLAYERS, SERIES }

/**
 * One active filter as the chip row draws it. [labelRes] wins over [text] for the value; the
 * multi-select kinds draw [count] and carry [text] only so a summary can name a lone selection.
 */
data class ActiveFilterChipUi(
    val kind: ActiveFilterKind,
    @StringRes val labelRes: Int? = null,
    val text: String? = null,
    val count: Int = 1
)

/**
 * The wording that stands in for the active filters: the lone filter's own name when exactly
 * one selection is on, the number of selections otherwise, null when nothing is active.
 */
fun List<ActiveFilterChipUi>.activeFilterSummary(
    context: Context,
    @PluralsRes countRes: Int
): String? {
    val total = sumOf { it.count }
    val lone = singleOrNull()?.takeIf { it.count == 1 }
    return when {
        total == 0 -> null
        lone != null -> lone.loneName(context)
        else -> context.resources.getQuantityString(countRes, total, total)
    }
}

private fun ActiveFilterChipUi.loneName(context: Context): String = when {
    kind == ActiveFilterKind.SEARCH -> "\"$text\""
    labelRes != null -> context.getString(labelRes)
    else -> text.orEmpty()
}

@Composable
fun ActiveFilterChipRow(
    label: String,
    chips: List<ActiveFilterChipUi>,
    modifier: Modifier = Modifier
) {
    if (chips.isEmpty()) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = LocalArgosyTheme.current.textDim
        )
        chips.forEach { chip ->
            ActiveFilterChip(chip)
        }
    }
}

@Composable
private fun ActiveFilterChip(chip: ActiveFilterChipUi) {
    val theme = LocalArgosyTheme.current
    Row(
        modifier = Modifier
            .background(theme.surfaceRaised, RoundedCornerShape(Dimens.radiusPill))
            .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = chip.kind.glyph,
            contentDescription = null,
            tint = theme.focusAccent,
            modifier = Modifier.size(Dimens.iconXs)
        )
        Text(
            text = chip.displayValue(),
            style = MaterialTheme.typography.labelMedium,
            color = theme.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ActiveFilterChipUi.displayValue(): String = when (kind) {
    ActiveFilterKind.PLATFORM,
    ActiveFilterKind.GENRE,
    ActiveFilterKind.REGION,
    ActiveFilterKind.SERIES -> count.toString()
    ActiveFilterKind.SORT,
    ActiveFilterKind.SEARCH,
    ActiveFilterKind.SOURCE,
    ActiveFilterKind.PLAYERS -> labelRes?.let { stringResource(it) } ?: text.orEmpty()
}

private val ActiveFilterKind.glyph: ImageVector
    get() = when (this) {
        ActiveFilterKind.SORT -> Icons.AutoMirrored.Filled.Sort
        ActiveFilterKind.SEARCH -> Icons.Default.Search
        ActiveFilterKind.SOURCE -> Icons.Default.FilterList
        ActiveFilterKind.PLATFORM -> Icons.Default.VideogameAsset
        ActiveFilterKind.GENRE -> Icons.Default.Category
        ActiveFilterKind.REGION -> Icons.Default.Public
        ActiveFilterKind.PLAYERS -> Icons.Default.Groups
        ActiveFilterKind.SERIES -> Icons.Default.CollectionsBookmark
    }
