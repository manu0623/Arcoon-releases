package com.nendo.argosy.ui.cocoon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/** A game a widget shows: its id (to open it) and its cover. */
data class CocoonWidgetGame(val id: Long, val title: String?, val cover: String?)

/** What the widgets draw; the board's view model fills it in. Shared by both screens. */
object CocoonWidgetData {
    val recent = mutableStateOf<List<CocoonWidgetGame>>(emptyList())
    val random = mutableStateOf<CocoonWidgetGame?>(null)
    val weeklyMinutes = mutableStateOf(0L)
}

/** The tile payload for a widget: "KIND|value". */
fun cocoonWidgetPayload(kind: CocoonWidgetKind, value: String?): String = kind.name + "|" + (value ?: "")

private fun parsePayload(payload: String?): Pair<CocoonWidgetKind, String?> {
    val parts = payload.orEmpty().split("|", limit = 2)
    val kind = CocoonWidgetKind.entries.firstOrNull { it.name == parts.getOrNull(0) } ?: CocoonWidgetKind.BLANK
    return kind to parts.getOrNull(1)?.takeIf { it.isNotBlank() }
}

private val WidgetShape = RoundedCornerShape(22)
private val WidgetBackground = Color(0xFF161618)

@Composable
fun CocoonWidgetTile(payload: String?, size: Dp, modifier: Modifier = Modifier, height: Dp = size) {
    val (kind, value) = remember(payload) { parsePayload(payload) }
    val width = size
    // text and marks scale with the smaller side, so a wide widget doesn't get huge text
    @Suppress("NAME_SHADOWING") val size = if (height < width) height else width
    val text = com.nendo.argosy.ui.theme.CocoonSurface.TextPrimary
    val dim = com.nendo.argosy.ui.theme.CocoonSurface.TextSecondary
    Box(
        modifier = modifier
            .size(width, height)
            .clip(WidgetShape)
            .background(if (kind == CocoonWidgetKind.BLANK) Color.Transparent else WidgetBackground),
        contentAlignment = Alignment.Center
    ) {
        when (kind) {
            CocoonWidgetKind.CLOCK -> {
                val now = rememberClock()
                val context = androidx.compose.ui.platform.LocalContext.current
                val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault()).format(java.util.Date(now)),
                        color = text,
                        fontSize = (size.value * 0.24f).sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.getDefault()).format(java.util.Date(now)),
                        color = dim,
                        fontSize = (size.value * 0.1f).sp
                    )
                }
            }
            CocoonWidgetKind.CLOCK_ANALOG -> {
                val now = rememberClock()
                Canvas(modifier = Modifier.fillMaxSize().padding(size * 0.12f)) {
                    val r = this.size.minDimension / 2f
                    val c = center
                    drawCircle(text.copy(alpha = 0.15f), radius = r)
                    for (i in 0 until 12) {
                        val a = Math.toRadians(i * 30.0)
                        val p1 = Offset(c.x + (r * 0.82f) * kotlin.math.sin(a).toFloat(), c.y - (r * 0.82f) * kotlin.math.cos(a).toFloat())
                        val p2 = Offset(c.x + (r * 0.92f) * kotlin.math.sin(a).toFloat(), c.y - (r * 0.92f) * kotlin.math.cos(a).toFloat())
                        drawLine(dim, p1, p2, strokeWidth = r * 0.04f, cap = StrokeCap.Round)
                    }
                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
                    val minutes = cal.get(java.util.Calendar.MINUTE) + cal.get(java.util.Calendar.SECOND) / 60f
                    val hours = (cal.get(java.util.Calendar.HOUR) + minutes / 60f)
                    fun hand(turns: Float, length: Float, width: Float, color: Color) {
                        val a = turns * 2 * Math.PI
                        drawLine(
                            color, c,
                            Offset(c.x + length * kotlin.math.sin(a).toFloat(), c.y - length * kotlin.math.cos(a).toFloat()),
                            strokeWidth = width, cap = StrokeCap.Round
                        )
                    }
                    hand(hours / 12f, r * 0.5f, r * 0.08f, text)
                    hand(minutes / 60f, r * 0.75f, r * 0.05f, text)
                    hand(cal.get(java.util.Calendar.SECOND) / 60f, r * 0.8f, r * 0.02f, com.nendo.argosy.ui.primitives.CocoonBracketSpec.color)
                }
            }
            CocoonWidgetKind.RECENT -> {
                val games by CocoonWidgetData.recent
                if (games.isEmpty()) WidgetLabel(Icons.Filled.History, size, dim) else {
                    // three covers fanned like a stack
                    Box(Modifier.fillMaxSize().padding(size * 0.1f)) {
                        games.take(3).reversed().forEachIndexed { i, game ->
                            val offset = size * (0.12f * (2 - i))
                            Cover(game.cover, Modifier.align(Alignment.CenterStart).padding(start = offset).size(size * 0.55f, size * 0.72f))
                        }
                    }
                }
            }
            CocoonWidgetKind.LAST_PLAYED -> {
                val game = CocoonWidgetData.recent.value.firstOrNull()
                if (game?.cover == null) WidgetLabel(Icons.Filled.History, size, dim)
                else Cover(game.cover, Modifier.fillMaxSize())
            }
            CocoonWidgetKind.RANDOM -> {
                val game by CocoonWidgetData.random
                Box(Modifier.fillMaxSize()) {
                    if (game?.cover != null) Cover(game?.cover, Modifier.fillMaxSize())
                    Icon(
                        Icons.Filled.Casino, contentDescription = null, tint = Color.White,
                        modifier = Modifier.align(Alignment.TopEnd).padding(size * 0.06f).size(size * 0.2f)
                            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50)).padding(size * 0.03f)
                    )
                }
            }
            CocoonWidgetKind.PLAYTIME -> {
                val minutes by CocoonWidgetData.weeklyMinutes
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Filled.Timer, contentDescription = null, tint = dim, modifier = Modifier.size(size * 0.18f))
                    Text(
                        text = if (minutes >= 60) "%dh %02dm".format(minutes / 60, minutes % 60) else "${minutes}m",
                        color = text, fontSize = (size.value * 0.17f).sp, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.nendo.argosy.R.string.cocoon_widget_this_week),
                        color = dim, fontSize = (size.value * 0.09f).sp, textAlign = TextAlign.Center
                    )
                }
            }
            CocoonWidgetKind.IMAGE_PIN -> {
                if (value != null && File(value).isFile) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    coil.compose.AsyncImage(
                        model = remember(value) {
                            coil.request.ImageRequest.Builder(context).data(File(value))
                                .decoderFactory(
                                    if (android.os.Build.VERSION.SDK_INT >= 28) coil.decode.ImageDecoderDecoder.Factory()
                                    else coil.decode.GifDecoder.Factory()
                                ).build()
                        },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else WidgetLabel(Icons.Filled.PhotoLibrary, size, dim)
            }
            CocoonWidgetKind.MUSIC -> {
                // Arcoon's own background music: what's playing, and A plays / pauses it.
                val context = androidx.compose.ui.platform.LocalContext.current
                val music = remember {
                    runCatching {
                        dagger.hilt.android.EntryPointAccessors.fromApplication(
                            context.applicationContext, CocoonJingleEntryPoint::class.java
                        ).ambientAudioManager()
                    }.getOrNull()
                }
                val playback by (music?.playback
                    ?: remember { kotlinx.coroutines.flow.MutableStateFlow(com.nendo.argosy.ui.audio.AmbientPlaybackState()) })
                    .collectAsState()
                Box(Modifier.fillMaxSize()) {
                    if (playback.coverPath != null) {
                        Cover(playback.coverPath, Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))))
                    }
                    Column(
                        modifier = Modifier.align(Alignment.BottomStart).padding(size * 0.08f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            if (playback.isPlaying) androidx.compose.material.icons.Icons.Filled.Pause
                            else androidx.compose.material.icons.Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = text,
                            modifier = Modifier.size(size * 0.2f)
                        )
                        Text(
                            playback.overrideTitle ?: playback.trackTitle
                                ?: androidx.compose.ui.res.stringResource(com.nendo.argosy.R.string.cocoon_widget_music_idle),
                            color = text,
                            fontSize = (size.value * 0.1f).sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        playback.gameTitle?.let {
                            Text(it, color = dim, fontSize = (size.value * 0.08f).sp, maxLines = 1)
                        }
                    }
                }
            }
            CocoonWidgetKind.BLANK -> Unit
        }
    }
}

@Composable
private fun rememberClock(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000L - System.currentTimeMillis() % 1000L)
            now = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
private fun Cover(path: String?, modifier: Modifier) {
    coil.compose.AsyncImage(
        model = path?.let { if (it.startsWith("/")) File(it) else it },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF222226))
    )
}

@Composable
private fun WidgetLabel(icon: ImageVector, size: Dp, tint: Color) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.36f))
}

/** A Pod on Home: a rounded square with the Pod's mark, like Cocoon's built-in app icons. */
@Composable
fun CocoonPodIcon(payload: String?, size: Dp, modifier: Modifier = Modifier) {
    val kind = CocoonPodKind.entries.firstOrNull { it.name == payload } ?: CocoonPodKind.LOG
    val (top, bottom, icon) = when (kind) {
        CocoonPodKind.LOG -> Triple(Color(0xFF7BE0FF), Color(0xFF2B8DF0), Icons.Filled.Insights)
        CocoonPodKind.SCREENSHOTS -> Triple(Color(0xFFFFC46B), Color(0xFFF07A2B), Icons.Filled.PhotoLibrary)
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(22))
            .background(Brush.verticalGradient(listOf(top, bottom))),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
    }
}

/** An app on Home: its own launcher icon. */
@Composable
fun CocoonAppTileIcon(packageName: String, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        CocoonAppIcon(packageName, Modifier.size(size * 0.86f))
    }
}


/** A game on Home: its cover (payload "id|coverPath"). */
@Composable
fun CocoonGameTileIcon(payload: String?, size: Dp, modifier: Modifier = Modifier) {
    val cover = payload.orEmpty().substringAfter("|", "").takeIf { it.isNotBlank() }
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Cover(cover, Modifier.size(size * 0.78f, size))
    }
}
