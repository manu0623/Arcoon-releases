package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.generated.ColorTokens

/**
 * How an achievement was earned, which decides the badge's frame: hardcore wears the gold border,
 * softcore the thin bronze one, and a locked badge is drained of colour and dimmed.
 */
enum class AchievementBadgeTier { HARDCORE, SOFTCORE, LOCKED }

fun achievementBadgeTier(isUnlocked: Boolean, isUnlockedHardcore: Boolean): AchievementBadgeTier =
    when {
        isUnlockedHardcore -> AchievementBadgeTier.HARDCORE
        isUnlocked -> AchievementBadgeTier.SOFTCORE
        else -> AchievementBadgeTier.LOCKED
    }

@Composable
fun AchievementBadgeTier.accentColor(): Color = when (this) {
    AchievementBadgeTier.HARDCORE -> ColorTokens.Domain.AchievementTier.hardcore
    AchievementBadgeTier.SOFTCORE -> ColorTokens.Domain.AchievementTier.softcore
    AchievementBadgeTier.LOCKED -> MaterialTheme.colorScheme.onSurface.copy(alpha = LOCKED_ACCENT_ALPHA)
}

/**
 * One achievement badge, the same on the game page and on the home tile. [badgePath] is a cached
 * file or a remote url; null draws the trophy in the tier's colour instead.
 */
@Composable
fun AchievementBadge(
    badgePath: String?,
    tier: AchievementBadgeTier,
    modifier: Modifier = Modifier,
    size: Dp = Dimens.iconXl,
    contentDescription: String? = null
) {
    val badgeShape = RoundedCornerShape(Dimens.radiusSm)
    val hardcore = ColorTokens.Domain.AchievementTier.hardcore
    val softcore = ColorTokens.Domain.AchievementTier.softcore
    val frame = when (tier) {
        AchievementBadgeTier.HARDCORE -> Modifier
            .shadow(Dimens.elevationMd, badgeShape, spotColor = hardcore.copy(alpha = HARDCORE_GLOW_ALPHA))
            .border(
                width = Dimens.borderMedium,
                brush = Brush.linearGradient(
                    colors = listOf(
                        hardcore,
                        ColorTokens.Domain.AchievementTier.hardcoreHighlight,
                        hardcore
                    )
                ),
                shape = badgeShape
            )
        AchievementBadgeTier.SOFTCORE ->
            Modifier.border(Dimens.borderThin, softcore.copy(alpha = SOFTCORE_BORDER_ALPHA), badgeShape)
        AchievementBadgeTier.LOCKED -> Modifier
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(badgeShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(frame),
        contentAlignment = Alignment.Center
    ) {
        if (badgePath != null) {
            AsyncImage(
                model = badgePath,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                colorFilter = if (tier == AchievementBadgeTier.LOCKED) {
                    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                } else {
                    null
                },
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (tier == AchievementBadgeTier.LOCKED) LOCKED_ART_ALPHA else 1f)
            )
        } else {
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = contentDescription,
                tint = tier.accentColor(),
                modifier = Modifier.size(size * TROPHY_FRACTION)
            )
        }
    }
}

private const val LOCKED_ACCENT_ALPHA = 0.5f
private const val LOCKED_ART_ALPHA = 0.7f
private const val HARDCORE_GLOW_ALPHA = 0.5f
private const val SOFTCORE_BORDER_ALPHA = 0.6f
private const val TROPHY_FRACTION = 0.66f
