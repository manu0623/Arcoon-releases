package com.nendo.argosy.ui.cocoon.theme

import android.os.Build
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

private val VIDEO = setOf("mp4", "webm", "mkv", "mov", "3gp", "m4v")

/** Draws a theme wallpaper: a still image, an animated GIF/WebP, or a looping silent video. */
@Composable
fun CocoonThemeWallpaper(file: File, modifier: Modifier = Modifier) {
    if (file.extension.lowercase() in VIDEO) {
        ThemeVideo(file, modifier)
        return
    }
    val context = LocalContext.current
    val request = remember(file.path, file.lastModified()) {
        coil.request.ImageRequest.Builder(context)
            .data(file)
            .crossfade(false)
            .decoderFactory(
                if (Build.VERSION.SDK_INT >= 28) coil.decode.ImageDecoderDecoder.Factory()
                else coil.decode.GifDecoder.Factory()
            )
            .build()
    }
    coil.compose.AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize()
    )
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun ThemeVideo(file: File, modifier: Modifier) {
    val context = LocalContext.current
    val player = remember(file.path) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(android.net.Uri.fromFile(file)))
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            androidx.media3.ui.PlayerView(ctx).apply {
                useController = false
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                this.player = player
            }
        }
    )
}
