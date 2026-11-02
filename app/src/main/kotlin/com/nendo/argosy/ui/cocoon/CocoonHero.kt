package com.nendo.argosy.ui.cocoon

import androidx.compose.foundation.background
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.Group
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.dualscreen.PresentationSlot
import com.nendo.argosy.ui.theme.CocoonSurface
import com.nendo.argosy.ui.theme.CocoonWallpaper

/** The presentation-screen half of the board: the focused tile, large, with its name card. */
@Composable
fun CocoonHeroScene(slot: PresentationSlot.CocoonHero) {
    Box(modifier = Modifier.fillMaxSize()) {
        CocoonWallpaper()
        BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val iconSize = (maxHeight * 0.5f).coerceAtMost(320.dp)
            // Cocoon's "reverse zoom": the old folder fades out fast, the new one settles in from
            // slightly larger while fading in.
            androidx.compose.animation.AnimatedContent(
                targetState = slot,
                transitionSpec = {
                    (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200, delayMillis = 40)) +
                        androidx.compose.animation.scaleIn(
                            androidx.compose.animation.core.tween(240, easing = com.nendo.argosy.ui.theme.Motion.argosyEase),
                            initialScale = 0.94f
                        )) togetherWith androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(100))
                },
                contentAlignment = Alignment.Center,
                label = "cocoon-hero"
            ) { shown ->
                // A theme can give each folder a full-screen hero image and a logo.
                val themeId = com.nendo.argosy.ui.cocoon.theme.CocoonThemeEngine.active.value?.id to
                    com.nendo.argosy.ui.cocoon.theme.CocoonThemeEngine.packs.value
                val hero = remember(themeId, shown.glyph, shown.platformSlug) {
                    com.nendo.argosy.ui.cocoon.theme.CocoonThemeEngine.folderArt(shown.glyph, shown.platformSlug, "hero")
                }
                val logo = remember(themeId, shown.glyph, shown.platformSlug) {
                    com.nendo.argosy.ui.cocoon.theme.CocoonThemeEngine.folderArt(shown.glyph, shown.platformSlug, "logo")
                }
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (hero != null) {
                        coil.compose.AsyncImage(
                            model = hero,
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            Modifier.fillMaxSize().background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    0.55f to androidx.compose.ui.graphics.Color.Transparent,
                                    1f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f)
                                )
                            )
                        )
                    }
                    if (logo != null) {
                        coil.compose.AsyncImage(
                            model = logo,
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            modifier = Modifier.size(width = iconSize * 1.6f, height = iconSize * 0.8f)
                        )
                    } else {
                        CocoonHeroCard(
                            title = shown.title ?: shown.titleRes?.let { stringResource(it) }.orEmpty(),
                            kind = stringResource(shown.kindRes),
                            glyph = shown.glyph,
                            platformSlug = shown.platformSlug,
                            colorIndex = shown.colorIndex,
                            iconSize = iconSize,
                        )
                    }
                }
            }
        }
        // Cocoon's corner HUD: the L1 / friends pill top-left (the clock and battery sit top-right).
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(CocoonSurface.Chip, RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            com.nendo.argosy.ui.primitives.InputGlyph(
                button = com.nendo.argosy.ui.components.InputButton.LB,
                size = 18.dp,
                tint = CocoonSurface.TextSecondary
            )
            Spacer(modifier = Modifier.width(8.dp))
            androidx.compose.material3.Icon(
                // LB opens the Friends panel from the left
                androidx.compose.material.icons.Icons.Filled.Group,
                contentDescription = null,
                tint = CocoonSurface.TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Big folder over a dark rounded name card, as Cocoon draws its focused item. */
@Composable
fun CocoonHeroCard(
    title: String,
    kind: String,
    glyph: CocoonGlyph,
    platformSlug: String?,
    colorIndex: Int,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CocoonFolderIcon(
            colorIndex = colorIndex,
            glyph = glyph,
            platformSlug = platformSlug,
            size = iconSize,
        )
        Spacer(modifier = Modifier.height(iconSize * 0.06f))
        Column(
            modifier = Modifier
                .widthIn(min = iconSize, max = iconSize * 1.6f)
                .background(CocoonSurface.Chip, RoundedCornerShape(18.dp))
                .padding(horizontal = 24.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = CocoonSurface.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = kind,
                style = MaterialTheme.typography.labelSmall,
                color = CocoonSurface.TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
