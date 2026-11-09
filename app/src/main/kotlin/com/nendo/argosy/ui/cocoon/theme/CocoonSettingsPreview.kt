package com.nendo.argosy.ui.cocoon.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.cocoon.CocoonFolderIcon
import com.nendo.argosy.ui.cocoon.CocoonFolderPalette
import com.nendo.argosy.ui.cocoon.CocoonGlyph
import com.nendo.argosy.ui.cocoon.CocoonHomeStore
import com.nendo.argosy.ui.cocoon.CocoonNowPlayingStyle
import com.nendo.argosy.ui.primitives.LocalCocoonCursor
import com.nendo.argosy.ui.primitives.cocoonBrackets
import com.nendo.argosy.ui.theme.CocoonSurface

/** Which screen a setting changes, so its preview shows that screen. */
enum class CocoonPreviewKind { HOME, TOP, SURFACE, HANDLE }

private val PREVIEW_SLUGS = listOf("snes", "gba", "psx", "n64", "nds", "genesis", "psp", "gb", "ps2", "gc", "3ds", "nes")

/**
 * A live, miniature picture of what a setting looks like once applied: the Home board (dock,
 * folders, cursor) or the top screen (game art, hero card, now playing). It reads the same stored
 * settings as the real screens, so changing a value redraws it straight away.
 */
@Composable
fun CocoonSettingsPreview(kind: CocoonPreviewKind, modifier: Modifier = Modifier) {
    // The preview draws its own corners: it must not pull the page's sliding cursor over to it.
    CompositionLocalProvider(
        LocalCocoonCursor provides null,
        com.nendo.argosy.ui.theme.LocalMotionTier provides com.nendo.argosy.ui.cocoon.cocoonMotionTier()
    ) {
        Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = androidx.compose.ui.res.stringResource(
                    when (kind) {
                        CocoonPreviewKind.TOP -> com.nendo.argosy.R.string.cocoon_preview_top
                        CocoonPreviewKind.HANDLE -> com.nendo.argosy.R.string.cocoon_preview_game
                        else -> com.nendo.argosy.R.string.cocoon_preview_home
                    }
                ),
                style = MaterialTheme.typography.labelMedium,
                color = CocoonSurface.TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            when (kind) {
                CocoonPreviewKind.TOP -> TopScreenPreview(Modifier.fillMaxWidth())
                CocoonPreviewKind.HANDLE -> HandlePreview(Modifier.fillMaxWidth())
                else -> HomePreview(Modifier.fillMaxWidth(), showPanel = kind == CocoonPreviewKind.SURFACE)
            }
        }
    }
}

/**
 * The same demo drawn on the presentation screen, where it has the whole panel instead of a
 * thumbnail slot in the settings list.
 */
@Composable
fun CocoonDemoScreen(kind: CocoonPreviewKind, modifier: Modifier = Modifier) {
    CompositionLocalProvider(
        LocalCocoonCursor provides null,
        com.nendo.argosy.ui.theme.LocalMotionTier provides com.nendo.argosy.ui.cocoon.cocoonMotionTier()
    ) {
        when (kind) {
            CocoonPreviewKind.TOP -> TopScreenPreview(modifier.fillMaxSize(), framed = false)
            CocoonPreviewKind.HANDLE -> HandlePreview(modifier.fillMaxSize(), framed = false)
            else -> HomePreview(
                modifier.fillMaxSize(),
                showPanel = kind == CocoonPreviewKind.SURFACE,
                framed = false
            )
        }
    }
}

@Composable
private fun PreviewFrame(
    ratio: Float,
    modifier: Modifier,
    framed: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(if (framed) 16.dp else 0.dp)
    Box(
        modifier = modifier
            .then(if (framed) Modifier.aspectRatio(ratio) else Modifier.fillMaxSize())
            .clip(shape)
            .background(CocoonSurface.Background)
            .then(
                if (framed) Modifier.border(1.dp, Color.White.copy(alpha = 0.14f), shape) else Modifier
            )
    ) { content() }
}

@Composable
private fun HomePreview(modifier: Modifier, showPanel: Boolean, framed: Boolean = true) {
    val rows = CocoonHomeStore.rows
    val columns = 4
    val count = rows * columns
    // The cursor hops from folder to folder so the cursor style, thickness and motion speed show.
    var cursor by remember { mutableIntStateOf(0) }
    // Animation Style sets how fast the cursor hops, and Reduced stops hopping altogether — the
    // same tiers the real board animates with.
    val motion = CocoonHomeStore.motion
    LaunchedEffect(rows, motion) {
        cursor = 0
        if (motion == "REDUCED") return@LaunchedEffect
        val stepMs = if (motion == "SNAPPY") 600L else 1100L
        while (true) {
            kotlinx.coroutines.delay(stepMs)
            cursor = (cursor + rows).let { if (it >= count) (it % rows + 1) % rows else it }
        }
    }
    PreviewFrame(1240f / 1080f, modifier, framed) {
        CocoonThemeEngine.active.value?.wallpaperMain?.let { CocoonThemeWallpaper(it) }
        BoxWithConstraints(Modifier.fillMaxSize().padding(10.dp)) {
            val dockIcon = (maxWidth / 9f).coerceAtMost(28.dp)
            val dock: @Composable () -> Unit = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (CocoonHomeStore.dockBackground)
                                Modifier.background(CocoonSurface.Chip, RoundedCornerShape(50))
                            else Modifier
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(5) { i ->
                        Box(
                            Modifier
                                .size(dockIcon)
                                .cocoonDockIconClip()
                                .background(CocoonFolderPalette.top(i + 3))
                        )
                    }
                }
            }
            Column(Modifier.fillMaxSize()) {
                if (!CocoonHomeStore.dockBottom) {
                    dock()
                    Spacer(Modifier.height(8.dp))
                }
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    val gap = 8.dp
                    val byHeight = (maxHeight - gap * (rows - 1)) / rows
                    val byWidth = (maxWidth - gap * (columns - 1)) / columns
                    val tile: Dp = minOf(byHeight, byWidth) * 0.9f
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        repeat(columns) { c ->
                            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                                repeat(rows) { r ->
                                    val index = c * rows + r
                                    CocoonFolderIcon(
                                        colorIndex = index,
                                        glyph = CocoonGlyph.PLATFORM,
                                        platformSlug = PREVIEW_SLUGS[index % PREVIEW_SLUGS.size],
                                        size = tile,
                                        modifier = Modifier.cocoonBrackets(focused = index == cursor, outset = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                if (CocoonHomeStore.dockBottom) {
                    Spacer(Modifier.height(8.dp))
                    dock()
                }
            }
        }
        if (showPanel) {
            // a menu card over the board, so solid vs glass panels can be compared
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.62f)
                    // the same fill Cocoon's pop-up menus use, so Solid and Glass compare honestly
                    .background(com.nendo.argosy.ui.cocoon.CocoonMenuPanel, RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                repeat(4) { i ->
                    Box(
                        Modifier
                            .fillMaxWidth(if (i == 0) 0.5f else 0.85f)
                            .height(if (i == 0) 10.dp else 8.dp)
                            .background(CocoonSurface.TextPrimary.copy(alpha = if (i == 0) 0.85f else 0.35f), RoundedCornerShape(50))
                    )
                }
            }
        }
    }
}

/**
 * On Screen Home Menu: the handle drawn on a stand-in game frame, at the size and in the corner
 * the setting picks. It reads the same store as the real handle, so every press redraws it.
 */
@Composable
private fun HandlePreview(modifier: Modifier, framed: Boolean = true) {
    val x = CocoonHomeStore.handleX
    val y = CocoonHomeStore.handleY
    val clarity = CocoonHomeStore.handleClarity
    val box = com.nendo.argosy.ui.cocoon.cocoonHandleTargetDp().dp
    val glyph = com.nendo.argosy.ui.cocoon.cocoonHandleIconDp().dp
    PreviewFrame(16f / 9f, modifier, framed) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(Color(0xFF1B2A4A), Color(0xFF35607A), Color(0xFF7FA98C)))
            )
        )
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .offset(x = (maxWidth - box) * x, y = (maxHeight - box) * y)
                    .size(box),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Home,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = clarity),
                    modifier = Modifier.size(glyph)
                )
            }
        }
    }
}

@Composable
private fun TopScreenPreview(modifier: Modifier, framed: Boolean = true) {
    val heroStyle = CocoonHomeStore.heroStyle
    val share = CocoonHomeStore.heroShare
    val nowPlaying = CocoonHomeStore.nowPlaying
    PreviewFrame(16f / 9f, modifier, framed) {
        // stand-in game art: a sunset gradient
        if (heroStyle != "NONE") {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(Color(0xFF2B1B5A), Color(0xFFB3417A), Color(0xFFF2A65A)))
                )
            )
            if (heroStyle == "VIGNETTE") {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.radialGradient(
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.92f)
                        )
                    )
                )
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize().padding(10.dp)) {
            // the focused folder's card, sized by Hero size
            val cardWidth = maxWidth * share
            val frameHeight = maxHeight
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(cardWidth),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CocoonFolderIcon(
                    colorIndex = 3,
                    glyph = CocoonGlyph.PLATFORM,
                    platformSlug = "snes",
                    size = (cardWidth * 0.55f).coerceAtMost(frameHeight * 0.55f)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Super Nintendo",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = CocoonSurface.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .background(CocoonSurface.Chip, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            NowPlayingPreview(nowPlaying, Modifier.align(Alignment.BottomStart))
        }
    }
}

@Composable
private fun NowPlayingPreview(style: CocoonNowPlayingStyle, modifier: Modifier) {
    val line: @Composable (Float, Float) -> Unit = { width, alpha ->
        Box(
            Modifier
                .width((60 * width).dp)
                .height(5.dp)
                .background(Color.White.copy(alpha = alpha), RoundedCornerShape(50))
        )
    }
    when (style) {
        CocoonNowPlayingStyle.OFF -> Unit
        CocoonNowPlayingStyle.MINIMAL -> Row(
            modifier.background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(6.dp).background(Color(0xFF6DD58C), CircleShape))
            Spacer(Modifier.width(6.dp))
            line(1f, 0.8f)
        }
        CocoonNowPlayingStyle.ARTWORK -> Row(
            modifier.background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp)).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(26.dp).background(Color(0xFF6DB8FF), RoundedCornerShape(6.dp)))
            Spacer(Modifier.width(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                line(1f, 0.85f)
                line(0.6f, 0.45f)
            }
        }
        CocoonNowPlayingStyle.DASHBOARD -> Column(
            modifier
                .fillMaxWidth(0.5f)
                .background(CocoonSurface.Panel, RoundedCornerShape(10.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp).background(Color(0xFF6DB8FF), RoundedCornerShape(5.dp)))
                Spacer(Modifier.width(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    line(0.9f, 0.85f)
                    line(0.5f, 0.45f)
                }
            }
            Box(Modifier.fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(50))) {
                Box(Modifier.fillMaxWidth(0.4f).fillMaxHeight().background(Color.White, RoundedCornerShape(50)))
            }
        }
    }
}
