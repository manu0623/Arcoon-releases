package com.nendo.argosy.ui.cocoon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.History
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp

/** What a board tile is drawn with: a platform's own glyph, or a built-in symbol. */
/**
 * What a board tile draws. APP, POD and WIDGET tiles carry their payload (package name, Pod or
 * widget kind) in the tile's platformSlug field, so the top-screen hero can draw them too.
 */
enum class CocoonGlyph { PLATFORM, SETTINGS, APPS, EMULATORS, COLLECTION, FOLDER, APP, POD, WIDGET, GAME, SMART, CUSTOM }

/**
 * Cocoon's folder colours: saturated pastels, each a top and a bottom stop. A folder keeps its
 * colour by index, so the same platform is always the same colour.
 */
object CocoonFolderPalette {
    private val stops: List<Pair<Color, Color>> = listOf(
        // Cocoon's folders are saturated: a bright top running into a deeper, slightly shifted hue.
        Color(0xFFA27BFF) to Color(0xFF8B2BE8), // lilac
        Color(0xFFFF8798) to Color(0xFFEF3A50), // coral
        Color(0xFF8E7DFF) to Color(0xFF5A3BF6), // violet
        Color(0xFF6DB8FF) to Color(0xFF2F7CF4), // sky
        Color(0xFFA6ABB6) to Color(0xFF5E6470), // slate
        Color(0xFF5FE8B6) to Color(0xFF12B87C), // mint
        Color(0xFFFFB35C) to Color(0xFFFF7818), // orange
        Color(0xFFFF9DC4) to Color(0xFFF04E8C), // pink
        Color(0xFF6E84FF) to Color(0xFF2F47F2), // indigo
        Color(0xFF52DAEE) to Color(0xFF0EA6CA), // teal
        Color(0xFFFFDB57) to Color(0xFFF3A70E), // sunflower
    )

    val size: Int get() = stops.size

    // Icon Style: every folder in the theme's accent, or flat grey, instead of its own colour.
    private val accent: Color get() = com.nendo.argosy.ui.cocoon.theme.CocoonThemeEngine.colors?.accent ?: Color(0xFF8E7DFF)

    fun top(index: Int): Color = when (CocoonHomeStore.iconStyle) {
        "ACCENT" -> accent
        "FLAT" -> Color(0xFF4A4A52)
        else -> stops[index.mod(stops.size)].first
    }

    fun bottom(index: Int): Color = when (CocoonHomeStore.iconStyle) {
        "ACCENT" -> androidx.compose.ui.graphics.lerp(accent, Color.Black, 0.35f)
        "FLAT" -> Color(0xFF2C2C32)
        else -> stops[index.mod(stops.size)].second
    }

    /** A stable colour for a platform slug, so a folder does not change colour between launches. */
    fun indexFor(key: String): Int = (key.hashCode() and 0x7fffffff) % stops.size

    const val SETTINGS = 4
    const val APPS = 5
    const val EMULATORS = 6
}

/**
 * One Cocoon folder: a rounded square body under a lighter tab, a vertical gradient, and a white
 * glyph centred on the body. Drawn in code, so no Cocoon artwork ships with the app.
 */
/** Folder icon art decodes at this size whatever size the folder is drawn at. */
private const val COCOON_FOLDER_ART_PX = 320

@Composable
fun CocoonFolderIcon(
    colorIndex: Int,
    glyph: CocoonGlyph,
    platformSlug: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    showTab: Boolean = glyph != CocoonGlyph.SETTINGS && glyph != CocoonGlyph.APPS,
) {
    // A theme's folder art (smart_folders/…/icon.png) replaces the drawn folder.
    val theme = com.nendo.argosy.ui.cocoon.theme.CocoonThemeEngine.active.value
    val packs = com.nendo.argosy.ui.cocoon.theme.CocoonThemeEngine.packs.value
    val art = remember(theme?.id, packs, glyph, platformSlug) {
        com.nendo.argosy.ui.cocoon.theme.CocoonThemeEngine.folderArt(glyph, platformSlug, "icon")
    }
    // Icon packs (and theme folder icons) sit on the folder, in place of the drawn console mark.
    // Home shortcuts draw themselves: an app's own icon, a Pod, or a live widget.
    when (glyph) {
        CocoonGlyph.APP -> { CocoonAppTileIcon(platformSlug.orEmpty(), size, modifier); return }
        CocoonGlyph.POD -> { CocoonPodIcon(platformSlug, size, modifier); return }
        CocoonGlyph.WIDGET -> { CocoonWidgetTile(platformSlug, size, modifier); return }
        CocoonGlyph.GAME -> { CocoonGameTileIcon(platformSlug, size, modifier); return }
        CocoonGlyph.CUSTOM -> {
            // folder appearance chosen by the user: "image:<path>" or "symbol:<name>"
            val payload = platformSlug.orEmpty()
            if (payload.startsWith("image:")) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val request = remember(payload) {
                    coil.request.ImageRequest.Builder(context)
                        .data(java.io.File(payload.removePrefix("image:"))).size(COCOON_FOLDER_ART_PX).build()
                }
                coil.compose.AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = modifier.size(size)
                )
                return
            }
        }
        else -> Unit
    }
    val top = CocoonFolderPalette.top(colorIndex)
    val bottom = CocoonFolderPalette.bottom(colorIndex)
    Box(modifier = modifier.size(size)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val radius = w * 0.2f
            val tabTop = if (showTab) h * 0.04f else 0f
            val bodyTop = if (showTab) h * 0.13f else 0f
            if (showTab) {
                drawRoundRect(
                    color = lerp(top, Color.White, 0.18f),
                    topLeft = Offset(w * 0.02f, tabTop),
                    size = Size(w * 0.46f, h * 0.3f),
                    cornerRadius = CornerRadius(radius * 0.7f)
                )
            }
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(top, bottom), startY = bodyTop, endY = h),
                topLeft = Offset(0f, bodyTop),
                size = Size(w, h - bodyTop),
                cornerRadius = CornerRadius(radius)
            )
        }
        val glyphSize = size * 0.54f
        val glyphModifier = Modifier
            .align(Alignment.Center)
            .offset(y = if (showTab) size * 0.05f else size * 0f)
            .size(glyphSize)
        if (art != null) {
            // Decoded at one fixed size: when the folder is highlighted it grows, and a request that
            // followed the on-screen size would reload the icon and blink. This one stays cached.
            val context = androidx.compose.ui.platform.LocalContext.current
            val request = remember(art) {
                coil.request.ImageRequest.Builder(context).data(art).size(COCOON_FOLDER_ART_PX).build()
            }
            coil.compose.AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = if (showTab) size * 0.05f else 0.dp)
                    .size(size * 0.66f)
            )
        } else when (glyph) {
            CocoonGlyph.PLATFORM -> PlatformGlyph(platformSlug, glyphModifier)
            CocoonGlyph.CUSTOM -> Icon(
                imageVector = cocoonFolderSymbol(platformSlug.orEmpty().removePrefix("symbol:")),
                contentDescription = null,
                tint = Color.White,
                modifier = glyphModifier
            )
            CocoonGlyph.SMART -> Icon(
                imageVector = when (platformSlug) {
                    CocoonSmartKind.RECENT.name -> androidx.compose.material.icons.Icons.Filled.History
                    CocoonSmartKind.MOST_PLAYED.name -> androidx.compose.material.icons.Icons.Filled.TrendingUp
                    CocoonSmartKind.NEWLY_ADDED.name -> androidx.compose.material.icons.Icons.Filled.NewReleases
                    else -> androidx.compose.material.icons.Icons.Filled.Star
                },
                contentDescription = null,
                tint = Color.White,
                modifier = glyphModifier
            )
            else -> Icon(
                imageVector = glyph.vector(),
                contentDescription = null,
                tint = Color.White,
                modifier = glyphModifier
            )
        }
    }
}

@Composable
private fun PlatformGlyph(platformSlug: String?, modifier: Modifier) {
    val shape = remember(platformSlug) { consoleShapeFor(platformSlug) }
    ConsoleGlyph(shape = shape, modifier = modifier)
}

private fun CocoonGlyph.vector(): ImageVector = when (this) {
    CocoonGlyph.SETTINGS -> Icons.Filled.Settings
    CocoonGlyph.APPS -> Icons.Filled.Apps
    CocoonGlyph.EMULATORS -> Icons.Filled.Memory
    CocoonGlyph.COLLECTION -> Icons.Filled.Star
    CocoonGlyph.FOLDER, CocoonGlyph.PLATFORM -> Icons.Filled.Folder
    CocoonGlyph.APP, CocoonGlyph.POD, CocoonGlyph.WIDGET, CocoonGlyph.GAME -> Icons.Filled.Apps
    CocoonGlyph.SMART -> Icons.Filled.Star
    CocoonGlyph.CUSTOM -> Icons.Filled.Folder
}


/** The symbols a folder can wear (Y ▸ Appearance ▸ Symbol). */
val COCOON_FOLDER_SYMBOLS = listOf("folder", "star", "heart", "bolt", "trophy", "gamepad", "music", "book", "flag", "rocket", "pets", "puzzle")

fun cocoonFolderSymbol(name: String): ImageVector = when (name) {
    "star" -> Icons.Filled.Star
    "heart" -> androidx.compose.material.icons.Icons.Filled.Favorite
    "bolt" -> androidx.compose.material.icons.Icons.Filled.Bolt
    "trophy" -> androidx.compose.material.icons.Icons.Filled.EmojiEvents
    "gamepad" -> androidx.compose.material.icons.Icons.Filled.SportsEsports
    "music" -> androidx.compose.material.icons.Icons.Filled.MusicNote
    "book" -> androidx.compose.material.icons.Icons.Filled.Book
    "flag" -> androidx.compose.material.icons.Icons.Filled.Flag
    "rocket" -> androidx.compose.material.icons.Icons.Filled.RocketLaunch
    "pets" -> androidx.compose.material.icons.Icons.Filled.Pets
    "puzzle" -> androidx.compose.material.icons.Icons.Filled.Extension
    else -> Icons.Filled.Folder
}
