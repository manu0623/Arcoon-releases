package com.nendo.argosy.ui.screens.gamedetail.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.MotionTokens

/**
 * The colour every loading block breathes with. The old flat grey read as a finished-but-empty
 * card; a slow pulse makes the page say it is still working, and stops the cover-shaped box from
 * looking like a game whose art never arrived.
 */
@Composable
internal fun skeletonTint(): Color {
    val theme = LocalArgosyTheme.current
    val transition = rememberInfiniteTransition(label = "gameDetailSkeleton")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(MotionTokens.Tween.shimmerMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gameDetailSkeletonPulse"
    )
    return theme.surfaceRaised.copy(alpha = 0.35f + 0.4f * pulse)
}

/** The cover's slot while its bitmap decodes. Geometry matches [GameHeader]'s cover exactly. */
@Composable
internal fun GameDetailCoverPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.radiusLg))
            .background(skeletonTint())
    )
}

/**
 * The page's stand-in while the game loads. It mirrors [GameHeader]'s geometry - a 200x280 cover
 * beside the title block - so the real header settles where the skeleton already sat instead of
 * jumping, and the blocks breathe so a slow load looks like work in progress.
 */
@Composable
fun GameDetailSkeleton(modifier: Modifier = Modifier) {
    val tint = skeletonTint()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimens.spacingXl)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXl)
            ) {
                SkeletonBlock(
                    modifier = Modifier
                        .width(200.dp)
                        .height(280.dp),
                    tint = tint,
                    radius = Dimens.radiusLg
                )

                Column(modifier = Modifier.weight(1f)) {
                    SkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(40.dp),
                        tint = tint,
                        radius = Dimens.radiusMd
                    )
                    Spacer(modifier = Modifier.height(Dimens.spacingMd))
                    SkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth(0.3f)
                            .height(24.dp),
                        tint = tint,
                        radius = Dimens.radiusSm
                    )
                }
            }
        }
    }
}

@Composable
private fun SkeletonBlock(
    modifier: Modifier,
    tint: Color,
    radius: Dp
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .background(tint)
    )
}
