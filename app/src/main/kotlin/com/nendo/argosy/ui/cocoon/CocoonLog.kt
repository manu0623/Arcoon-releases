package com.nendo.argosy.ui.cocoon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.theme.CocoonSurface
import com.nendo.argosy.ui.util.clickableNoFocus
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CocoonLogEntryPoint {
    fun playSessionDao(): com.nendo.argosy.data.local.dao.PlaySessionDao
    fun imageCacheManager(): com.nendo.argosy.data.cache.ImageCacheManager
}

private data class LogDay(val label: String, val minutes: Long)
private data class LogTop(val title: String, val minutes: Long, val cover: String?)
private data class LogData(
    val weekMinutes: Long = 0,
    val days: List<LogDay> = emptyList(),
    val top: List<LogTop> = emptyList(),
    val sessions: Int = 0,
)

private fun minutesOf(start: java.time.Instant, end: java.time.Instant): Long =
    java.time.Duration.between(start, end).toMinutes().coerceAtLeast(0)

private fun hm(minutes: Long): String = if (minutes >= 60) "%dh %02dm".format(minutes / 60, minutes % 60) else "${minutes}m"

/**
 * Cocoon's Log Pod: this week's playtime, a bar per day, and the games you played most in the
 * last 30 days (apps marked "Treat as Game" included). B closes it.
 */
@Composable
fun CocoonLogPod(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val data by produceState(LogData()) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val entry = EntryPointAccessors.fromApplication(context.applicationContext, CocoonLogEntryPoint::class.java)
                val zone = java.time.ZoneId.systemDefault()
                val today = java.time.LocalDate.now(zone)
                val monthAgo = java.time.Instant.now().minus(java.time.Duration.ofDays(30))
                val sessions = entry.playSessionDao().getSessionsSince(monthAgo)
                val apps = CocoonAppStore.sessionsSince(monthAgo.toEpochMilli())
                // (day, minutes) for games and apps alike
                val all = sessions.map { it.startTime.atZone(zone).toLocalDate() to minutesOf(it.startTime, it.endTime) } +
                    apps.map { java.time.Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() to it.minutes }
                val days = (6 downTo 0).map { back ->
                    val day = today.minusDays(back.toLong())
                    LogDay(
                        day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()),
                        all.filter { it.first == day }.sumOf { it.second }
                    )
                }
                val gameTop = sessions.groupBy { it.gameId }.map { (id, list) ->
                    val cover = runCatching {
                        (entry.imageCacheManager().artFor(id) ?: entry.imageCacheManager().loadArt(id))?.coverPath
                    }.getOrNull()
                    LogTop(list.first().gameTitle, list.sumOf { minutesOf(it.startTime, it.endTime) }, cover)
                }
                val pm = context.packageManager
                val appTop = apps.groupBy { it.pkg }.map { (pkg, list) ->
                    val name = CocoonAppStore.name(pkg)
                        ?: runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
                    LogTop(name, list.sumOf { it.minutes }, null)
                }
                LogData(
                    weekMinutes = days.sumOf { it.minutes },
                    days = days,
                    top = (gameTop + appTop).sortedByDescending { it.minutes }.take(6),
                    sessions = sessions.size + apps.size,
                )
            }.getOrDefault(LogData())
        }
    }
    val dismiss by rememberUpdatedState(onDismiss)
    val scroll = rememberScrollState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val handler = remember {
        object : InputHandler {
            override fun onUp(): InputResult { scope.launchScroll(scroll, -240); return InputResult.HANDLED }
            override fun onDown(): InputResult { scope.launchScroll(scroll, 240); return InputResult.HANDLED }
            override fun onLeft(): InputResult = InputResult.HANDLED
            override fun onRight(): InputResult = InputResult.HANDLED
            override fun onConfirm(): InputResult = InputResult.HANDLED
            override fun onBack(): InputResult { dismiss(); return InputResult.HANDLED }
            override fun onMenu(): InputResult = InputResult.handled(SoundType.SILENT)
            override fun onSecondaryAction(): InputResult = InputResult.HANDLED
            override fun onContextMenu(): InputResult = InputResult.HANDLED
        }
    }
    ModalInputEffect(active = true, handler = handler)
    val accent = com.nendo.argosy.ui.primitives.CocoonBracketSpec.color

    Box(modifier = Modifier.fillMaxSize().background(Color.Black).clickableNoFocus { }) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp).verticalScroll(scroll)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CocoonPodIcon(CocoonPodKind.LOG.name, 34.dp)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.cocoon_pod_log), style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold, color = CocoonSurface.TextPrimary)
            }
            Spacer(Modifier.height(18.dp))
            // this week
            Column(
                modifier = Modifier.fillMaxWidth().background(CocoonMenuPanel, RoundedCornerShape(20.dp)).padding(20.dp)
            ) {
                Text(stringResource(R.string.cocoon_log_this_week), color = CocoonSurface.TextSecondary, style = MaterialTheme.typography.labelLarge)
                Text(hm(data.weekMinutes), color = CocoonSurface.TextPrimary, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                val peak = (data.days.maxOfOrNull { it.minutes } ?: 0L).coerceAtLeast(1L)
                Row(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    data.days.forEach { day ->
                        Column(modifier = Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight((day.minutes.toFloat() / peak * 0.8f).coerceAtLeast(0.03f))
                                    .background(
                                        Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.35f))),
                                        RoundedCornerShape(8.dp)
                                    )
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(day.label, color = CocoonSurface.TextSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.cocoon_log_top), color = CocoonSurface.TextSecondary, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            if (data.top.isEmpty()) {
                Text(stringResource(R.string.cocoon_log_empty), color = CocoonSurface.TextSecondary)
            }
            data.top.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(CocoonMenuPanel, RoundedCornerShape(14.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${index + 1}", color = CocoonSurface.TextSecondary, modifier = Modifier.width(26.dp))
                    if (item.cover != null) {
                        coil.compose.AsyncImage(
                            model = item.cover.let { if (it.startsWith("/")) java.io.File(it) else it },
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.size(40.dp, 52.dp).background(Color(0xFF222226), RoundedCornerShape(6.dp))
                        )
                    } else {
                        Icon(Icons.Filled.Insights, null, tint = CocoonSurface.TextSecondary, modifier = Modifier.size(40.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(item.title, color = CocoonSurface.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(hm(item.minutes), color = CocoonSurface.TextPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.cocoon_log_sessions, data.sessions), color = CocoonSurface.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun kotlinx.coroutines.CoroutineScope.launchScroll(state: androidx.compose.foundation.ScrollState, by: Int) {
    launch { state.animateScrollTo((state.value + by).coerceIn(0, state.maxValue)) }
}
