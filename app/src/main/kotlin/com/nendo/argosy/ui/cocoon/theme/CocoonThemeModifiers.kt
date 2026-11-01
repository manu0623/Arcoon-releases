package com.nendo.argosy.ui.cocoon.theme

import android.graphics.BitmapFactory
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntSize
import java.io.File

private val bitmapCache = java.util.concurrent.ConcurrentHashMap<String, ImageBitmap>()

private fun load(file: File?): ImageBitmap? {
    if (file == null) return null
    val key = file.absolutePath + ":" + file.lastModified()
    bitmapCache[key]?.let { return it }
    val bitmap = runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }.getOrNull() ?: return null
    bitmapCache[key] = bitmap
    return bitmap
}

/**
 * Cocoon icon overlays: the theme's `icon_overlays/<platform>/mask.png` cuts the cover to its
 * shape (white = visible) and `overlay.png` is drawn on top as a frame.
 */
fun Modifier.cocoonIconOverlay(platformSlug: String?): Modifier = composed {
    val themeId = CocoonThemeEngine.active.value?.id
    val packs = CocoonThemeEngine.packs.value
    val images = remember(themeId, packs, platformSlug) {
        CocoonThemeEngine.iconOverlay(platformSlug)?.let { (mask, overlay) -> load(mask) to load(overlay) }
    }
    if (images == null || (images.first == null && images.second == null)) return@composed this
    val (mask, overlay) = images
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val dst = IntSize(size.width.toInt(), size.height.toInt())
            if (mask != null) drawImage(image = mask, dstSize = dst, blendMode = BlendMode.DstIn)
            if (overlay != null) drawImage(image = overlay, dstSize = dst)
        }
}

/** The dock icon shape a theme asks for (dock_icon_shape), or null to leave icons as they are. */
fun cocoonDockIconShape(): Shape? = when (CocoonThemeEngine.settings.dockIconShape) {
    "CIRCLE" -> CircleShape
    "SQUIRCLE" -> RoundedCornerShape(percent = 30)
    "ROUNDED_SQUARE" -> RoundedCornerShape(percent = 18)
    "SQUARE" -> RectangleShape
    else -> null
}

fun Modifier.cocoonDockIconClip(): Modifier = composed {
    CocoonThemeEngine.active.value // re-read when the theme changes
    val shape = cocoonDockIconShape()
    if (shape == null) this else this.clip(shape)
}
