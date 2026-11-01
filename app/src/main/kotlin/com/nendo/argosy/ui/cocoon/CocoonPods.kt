package com.nendo.argosy.ui.cocoon

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.CocoonCursorHost
import com.nendo.argosy.ui.primitives.cocoonBrackets
import com.nendo.argosy.ui.theme.CocoonSurface
import com.nendo.argosy.ui.util.clickableNoFocus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val SHOT_COLUMNS = 4
private val IMAGE_EXT = setOf("png", "jpg", "jpeg", "webp")

/** The device's screenshot folders, newest first (Cocoon's Picnic Pod). */
private fun findScreenshots(): List<File> {
    val roots = listOf(
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Screenshots"),
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Screenshots"),
        File(Environment.getExternalStorageDirectory(), "RetroArch/screenshots"),
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "RetroArch"),
    )
    return roots.filter { it.isDirectory }
        .flatMap { root -> root.walkTopDown().maxDepth(2).filter { it.isFile && it.extension.lowercase() in IMAGE_EXT }.toList() }
        .sortedByDescending { it.lastModified() }
}

/** Which game a screenshot is from, read from its file name (RetroArch, Android) or folder. */
private fun shotGroup(file: File): String {
    val name = file.nameWithoutExtension
    // RetroArch: "Game Name-251031-160349"
    Regex("^(.*)-\\d{6}-\\d{6}$").find(name)?.let { return it.groupValues[1].trim() }
    // Android: "Screenshot_20261031-160349_App Name"
    if (name.startsWith("Screenshot_")) {
        val tail = name.substringAfterLast('_', "")
        if (tail.isNotBlank() && !tail.all { it.isDigit() || it == '-' }) return tail
    }
    return file.parentFile?.name ?: "Screenshots"
}

private object ShotFavorites {
    private const val PREFS = "cocoon_screenshots"
    val version = androidx.compose.runtime.mutableIntStateOf(0)
    fun all(context: android.content.Context): Set<String> =
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getStringSet("fav", emptySet()).orEmpty()
    fun toggle(context: android.content.Context, path: String) {
        val p = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        val set = p.getStringSet("fav", emptySet()).orEmpty().toMutableSet()
        if (!set.add(path)) set.remove(path)
        p.edit().putStringSet("fav", set).apply()
        version.intValue++
    }
}

private fun shareShot(context: android.content.Context, file: File) {
    runCatching {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            android.content.Intent.createChooser(send, null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/**
 * Screenshots Pod (Cocoon's Picnic): L1/R1 switch between All, Favorites and each game; A opens a
 * shot full screen (←/→ step), Y favourites it, X shares it, B goes back.
 */
@Composable
fun CocoonScreenshotsPod(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val allShots by produceState(initialValue = emptyList<File>()) {
        value = withContext(Dispatchers.IO) { runCatching { findScreenshots() }.getOrDefault(emptyList()) }
    }
    val favVersion = ShotFavorites.version.intValue
    val favorites = remember(favVersion) { ShotFavorites.all(context) }
    val groups = remember(allShots) { allShots.groupBy { shotGroup(it) }.toList().sortedByDescending { (_, list) -> list.first().lastModified() } }
    // tabs: All, Favorites, then one per game
    val allLabel = stringResource(R.string.cocoon_shots_all)
    val favLabel = stringResource(R.string.cocoon_shots_favorites)
    val tabs = remember(groups, allLabel, favLabel) { listOf(allLabel, favLabel) + groups.map { it.first } }
    var tab by remember { mutableIntStateOf(0) }
    val shots = remember(tab, allShots, favorites, groups) {
        when (tab) {
            0 -> allShots
            1 -> allShots.filter { it.absolutePath in favorites }
            else -> groups.getOrNull(tab - 2)?.second.orEmpty()
        }
    }
    var focus by remember { mutableIntStateOf(0) }
    var viewing by remember { mutableStateOf(false) }
    if (focus > shots.lastIndex) focus = shots.lastIndex.coerceAtLeast(0)
    val dismiss by rememberUpdatedState(onDismiss)
    val shotsNow by rememberUpdatedState(shots)
    val tabsNow by rememberUpdatedState(tabs)
    val handler = remember {
        object : InputHandler {
            fun step(delta: Int, sameRow: Boolean): InputResult {
                val list = shotsNow
                val next = focus + delta
                if (next !in list.indices || (!viewing && sameRow && next / SHOT_COLUMNS != focus / SHOT_COLUMNS)) {
                    return InputResult.handled(SoundType.BOUNDARY)
                }
                focus = next
                return InputResult.HANDLED
            }
            fun switchTab(delta: Int): InputResult {
                val next = tab + delta
                if (next !in tabsNow.indices) return InputResult.handled(SoundType.BOUNDARY)
                tab = next
                focus = 0
                viewing = false
                return InputResult.HANDLED
            }
            override fun onUp() = if (viewing) InputResult.HANDLED else step(-SHOT_COLUMNS, false)
            override fun onDown() = if (viewing) InputResult.HANDLED else step(SHOT_COLUMNS, false)
            override fun onLeft() = step(-1, true)
            override fun onRight() = step(1, true)
            override fun onPrevSection() = switchTab(-1)
            override fun onNextSection() = switchTab(1)
            override fun onConfirm(): InputResult {
                if (shotsNow.isNotEmpty()) viewing = !viewing
                return InputResult.handled(SoundType.SELECT)
            }
            override fun onBack(): InputResult {
                if (viewing) viewing = false else dismiss()
                return InputResult.HANDLED
            }
            override fun onMenu(): InputResult = InputResult.handled(SoundType.SILENT)
            override fun onSecondaryAction(): InputResult {
                shotsNow.getOrNull(focus)?.let { ShotFavorites.toggle(context, it.absolutePath) }
                return InputResult.handled(SoundType.FAVORITE)
            }
            override fun onContextMenu(): InputResult {
                shotsNow.getOrNull(focus)?.let { shareShot(context, it) }
                return InputResult.HANDLED
            }
        }
    }
    ModalInputEffect(active = true, handler = handler)
    val grid = rememberLazyGridState()
    LaunchedEffect(focus, tab) {
        val visible = grid.layoutInfo.visibleItemsInfo
        if (visible.isEmpty() || focus < visible.first().index || focus > visible.last().index) {
            runCatching { grid.animateScrollToItem(focus) }
        }
    }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black).clickableNoFocus { }) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = CocoonSurface.TextPrimary, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.cocoon_pod_screenshots), style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold, color = CocoonSurface.TextPrimary)
                Spacer(Modifier.weight(1f))
                com.nendo.argosy.ui.primitives.InputGlyph(com.nendo.argosy.ui.components.InputButton.LB, size = 18.dp, tint = CocoonSurface.TextSecondary)
                Text(
                    tabs.getOrNull(tab).orEmpty(),
                    color = CocoonSurface.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
                com.nendo.argosy.ui.primitives.InputGlyph(com.nendo.argosy.ui.components.InputButton.RB, size = 18.dp, tint = CocoonSurface.TextSecondary)
            }
            Spacer(Modifier.height(14.dp))
            if (shots.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.cocoon_pod_screenshots_empty), color = CocoonSurface.TextSecondary)
                }
            } else {
                CocoonCursorHost(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(SHOT_COLUMNS),
                        state = grid,
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(shots.size, key = { shots[it].absolutePath }) { index ->
                            Box {
                                coil.compose.AsyncImage(
                                    model = shots[index],
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                        .cocoonBrackets(focused = index == focus, outset = 3.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickableNoFocus { focus = index; viewing = true }
                                )
                                if (shots[index].absolutePath in favorites) {
                                    Icon(
                                        androidx.compose.material.icons.Icons.Filled.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFFFD54F),
                                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                ShotHint(com.nendo.argosy.ui.components.InputButton.A, stringResource(R.string.cocoon_shots_view))
                ShotHint(com.nendo.argosy.ui.components.InputButton.Y, stringResource(R.string.cocoon_shots_favorite))
                ShotHint(com.nendo.argosy.ui.components.InputButton.X, stringResource(R.string.cocoon_shots_share))
                ShotHint(com.nendo.argosy.ui.components.InputButton.B, stringResource(R.string.cocoon_back))
            }
        }
        if (viewing) {
            shots.getOrNull(focus)?.let { file ->
                Box(Modifier.fillMaxSize().background(Color.Black).clickableNoFocus { viewing = false }) {
                    coil.compose.AsyncImage(
                        model = file,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                    Text(
                        shotGroup(file),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.BottomStart).padding(18.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ShotHint(button: com.nendo.argosy.ui.components.InputButton, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        com.nendo.argosy.ui.primitives.InputGlyph(button, size = 18.dp, tint = CocoonSurface.TextSecondary)
        Spacer(Modifier.width(6.dp))
        Text(label, color = CocoonSurface.TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

/** Cocoon's Pods button (the interlinked-rings icon at the right end of the dock). */
object CocoonPods {
    val menuOpen = androidx.compose.runtime.mutableStateOf(false)
    fun open() { menuOpen.value = true }
    val logOpen = androidx.compose.runtime.mutableStateOf(false)
    fun openLog() { logOpen.value = true }
}

private enum class PodRow(val labelRes: Int) {
    LOG(R.string.cocoon_pod_log),
    SCREENSHOTS(R.string.cocoon_pod_screenshots),
    THEMES(R.string.cocoon_themes_title),
    STORE(R.string.cocoon_store_title),
}

/**
 * The Pods menu: Log (play history), Screenshots and Themes (Arcoon's stand-in for Cocoon's Silk
 * store). Shown over the board or a folder when the dock's Pods button is pressed.
 */
@Composable
fun CocoonPodsOverlay(onNavigate: (String) -> Unit) {
    val open by CocoonPods.menuOpen
    var screenshots by remember { mutableStateOf(false) }
    var store by remember { mutableStateOf(false) }
    val log by CocoonPods.logOpen
    if (open) CocoonPodsMenu(
        onPick = { row ->
            CocoonPods.menuOpen.value = false
            when (row) {
                PodRow.LOG -> CocoonPods.openLog()
                PodRow.SCREENSHOTS -> screenshots = true
                PodRow.STORE -> store = true
                PodRow.THEMES -> {
                    com.nendo.argosy.ui.cocoon.theme.CocoonThemeEngine.pickerOpen.value = true
                    onNavigate(
                        com.nendo.argosy.ui.navigation.Screen.Settings.createRoute(
                            com.nendo.argosy.ui.screens.settings.SettingsSection.THEME.name
                        )
                    )
                }
            }
        },
        onDismiss = { CocoonPods.menuOpen.value = false }
    )
    if (screenshots) CocoonScreenshotsPod(onDismiss = { screenshots = false })
    if (log) CocoonLogPod(onDismiss = { CocoonPods.logOpen.value = false })
    if (store) com.nendo.argosy.ui.cocoon.theme.CocoonThemeStoreScreen(onDismiss = { store = false })
}

@Composable
private fun CocoonPodsMenu(onPick: (PodRow) -> Unit, onDismiss: () -> Unit) {
    var focus by remember { mutableIntStateOf(0) }
    val rows = PodRow.entries
    val pick by rememberUpdatedState(onPick)
    val dismiss by rememberUpdatedState(onDismiss)
    val handler = remember {
        object : InputHandler {
            fun step(delta: Int): InputResult {
                val next = focus + delta
                if (next !in rows.indices) return InputResult.handled(SoundType.BOUNDARY)
                focus = next
                return InputResult.HANDLED
            }
            override fun onUp() = step(-1)
            override fun onDown() = step(1)
            override fun onLeft(): InputResult = InputResult.HANDLED
            override fun onRight(): InputResult = InputResult.HANDLED
            override fun onConfirm(): InputResult {
                pick(rows[focus])
                return InputResult.handled(SoundType.SELECT)
            }
            override fun onBack(): InputResult {
                dismiss()
                return InputResult.HANDLED
            }
            override fun onMenu(): InputResult = InputResult.handled(SoundType.SILENT)
            override fun onSecondaryAction(): InputResult = InputResult.HANDLED
            override fun onContextMenu(): InputResult = InputResult.HANDLED
        }
    }
    ModalInputEffect(active = true, handler = handler)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickableNoFocus(onClick = onDismiss)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(0.4f), horizontalAlignment = Alignment.CenterHorizontally) {
                CocoonPodsGlyph(modifier = Modifier.size(84.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.cocoon_pods_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = CocoonSurface.TextPrimary
                )
            }
            Column(modifier = Modifier.weight(0.6f).padding(horizontal = 8.dp)) {
                rows.forEachIndexed { index, row ->
                    CocoonMenuRow(
                        icon = when (row) {
                            PodRow.LOG -> androidx.compose.material.icons.Icons.Filled.Insights
                            PodRow.SCREENSHOTS -> Icons.Filled.PhotoLibrary
                            PodRow.THEMES -> androidx.compose.material.icons.Icons.Filled.Palette
                            PodRow.STORE -> androidx.compose.material.icons.Icons.Filled.Storefront
                        },
                        label = stringResource(row.labelRes),
                        focused = index == focus,
                        isFirst = index == 0,
                        isLast = index == rows.lastIndex,
                        onClick = { focus = index; pick(row) },
                    )
                }
            }
        }
    }
}

/** Cocoon's Pods mark: four rings linked around a grey centre. */
@Composable
fun CocoonPodsGlyph(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.minDimension
        val c = center
        val r = w * 0.24f
        val d = w * 0.2f
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.09f)
        drawCircle(Color(0xFF8E8E93), w * 0.2f, c)
        for ((dx, dy) in listOf(0f to -d, d to 0f, 0f to d, -d to 0f)) {
            drawCircle(Color.White, r, androidx.compose.ui.geometry.Offset(c.x + dx, c.y + dy), style = stroke)
        }
    }
}
