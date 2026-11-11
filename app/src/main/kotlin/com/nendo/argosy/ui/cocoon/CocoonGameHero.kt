package com.nendo.argosy.ui.cocoon

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.lerp
import coil.compose.AsyncImage
import com.nendo.argosy.ui.dualscreen.CompanionDetail
import com.nendo.argosy.ui.dualscreen.withLiveArt
import com.nendo.argosy.ui.dualscreen.inGameRestFade
import com.nendo.argosy.ui.dualscreen.inGameRestLogoFilter
import com.nendo.argosy.ui.theme.CocoonSurface

/**
 * Cocoon's game hero on the top screen. By default: the game's art filling the screen with its
 * logo centred. With Info (X): the art softens, the logo slides into the left third and a panel
 * slides in from the right with the title, quick facts, platform and developer, the description
 * and, beneath it, a strip of screenshots. With Journey (X again): the same panel, full height,
 * tells the game's recorded journey.
 */
@Composable
fun CocoonGameHero(
    rawDetail: CompanionDetail,
    modifier: Modifier = Modifier,
    showInfo: Boolean = true,
    showJourney: Boolean = false,
    dim: Float = 0f
) {
    val liveArt = rawDetail.gameId?.let { com.nendo.argosy.ui.common.rememberResolvedArt(it) }
    val detail = rawDetail.withLiveArt(liveArt)
    // 0 at full light, 1 settled: a running game's hero goes dark a few seconds in (InGameHero),
    // leaving the logo as a grey ghost rather than a dimmed colour version of itself.
    val rest = dim.coerceIn(0f, 1f)
    val restFade = inGameRestFade(rest)
    val restLogoFilter = inGameRestLogoFilter(rest)
    val backdrop = detail.backdropUrl ?: detail.artUrl
    val t by animateFloatAsState(
        targetValue = if (showInfo) 1f else 0f,
        animationSpec = tween(280, easing = com.nendo.argosy.ui.theme.Motion.argosyEase),
        label = "cocoon-hero-info"
    )
    var screenshots by remember(detail.gameId) { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(detail.gameId) {
        screenshots = cocoonHeroScreenshots(detail.gameId)
    }

    Box(modifier = modifier.fillMaxSize().background(CocoonSurface.Background)) {
        // Cocoon's art sequence: the new art cross-fades over the old one, arriving slightly
        // zoomed in and then easing back very slowly, so the picture keeps drifting while you rest.
        // Hero Settings ▸ None keeps the wallpaper instead of the game's art.
        val heroStyle = CocoonHomeStore.heroStyle
        androidx.compose.animation.Crossfade(
            targetState = backdrop.takeIf { heroStyle != "NONE" },
            animationSpec = tween(300),
            label = "cocoon-hero-art"
        ) { art ->
            if (art != null) {
                val zoom = androidx.compose.runtime.remember(art) { androidx.compose.animation.core.Animatable(1.08f) }
                androidx.compose.runtime.LaunchedEffect(art) {
                    zoom.animateTo(1f, tween(7000, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
                }
                AsyncImage(
                    model = com.nendo.argosy.ui.common.rememberFileImageModel(art),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { scaleX = zoom.value; scaleY = zoom.value }
                        .then(if (t > 0.01f) Modifier.blur((16 * t).dp) else Modifier)
                )
            } else {
                com.nendo.argosy.ui.theme.CocoonWallpaper()
            }
        }
        if (backdrop != null) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f * t)))
        }
        if (backdrop != null && heroStyle == "VIGNETTE") {
            // Hero Settings ▸ Vignette: the art fades into black at the edges
            Box(
                Modifier.fillMaxSize().background(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.92f)
                    )
                )
            )
        }
        // The settle stage: the art goes out completely, the logo staying above it.
        if (rest > 0f) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = rest)))
        }
        // The logo follows the art a beat later: fades up, rising a little and growing to size.
        val logoIn = androidx.compose.runtime.remember(detail.gameId) { androidx.compose.animation.core.Animatable(0f) }
        androidx.compose.runtime.LaunchedEffect(detail.gameId) {
            kotlinx.coroutines.delay(110)
            logoIn.animateTo(1f, tween(340, easing = com.nendo.argosy.ui.theme.Motion.argosyEase))
        }
        val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "cocoon-logo-float")
        val floatOffset by infiniteTransition.animateFloat(
            initialValue = -5f,
            targetValue = 5f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = tween(2800, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "cocoon-logo-y"
        )
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val w = maxWidth
            val h = maxHeight
            // The logo: centred and large on its own, smaller in the left third beside the panel.
            val logo = if (detail.embeddedBannerBackdrop) null
            else detail.logoUrl ?: detail.artUrl?.takeIf { t > 0.5f && detail.logoUrl == null }
            if (logo != null) {
                // A 3DS banner is a wide picture, not a wordmark: it gets its own box so it reads
                // at full size while a game logo keeps the box it was tuned for.
                val logoW = if (detail.logoIsBanner) w * 0.72f else w * 0.46f
                val logoH = if (detail.logoIsBanner) h * 0.62f else h * 0.42f
                val centerX = w * 0.5f
                AsyncImage(
                    model = com.nendo.argosy.ui.common.rememberFileImageModel(logo),
                    contentDescription = detail.title,
                    colorFilter = restLogoFilter,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .absoluteOffset(x = centerX - logoW / 2, y = (h - logoH) / 2)
                        .size(width = logoW, height = logoH)
                        .graphicsLayer {
                            alpha = logoIn.value * restFade
                            val s = 0.94f + 0.06f * logoIn.value
                            scaleX = s
                            scaleY = s
                            translationY = (1f - logoIn.value) * 14.dp.toPx() + floatOffset.dp.toPx()
                        }
                        .then(if (detail.logoUrl == null) Modifier.clip(RoundedCornerShape(16.dp)) else Modifier)
                )
            } else if (backdrop == null && t < 0.5f) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = detail.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = CocoonSurface.TextPrimary
                    )
                }
            }

            if (t > 0f) {
                val left = w * 0.345f
                val panelW = w * 0.64f
                val hasShots = screenshots.isNotEmpty() && !showJourney
                val panelTop = h * 0.14f
                val panelH = if (hasShots) h * 0.50f else h * 0.70f
                Column(
                    modifier = Modifier
                        .absoluteOffset(x = left, y = panelTop)
                        .width(panelW)
                        .height(panelH)
                        .graphicsLayer { alpha = t * restFade }
                        .background(CocoonSurface.Panel.copy(alpha = 0.92f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    val gameId = detail.gameId
                    if (showJourney && gameId != null) {
                        CocoonJourneyPanel(gameId = gameId, platformName = detail.subtitle)
                    } else {
                        InfoPanelBody(detail)
                    }
                }
                if (hasShots) {
                    val stripTop = panelTop + panelH + h * 0.025f
                    val stripH = h * 0.175f
                    Row(
                        modifier = Modifier
                            .absoluteOffset(x = left, y = stripTop)
                            .width(panelW)
                            .height(stripH)
                            .graphicsLayer { alpha = t * restFade }
                            .background(CocoonSurface.Panel.copy(alpha = 0.92f), RoundedCornerShape(18.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val shotH = stripH - 16.dp
                        screenshots.forEach { shot ->
                            AsyncImage(
                                model = com.nendo.argosy.ui.common.rememberFileImageModel(shot),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .height(shotH)
                                    .width(shotH * 1.75f)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        }
                    }
                }
            }

            val stats = detail.stats
            if (stats != null && stats.achievementCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 20.dp, bottom = 18.dp)
                        .graphicsLayer { alpha = restFade }
                        .background(CocoonSurface.Chip, RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.EmojiEvents,
                            contentDescription = null,
                            tint = CocoonSurface.TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${stats.earnedAchievementCount}/${stats.achievementCount}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = com.nendo.argosy.ui.theme.generated.ComponentDefaults
                                    .FrostedSurface.chromeFontSizeSp.sp
                            ),
                            fontWeight = FontWeight.Bold,
                            color = CocoonSurface.TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoPanelBody(detail: CompanionDetail) {
    val stats = detail.stats
    val chips = buildList {
        stats?.timeToBeatMainSec?.takeIf { it > 0 }?.let { add(Icons.Filled.Schedule to hours(it)) }
        stats?.timeToBeatCompletionistSec?.takeIf { it > 0 }?.let { add(Icons.Filled.CheckCircle to hours(it)) }
        if (isEmpty()) {
            stats?.releaseYear?.let { add(Icons.Filled.CalendarMonth to it.toString()) }
            stats?.players?.takeIf { it.isNotBlank() }?.let { add(Icons.Filled.Group to it) }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = detail.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = CocoonSurface.TextPrimary.copy(alpha = 0.72f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (chips.isNotEmpty()) {
            Spacer(modifier = Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                chips.forEach { (icon, label) -> HeroChip(icon, label) }
            }
        }
    }
    val platformLine = listOfNotNull(
        detail.subtitle?.takeIf { it.isNotBlank() },
        stats?.developer?.takeIf { it.isNotBlank() }
    ).joinToString(" • ")
    if (platformLine.isNotEmpty()) {
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.SportsEsports,
                contentDescription = null,
                tint = CocoonSurface.TextPrimary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = platformLine,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = CocoonSurface.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    detail.overview?.takeIf { it.isNotBlank() }?.let { overview ->
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = overview,
            style = MaterialTheme.typography.bodySmall,
            color = CocoonSurface.TextPrimary.copy(alpha = 0.9f),
            overflow = TextOverflow.Clip,
            modifier = Modifier.fillMaxHeight()
        )
    }
}

private fun hours(seconds: Int): String {
    val h = seconds / 3600f
    return if (h >= 10f) "${h.toInt()}h" else "%.1fh".format(h)
}

@Composable
private fun HeroChip(icon: ImageVector, label: String) {
    Row(
        modifier = Modifier
            .background(CocoonSurface.Chip, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = CocoonSurface.TextPrimary, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(5.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = CocoonSurface.TextPrimary)
    }
}

/** Cocoon's bottom-right X pill: names what X does next (Info, then Your journey, then Close). */
@Composable
fun CocoonInfoPill(active: Boolean, modifier: Modifier = Modifier, journey: Boolean = false) {
    Row(
        modifier = modifier
            .background(if (active) CocoonSurface.TextPrimary.copy(alpha = 0.18f) else CocoonSurface.Chip, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        com.nendo.argosy.ui.primitives.InputGlyph(
            button = com.nendo.argosy.ui.components.InputButton.X,
            size = 16.dp,
            tint = CocoonSurface.TextSecondary
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = androidx.compose.ui.res.stringResource(
                when {
                    journey -> com.nendo.argosy.R.string.cocoon_journey_close
                    active -> com.nendo.argosy.R.string.cocoon_journey_title
                    else -> com.nendo.argosy.R.string.cocoon_info
                }
            ),
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = com.nendo.argosy.ui.theme.generated.ComponentDefaults
                    .FrostedSurface.chromeFontSizeSp.sp
            ),
            fontWeight = FontWeight.Bold,
            color = CocoonSurface.TextPrimary
        )
    }
}


private suspend fun cocoonHeroScreenshots(gameId: Long?): List<String> {
    val id = gameId ?: return emptyList()
    val repo = com.nendo.argosy.DualScreenManagerHolder.instance?.gameRepository ?: return emptyList()
    return runCatching {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            repo.getScreenshots(id)
                .sortedBy { it.position }
                .mapNotNull { it.cachedPath ?: it.sourceUrl.takeIf { url -> url.isNotBlank() } }
        }
    }.getOrDefault(emptyList()).take(4)
}
