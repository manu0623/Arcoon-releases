package com.nendo.argosy.ui.components

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import com.nendo.argosy.domain.model.RaFeaturedMode
import com.nendo.argosy.domain.model.RaTileContent
import com.nendo.argosy.domain.model.TileRect
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.primitives.ArgosyProgressBar
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.screens.home.HomeGameUi
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalBoxArtStyle
import com.nendo.argosy.ui.theme.generated.ColorTokens
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.util.formatRelativeTimeShort
import java.time.Instant

private enum class RaTileShape { SINGLE, WIDE, TALL, SQUARE }

private const val GROUND_ART_ALPHA = 0.35f
private const val GROUND_SCRIM_TOP_ALPHA = 0.55f
private const val GROUND_SCRIM_BOTTOM_ALPHA = 0.85f
private const val MASTERED_BAND_ALPHA = 0.18f
private const val STRIP_LIMIT = 4
private const val TALL_STRIP_LIMIT = 3

/**
 * The RetroAchievements tile in every cell shape. The frame is the one a wide game tile wears:
 * the ground game's gradient and glass border, so the tile reads as belonging to the same page as
 * the covers around it. Badges hang off [onBadgeTap] and the top band off [onBandTap], which is
 * how touch reaches what the d-pad reaches while the tile is engaged.
 */
@Composable
internal fun RaTileBox(
    placement: Modifier,
    rect: TileRect,
    ra: RaTileUi,
    isFocused: Boolean,
    isOverlapped: Boolean,
    isEngaged: Boolean,
    engagedIndex: Int,
    editModeLabel: String?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onBadgeTap: ((Int) -> Unit)?,
    onBandTap: (() -> Unit)?,
    onCoverLoaded: ((Long, Bitmap) -> Unit)?
) {
    val theme = LocalArgosyTheme.current
    val boxArtStyle = LocalBoxArtStyle.current
    val shape = RoundedCornerShape(boxArtStyle.cornerRadiusDp)
    val ground = ra.groundGame
    val gradientColors = ground?.gradientColors
    val accent = boxArtStyle.accentColor ?: theme.focusAccent
    val borderBrush = wideTileBorderBrush(
        style = boxArtStyle.borderStyle,
        gradientColors = gradientColors,
        accent = accent,
        secondary = boxArtStyle.secondaryColor ?: accent
    ).takeIf { isFocused && boxArtStyle.borderThicknessDp.value > 0f }
    val tileShape = when {
        rect.columnSpan == 1 && rect.rowSpan == 1 -> RaTileShape.SINGLE
        rect.columnSpan > rect.rowSpan -> RaTileShape.WIDE
        rect.rowSpan > rect.columnSpan -> RaTileShape.TALL
        else -> RaTileShape.SQUARE
    }

    Box(modifier = placement) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .boxArtFrame(
                    isFocused = isFocused,
                    focusScale = focusScaleForSpan(rect),
                    alphaOverride = if (isOverlapped) OVERLAPPED_ALPHA else null,
                    artworkGradient = gradientColors,
                    background = SolidColor(theme.surfaceRaised)
                )
                .then(
                    if (borderBrush != null) {
                        Modifier.border(boxArtStyle.borderThicknessDp, borderBrush, shape)
                    } else {
                        Modifier
                    }
                )
                .then(
                    if (onLongClick == null) {
                        Modifier.clickableNoFocus(onClick = onClick)
                    } else {
                        Modifier.clickableNoFocus(onClick = onClick, onLongClick = onLongClick)
                    }
                )
        ) {
            RaTileGround(game = ground, onCoverLoaded = onCoverLoaded)
            RaTileBody(
                ra = ra,
                tileShape = tileShape,
                isEngaged = isEngaged,
                engagedIndex = engagedIndex,
                onBadgeTap = onBadgeTap,
                onBandTap = onBandTap
            )
        }
        if (editModeLabel != null && isFocused) {
            TileModeTab(label = editModeLabel, modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}

@Composable
private fun RaTileGround(game: HomeGameUi?, onCoverLoaded: ((Long, Bitmap) -> Unit)?) {
    val theme = LocalArgosyTheme.current
    val cover = rememberFileImageModel(game?.coverPath) ?: return
    AsyncImage(
        model = cover,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .alpha(GROUND_ART_ALPHA),
        onSuccess = { state ->
            val gameId = game?.id ?: return@AsyncImage
            val bitmap = (state.result.drawable as? BitmapDrawable)?.bitmap
            if (bitmap != null) onCoverLoaded?.invoke(gameId, bitmap)
        }
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        theme.surfaceBase.copy(alpha = GROUND_SCRIM_TOP_ALPHA),
                        theme.surfaceBase.copy(alpha = GROUND_SCRIM_BOTTOM_ALPHA)
                    )
                )
            )
    )
}

@Composable
private fun RaTileBody(
    ra: RaTileUi,
    tileShape: RaTileShape,
    isEngaged: Boolean,
    engagedIndex: Int,
    onBadgeTap: ((Int) -> Unit)?,
    onBandTap: (() -> Unit)?
) {
    val content = ra.content
    val selectedIndex = engagedIndex.takeIf { isEngaged && it in ra.entries.indices }
    when {
        content == null -> RaSignedOut(labels = ra.labels, tileShape = tileShape)
        content is RaTileContent.Account && content.recentUnlocks.isEmpty() ->
            RaNothingEarned(content = content, labels = ra.labels, tileShape = tileShape)
        content is RaTileContent.TrackedGame && content.total == 0 ->
            RaNothingEarned(content = content, labels = ra.labels, tileShape = tileShape)
        content is RaTileContent.Account -> RaAccountLayout(
            content = content,
            ra = ra,
            tileShape = tileShape,
            selectedIndex = selectedIndex,
            onBadgeTap = onBadgeTap,
            onBandTap = onBandTap
        )
        content is RaTileContent.TrackedGame -> RaTrackedLayout(
            content = content,
            ra = ra,
            tileShape = tileShape,
            selectedIndex = selectedIndex,
            onBadgeTap = onBadgeTap,
            onBandTap = onBandTap
        )
    }
}

@Composable
private fun RaSignedOut(labels: RaTileLabels, tileShape: RaTileShape) {
    val theme = LocalArgosyTheme.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.spacingSm),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.EmojiEvents,
            contentDescription = null,
            tint = theme.textDim,
            modifier = Modifier.size(Dimens.iconXl)
        )
        if (tileShape != RaTileShape.SINGLE) {
            Text(
                text = stringResource(labels.signedOut),
                style = MaterialTheme.typography.labelMedium,
                color = theme.textDim,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Dimens.spacingXs)
            )
        }
    }
}

@Composable
private fun RaNothingEarned(content: RaTileContent, labels: RaTileLabels, tileShape: RaTileShape) {
    val theme = LocalArgosyTheme.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.spacingSm),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        if (tileShape != RaTileShape.SINGLE) RaAccountLine(content = content, labels = labels)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = theme.textDim,
                modifier = Modifier.size(Dimens.iconXl)
            )
            if (tileShape != RaTileShape.SINGLE) {
                Text(
                    text = stringResource(labels.empty),
                    style = MaterialTheme.typography.labelMedium,
                    color = theme.textDim,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Dimens.spacingXs)
                )
            }
        }
    }
}

@Composable
private fun RaAccountLayout(
    content: RaTileContent.Account,
    ra: RaTileUi,
    tileShape: RaTileShape,
    selectedIndex: Int?,
    onBadgeTap: ((Int) -> Unit)?,
    onBandTap: (() -> Unit)?
) {
    val theme = LocalArgosyTheme.current
    val entries = ra.entries
    val hero = entries[selectedIndex ?: 0]
    val bandModifier = if (onBandTap != null) Modifier.clickableNoFocus { onBandTap() } else Modifier
    when (tileShape) {
        RaTileShape.SINGLE -> RaSingleBadgeCell(
            entry = hero,
            ra = ra,
            trailing = content.unlocks.toString(),
            modifier = bandModifier
        )
        RaTileShape.WIDE -> Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimens.spacingSm),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            RaHeroBand(
                entry = hero,
                ra = ra,
                showGame = true,
                showDescription = selectedIndex != null,
                badgeSize = Dimens.iconXl,
                vertical = false,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(bandModifier)
            )
            RaVerticalRule()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs, Alignment.CenterVertically)
            ) {
                RaAccountLine(content = content, labels = ra.labels)
                RaBadgeStrip(
                    entries = entries,
                    selectedIndex = selectedIndex,
                    limit = STRIP_LIMIT,
                    badgeSize = Dimens.iconLg,
                    onBadgeTap = onBadgeTap
                )
                RaGameProgress(content = content, labels = ra.labels)
            }
        }
        RaTileShape.TALL -> Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimens.spacingSm),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            RaHeroBand(
                entry = hero,
                ra = ra,
                showGame = true,
                showDescription = selectedIndex != null,
                badgeSize = Dimens.avatarXl,
                vertical = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .then(bandModifier)
            )
            HorizontalDivider(color = theme.hairlineLow)
            RaAccountLine(content = content, labels = ra.labels)
            RaGameProgress(content = content, labels = ra.labels)
            RaBadgeStrip(
                entries = entries,
                selectedIndex = selectedIndex,
                limit = TALL_STRIP_LIMIT,
                badgeSize = Dimens.iconLg,
                onBadgeTap = onBadgeTap,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
        RaTileShape.SQUARE -> Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimens.spacingSm),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            RaHeroBand(
                entry = hero,
                ra = ra,
                showGame = true,
                showDescription = selectedIndex != null,
                badgeSize = Dimens.avatarXl,
                vertical = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .then(bandModifier)
            )
            HorizontalDivider(color = theme.hairlineLow)
            RaAccountLine(content = content, labels = ra.labels)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RaBadgeStrip(
                    entries = entries,
                    selectedIndex = selectedIndex,
                    limit = STRIP_LIMIT,
                    badgeSize = Dimens.iconLg,
                    onBadgeTap = onBadgeTap,
                    modifier = Modifier.weight(1f, fill = false)
                )
                RaGameProgress(content = content, labels = ra.labels)
            }
        }
    }
}

@Composable
private fun RaTrackedLayout(
    content: RaTileContent.TrackedGame,
    ra: RaTileUi,
    tileShape: RaTileShape,
    selectedIndex: Int?,
    onBadgeTap: ((Int) -> Unit)?,
    onBandTap: (() -> Unit)?
) {
    val theme = LocalArgosyTheme.current
    val selected = selectedIndex?.let { ra.entries.getOrNull(it) }
    val bandModifier = if (onBandTap != null) Modifier.clickableNoFocus { onBandTap() } else Modifier
    val lockedOffset = if (content.latestUnlock != null) 1 else 0
    val locked = ra.entries.drop(lockedOffset)

    if (tileShape == RaTileShape.SINGLE) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(Dimens.spacingSm),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
            ) {
                if (content.mastered) {
                    RaMasteredBand(content = content, labels = ra.labels)
                } else {
                    RaProgressRow(content = content, labels = ra.labels, showPoints = false)
                }
            }
        }
        return
    }

    val header: @Composable (Modifier) -> Unit = { modifier ->
        if (selected != null) {
            RaHeroBand(
                entry = selected,
                ra = ra,
                showGame = false,
                showDescription = true,
                badgeSize = Dimens.iconXl,
                vertical = false,
                modifier = modifier
            )
        } else {
            Column(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs, Alignment.CenterVertically)
            ) {
                Text(
                    text = content.gameTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = theme.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (content.mastered) {
                    RaMasteredBand(content = content, labels = ra.labels)
                } else {
                    RaProgressRow(content = content, labels = ra.labels, showPoints = true)
                }
            }
        }
    }
    val rows: @Composable (Modifier) -> Unit = { modifier ->
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs, Alignment.CenterVertically)
        ) {
            val latest = ra.entries.firstOrNull() as? RaBrowseEntry.Unlocked
            if (latest != null) {
                RaLatestRow(
                    entry = latest,
                    ra = ra,
                    isSelected = selectedIndex == 0,
                    onTap = onBadgeTap?.let { tap -> { tap(0) } }
                )
            }
            if (!content.mastered && locked.isNotEmpty()) {
                RaNextRow(
                    entries = locked,
                    labels = ra.labels,
                    selectedIndex = selectedIndex?.minus(lockedOffset)?.takeIf { it >= 0 },
                    onBadgeTap = onBadgeTap?.let { tap -> { index -> tap(index + lockedOffset) } }
                )
            }
        }
    }

    if (tileShape == RaTileShape.WIDE) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimens.spacingSm),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            header(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(bandModifier)
            )
            RaVerticalRule()
            rows(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.spacingSm),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        header(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .then(bandModifier)
        )
        HorizontalDivider(color = theme.hairlineLow)
        rows(Modifier.fillMaxWidth())
    }
}

/**
 * The latest badge as the whole cell, with a count where a game tile's tick would sit.
 */
@Composable
private fun RaSingleBadgeCell(
    entry: RaBrowseEntry,
    ra: RaTileUi,
    trailing: String,
    modifier: Modifier
) {
    val theme = LocalArgosyTheme.current
    val unlock = (entry as? RaBrowseEntry.Unlocked)?.unlock
    val isNew = unlock != null && unlock.unlockedAt >= ra.newSince
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val inset = Dimens.spacingMd
        val side = minOf(maxWidth, maxHeight) - inset * 2
        AchievementBadge(
            badgePath = entry.badgePath,
            tier = entry.tier,
            size = side,
            contentDescription = entry.title
        )
        if (isNew) {
            NewBadge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Dimens.spacingXs),
                width = Dimens.avatarMd,
                height = Dimens.iconMd
            )
        }
        Text(
            text = trailing,
            style = MaterialTheme.typography.labelSmall,
            color = theme.textPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Dimens.spacingXs)
                .clip(RoundedCornerShape(Dimens.radiusSm))
                .background(theme.surfaceBase.copy(alpha = GROUND_SCRIM_BOTTOM_ALPHA))
                .padding(horizontal = Dimens.spacingXs)
        )
    }
}

/**
 * The band naming one achievement: its badge, its title, the game it belongs to, and its points
 * and age. With [showDescription] the achievement's own text joins as a third line, which for a
 * locked one is what there is still to do.
 */
@Composable
private fun RaHeroBand(
    entry: RaBrowseEntry,
    ra: RaTileUi,
    showGame: Boolean,
    showDescription: Boolean,
    badgeSize: Dp,
    vertical: Boolean,
    modifier: Modifier
) {
    val theme = LocalArgosyTheme.current
    val labels = ra.labels
    val unlock = (entry as? RaBrowseEntry.Unlocked)?.unlock
    val isNew = unlock != null && unlock.unlockedAt >= ra.newSince
    val ago = unlock?.let { rememberRelativeAge(it.unlockedAt) }
    val points = pluralStringResource(labels.points, entry.points, entry.points)
    val meta = if (ago != null) stringResource(labels.unlockMeta, points, ago) else points
    val description = when {
        !showDescription -> null
        entry.description != null -> entry.description
        entry.tier == AchievementBadgeTier.LOCKED -> stringResource(labels.locked)
        else -> null
    }
    val badge: @Composable () -> Unit = {
        Box {
            AchievementBadge(
                badgePath = entry.badgePath,
                tier = entry.tier,
                size = badgeSize,
                contentDescription = entry.title
            )
            if (isNew) {
                NewBadge(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = Dimens.spacingXs, y = -Dimens.spacingXs),
                    width = Dimens.avatarMd,
                    height = Dimens.iconMd
                )
            }
        }
    }
    val lines: @Composable ColumnScopeLines.() -> Unit = {
        Text(
            text = entry.title,
            style = MaterialTheme.typography.titleSmall,
            color = theme.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign
        )
        if (showGame && unlock != null) {
            Text(
                text = unlock.gameTitle,
                style = MaterialTheme.typography.labelSmall,
                color = theme.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = textAlign
            )
        }
        Text(
            text = meta,
            style = MaterialTheme.typography.labelSmall,
            color = theme.textMute,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = theme.textDim,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = textAlign
            )
        }
    }
    if (vertical) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            badge()
            ColumnScopeLines(TextAlign.Center).lines()
        }
    } else {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            badge()
            Column(modifier = Modifier.weight(1f)) {
                ColumnScopeLines(TextAlign.Start).lines()
            }
        }
    }
}

private class ColumnScopeLines(val textAlign: TextAlign)

@Composable
private fun RaAccountLine(content: RaTileContent, labels: RaTileLabels) {
    val theme = LocalArgosyTheme.current
    val points = pluralStringResource(labels.points, content.points, content.points)
    val unlocks = pluralStringResource(labels.unlocks, content.unlocks, content.unlocks)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = content.username,
            style = MaterialTheme.typography.labelMedium,
            color = theme.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Text(
            text = stringResource(labels.accountTally, points, unlocks),
            style = MaterialTheme.typography.labelSmall,
            color = theme.textDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RaGameProgress(content: RaTileContent.Account, labels: RaTileLabels) {
    val theme = LocalArgosyTheme.current
    val latest = content.latestUnlock ?: return
    if (content.latestGameTotal <= 0) return
    Text(
        text = stringResource(
            labels.gameProgress,
            latest.gameTitle,
            content.latestGameEarned,
            content.latestGameTotal
        ),
        style = MaterialTheme.typography.labelSmall,
        color = theme.textMute,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun RaBadgeStrip(
    entries: List<RaBrowseEntry>,
    selectedIndex: Int?,
    limit: Int,
    badgeSize: Dp,
    onBadgeTap: ((Int) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val ringShape = RoundedCornerShape(Dimens.radiusSm)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        entries.take(limit).forEachIndexed { index, entry ->
            AchievementBadge(
                badgePath = entry.badgePath,
                tier = entry.tier,
                size = badgeSize,
                contentDescription = entry.title,
                modifier = Modifier
                    .argosyFocusIndicators(
                        focused = selectedIndex == index,
                        indicators = FocusIndicators.Ring,
                        shape = ringShape
                    )
                    .then(
                        if (onBadgeTap != null) {
                            Modifier.clickableNoFocus { onBadgeTap(index) }
                        } else {
                            Modifier
                        }
                    )
            )
        }
    }
}

@Composable
private fun RaProgressRow(
    content: RaTileContent.TrackedGame,
    labels: RaTileLabels,
    showPoints: Boolean
) {
    val theme = LocalArgosyTheme.current
    val fraction = if (content.total > 0) content.unlocks.toFloat() / content.total else 0f
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArgosyProgressBar(
            progress = fraction,
            tint = content.featuredMode.tierColor(),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(labels.progress, content.unlocks, content.total),
            style = MaterialTheme.typography.labelSmall,
            color = theme.textPrimary,
            maxLines = 1
        )
        if (showPoints) {
            Text(
                text = pluralStringResource(labels.points, content.points, content.points),
                style = MaterialTheme.typography.labelSmall,
                color = theme.textDim,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun RaMasteredBand(content: RaTileContent.TrackedGame, labels: RaTileLabels) {
    val gold = ColorTokens.Domain.AchievementTier.hardcore
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(gold.copy(alpha = MASTERED_BAND_ALPHA))
            .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.EmojiEvents,
            contentDescription = null,
            tint = gold,
            modifier = Modifier.size(Dimens.iconSm)
        )
        Text(
            text = stringResource(labels.mastered),
            style = MaterialTheme.typography.labelMedium,
            color = gold,
            maxLines = 1
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = pluralStringResource(labels.points, content.points, content.points),
            style = MaterialTheme.typography.labelSmall,
            color = gold,
            maxLines = 1
        )
    }
}

@Composable
private fun RaLatestRow(
    entry: RaBrowseEntry.Unlocked,
    ra: RaTileUi,
    isSelected: Boolean,
    onTap: (() -> Unit)?
) {
    val theme = LocalArgosyTheme.current
    val ago = rememberRelativeAge(entry.unlock.unlockedAt)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RaRowHeading(text = stringResource(ra.labels.latestHeading))
        AchievementBadge(
            badgePath = entry.badgePath,
            tier = entry.tier,
            size = Dimens.iconLg,
            contentDescription = entry.title,
            modifier = Modifier
                .argosyFocusIndicators(
                    focused = isSelected,
                    indicators = FocusIndicators.Ring,
                    shape = RoundedCornerShape(Dimens.radiusSm)
                )
                .then(if (onTap != null) Modifier.clickableNoFocus { onTap() } else Modifier)
        )
        Text(
            text = entry.title,
            style = MaterialTheme.typography.labelMedium,
            color = theme.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = ago,
            style = MaterialTheme.typography.labelSmall,
            color = theme.textMute,
            maxLines = 1
        )
    }
}

@Composable
private fun RaNextRow(
    entries: List<RaBrowseEntry>,
    labels: RaTileLabels,
    selectedIndex: Int?,
    onBadgeTap: ((Int) -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RaRowHeading(text = stringResource(labels.nextHeading))
        RaBadgeStrip(
            entries = entries,
            selectedIndex = selectedIndex,
            limit = STRIP_LIMIT,
            badgeSize = Dimens.iconLg,
            onBadgeTap = onBadgeTap
        )
    }
}

@Composable
private fun RowScope.RaRowHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = LocalArgosyTheme.current.textMute,
        maxLines = 1,
        modifier = Modifier.align(Alignment.CenterVertically)
    )
}

@Composable
private fun RaVerticalRule() {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(Dimens.borderThin)
            .background(LocalArgosyTheme.current.hairlineLow)
    )
}

@Composable
private fun rememberRelativeAge(unlockedAtMillis: Long): String {
    val context = LocalContext.current
    return remember(unlockedAtMillis) {
        formatRelativeTimeShort(context, Instant.ofEpochMilli(unlockedAtMillis))
    }
}

@Composable
private fun RaFeaturedMode.tierColor(): Color = when (this) {
    RaFeaturedMode.HARDCORE -> ColorTokens.Domain.AchievementTier.hardcore
    RaFeaturedMode.SOFTCORE -> ColorTokens.Domain.AchievementTier.softcore
}
