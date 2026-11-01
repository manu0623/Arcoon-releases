package com.nendo.argosy.ui.cocoon

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap

/**
 * App icons drawn straight from a shared in-memory cache, so a screen that shows the dock (the
 * board, every folder) has the icons on its very first frame instead of loading them again —
 * the async load was what made the dock blink when a folder opened.
 */
object CocoonAppIconCache {
    private val cache = android.util.LruCache<String, ImageBitmap>(160)

    fun clear() = cache.evictAll()

    fun get(context: android.content.Context, packageName: String): ImageBitmap? {
        cache.get(packageName)?.let { return it }
        val bitmap = runCatching {
            context.packageManager.getApplicationIcon(packageName).toBitmap(192, 192).asImageBitmap()
        }.getOrNull() ?: return null
        cache.put(packageName, bitmap)
        return bitmap
    }
}

@Composable
fun CocoonAppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    CocoonAppStore.version.intValue // redraw when the icon is changed
    val custom = CocoonAppStore.icon(packageName)
    if (custom != null) {
        coil.compose.AsyncImage(model = java.io.File(custom), contentDescription = null, modifier = modifier)
        return
    }
    val bitmap = remember(packageName) { CocoonAppIconCache.get(context, packageName) }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier)
    } else {
        coil.compose.AsyncImage(
            model = com.nendo.argosy.ui.coil.AppIconData(packageName),
            contentDescription = null,
            modifier = modifier
        )
    }
}
