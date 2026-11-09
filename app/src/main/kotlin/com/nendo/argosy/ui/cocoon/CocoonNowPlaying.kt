package com.nendo.argosy.ui.cocoon

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Personalization ▸ Now Playing Style: what the other screen shows while a game runs. */
enum class CocoonNowPlayingStyle(@StringRes val labelRes: Int) {
    ARTWORK(R.string.cocoon_np_artwork),
    MINIMAL(R.string.cocoon_np_minimal),
    DASHBOARD(R.string.cocoon_np_dashboard),
    OFF(R.string.cocoon_np_off),
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CocoonNowPlayingEntryPoint {
    fun gameDao(): com.nendo.argosy.data.local.dao.GameDao
    fun imageCacheManager(): com.nendo.argosy.data.cache.ImageCacheManager
}

private data class NowPlayingInfo(val title: String?, val background: String?, val logo: String?)

/**
 * Cocoon's Now Playing screen. Artwork: the game's art with its logo, how long you've played and
 * the time. Minimal: the same on black. Screen Off: black, for OLED.
 */
@Composable
fun CocoonNowPlaying(style: CocoonNowPlayingStyle) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dsm = com.nendo.argosy.DualScreenManagerHolder.instance
    val session by (dsm?.playSessionTracker?.activeSession
        ?: remember { kotlinx.coroutines.flow.MutableStateFlow(null) }).collectAsState()
    val gameId = session?.gameId
    var info by remember(gameId) { mutableStateOf(NowPlayingInfo(null, null, null)) }
    LaunchedEffect(gameId, context) {
        info = cocoonNowPlayingInfo(context, gameId)
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            now = System.currentTimeMillis()
        }
    }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (style == CocoonNowPlayingStyle.OFF) return@Box
        if (style == CocoonNowPlayingStyle.ARTWORK && info.background != null) {
            coil.compose.AsyncImage(
                model = info.background!!.let { if (it.startsWith("/")) java.io.File(it) else it },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))
                )
            )
        }
        Column(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val logo = info.logo
            if (style == CocoonNowPlayingStyle.ARTWORK && logo != null) {
                coil.compose.AsyncImage(
                    model = if (logo.startsWith("/")) java.io.File(logo) else logo,
                    contentDescription = info.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(width = 360.dp, height = 140.dp)
                )
            } else {
                Text(
                    info.title.orEmpty(),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(14.dp))
            val start = session?.startTime?.toEpochMilli()
            if (start != null) {
                val seconds = ((now - start) / 1000).coerceAtLeast(0)
                Text(
                    stringResource(R.string.cocoon_np_playing) + " · " +
                        if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
                        else "%d:%02d".format(seconds / 60, seconds % 60),
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
        Text(
            java.text.SimpleDateFormat(
                if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm",
                java.util.Locale.getDefault()
            ).format(java.util.Date(now)),
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.align(Alignment.TopEnd).padding(20.dp)
        )
    }
}

private suspend fun cocoonNowPlayingInfo(context: android.content.Context, gameId: Long?): NowPlayingInfo {
    val id = gameId ?: return NowPlayingInfo(null, null, null)
    return withContext(Dispatchers.IO) {
        runCatching {
            val entry = EntryPointAccessors.fromApplication(
                context.applicationContext,
                CocoonNowPlayingEntryPoint::class.java
            )
            val art = entry.imageCacheManager().artFor(id) ?: entry.imageCacheManager().loadArt(id)
            NowPlayingInfo(
                entry.gameDao().cocoonTitleOf(id),
                art.backgroundPath ?: art.coverPath,
                art.logoPath
            )
        }.getOrDefault(NowPlayingInfo(null, null, null))
    }
}
