package com.nendo.argosy.ui.cocoon.theme

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
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

private enum class PersonalRow(val labelRes: Int) {
    CURSOR(R.string.cocoon_personal_cursor),
    THICKNESS(R.string.cocoon_personal_thickness),
    DOCK_SHAPE(R.string.cocoon_personal_dock_shape),
    GRID_ROWS(R.string.cocoon_personal_grid_rows),
    SURFACE(R.string.cocoon_personal_surface),
    ICON_STYLE(R.string.cocoon_personal_icon_style),
    MOTION(R.string.cocoon_personal_motion),
    HERO_STYLE(R.string.cocoon_personal_hero),
    HERO_SIZE(R.string.cocoon_personal_hero_size),
    NOW_PLAYING(R.string.cocoon_personal_now_playing),
    DOCK_POSITION(R.string.cocoon_personal_dock_position),
    DOCK_BACKGROUND(R.string.cocoon_personal_dock_background),
    DOCK_AUTOHIDE(R.string.cocoon_personal_dock_autohide),
    JINGLES(R.string.cocoon_jingles),
    JINGLE_VOLUME(R.string.cocoon_jingle_volume),
    JINGLE_DEFAULT(R.string.cocoon_jingle_default),
    JINGLE_REPOS(R.string.cocoon_jingle_repos),
    JINGLE_CLEAR(R.string.cocoon_jingle_clear),
    OPTIMIZE(R.string.cocoon_optimize_now),
    FREE_MEMORY(R.string.cocoon_free_memory),
    BACKUP(R.string.cocoon_backup),
    RESTORE(R.string.cocoon_restore),
}

private val CURSOR_STYLES = listOf(null, "ANIMATED", "OUTLINED", "ALTERNATIVE_ANIMATED", "ALTERNATIVE")
private val THICKNESSES = listOf(null, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1f)
private val DOCK_SHAPES = listOf(null, "CIRCLE", "SQUIRCLE", "ROUNDED_SQUARE", "SQUARE")

/** One height for every row, tall enough for a label or value on two lines. */
private val PERSONAL_ROW_HEIGHT = 76.dp

private fun <T> cycle(list: List<T>, current: T, delta: Int): T {
    val index = list.indexOf(current).coerceAtLeast(0)
    return list[(index + delta).mod(list.size)]
}

/**
 * Cocoon's Personalization page: cursor style, highlight thickness, dock icon shape and grid rows.
 * ←/→ (or A) change a value; "Theme" leaves it to the applied theme.
 */
@Composable
fun CocoonPersonalizationScreen(onDismiss: () -> Unit, jinglesOnly: Boolean = false) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var focus by remember { mutableIntStateOf(0) }
    var pickingJingle by remember { mutableStateOf(false) }
    var managingRepos by remember { mutableStateOf(false) }
    var backupStatus by remember { mutableStateOf<String?>(null) }
    com.nendo.argosy.ui.cocoon.CocoonJingles.version.intValue // redraw on jingle changes
    val personal by CocoonThemeEngine.personal
    val dismiss by rememberUpdatedState(onDismiss)
    // Jingles have their own page (Settings ▸ Theme ▸ Jingles), as in Cocoon.
    val jingleRows = setOf(
        PersonalRow.JINGLES, PersonalRow.JINGLE_VOLUME, PersonalRow.JINGLE_DEFAULT,
        PersonalRow.JINGLE_REPOS, PersonalRow.JINGLE_CLEAR
    )
    val rows = PersonalRow.entries.filter { (it in jingleRows) == jinglesOnly }

    fun change(row: PersonalRow, delta: Int) {
        val p = CocoonThemeEngine.personal.value
        when (row) {
            PersonalRow.CURSOR -> CocoonThemeEngine.setPersonal(p.copy(cursorStyle = cycle(CURSOR_STYLES, p.cursorStyle, delta)))
            PersonalRow.THICKNESS -> CocoonThemeEngine.setPersonal(p.copy(highlightThickness = cycle(THICKNESSES, p.highlightThickness, delta)))
            PersonalRow.DOCK_SHAPE -> CocoonThemeEngine.setPersonal(p.copy(dockIconShape = cycle(DOCK_SHAPES, p.dockIconShape, delta)))
            PersonalRow.GRID_ROWS -> com.nendo.argosy.ui.cocoon.CocoonHomeStore.setRows(
                context,
                listOf(2, 3, 4).let { cycle(it, com.nendo.argosy.ui.cocoon.CocoonHomeStore.rows, delta) }
            )
            PersonalRow.SURFACE -> CocoonThemeEngine.setPersonal(
                p.copy(glass = cycle(listOf<Boolean?>(null, false, true), p.glass, delta))
            )
            PersonalRow.ICON_STYLE -> com.nendo.argosy.ui.cocoon.CocoonHomeStore.setIconStyle(
                context, cycle(listOf("COLORED", "ACCENT", "FLAT"), com.nendo.argosy.ui.cocoon.CocoonHomeStore.iconStyle, delta)
            )
            PersonalRow.MOTION -> com.nendo.argosy.ui.cocoon.CocoonHomeStore.setMotion(
                context, cycle(listOf("COMFORTABLE", "SNAPPY", "REDUCED"), com.nendo.argosy.ui.cocoon.CocoonHomeStore.motion, delta)
            )
            PersonalRow.HERO_STYLE -> com.nendo.argosy.ui.cocoon.CocoonHomeStore.setHeroStyle(
                context, cycle(listOf("FULL", "VIGNETTE", "NONE"), com.nendo.argosy.ui.cocoon.CocoonHomeStore.heroStyle, delta)
            )
            PersonalRow.HERO_SIZE -> com.nendo.argosy.ui.cocoon.CocoonHomeStore.setHeroShare(
                context, cycle(listOf(0.3f, 0.45f, 0.6f), com.nendo.argosy.ui.cocoon.CocoonHomeStore.heroShare, delta)
            )
            PersonalRow.NOW_PLAYING -> com.nendo.argosy.ui.cocoon.CocoonHomeStore.setNowPlaying(
                context,
                cycle(com.nendo.argosy.ui.cocoon.CocoonNowPlayingStyle.entries.toList(), com.nendo.argosy.ui.cocoon.CocoonHomeStore.nowPlaying, delta)
            )
            PersonalRow.DOCK_POSITION -> com.nendo.argosy.ui.cocoon.CocoonHomeStore.let { it.setDock(context, bottom = !it.dockBottom) }
            PersonalRow.DOCK_BACKGROUND -> com.nendo.argosy.ui.cocoon.CocoonHomeStore.let { it.setDock(context, background = !it.dockBackground) }
            PersonalRow.DOCK_AUTOHIDE -> com.nendo.argosy.ui.cocoon.CocoonHomeStore.let { it.setDock(context, autoHide = !it.dockAutoHide) }
            PersonalRow.JINGLES -> com.nendo.argosy.ui.cocoon.CocoonJingles.setEnabled(
                context, !com.nendo.argosy.ui.cocoon.CocoonJingles.enabled(context)
            )
            PersonalRow.JINGLE_VOLUME -> com.nendo.argosy.ui.cocoon.CocoonJingles.setVolume(
                context, com.nendo.argosy.ui.cocoon.CocoonJingles.volume(context) + delta * 10
            )
            PersonalRow.JINGLE_DEFAULT -> {
                if (delta > 0) {
                    pickingJingle = true
                } else {
                    com.nendo.argosy.ui.cocoon.CocoonJingles.setGlobalJingle(context, null)
                }
            }
            PersonalRow.JINGLE_CLEAR -> com.nendo.argosy.ui.cocoon.CocoonJingles.clearGameJingles(context)
            PersonalRow.JINGLE_REPOS -> managingRepos = true
            PersonalRow.OPTIMIZE -> com.nendo.argosy.ui.cocoon.CocoonOptimizer.start(context, force = true)
            // only when you ask: lets go of cached pictures (they come back from the prepared copies)
            PersonalRow.FREE_MEMORY -> {
                com.nendo.argosy.ui.cocoon.CocoonOptimizer.freePictureMemory(context)
                backupStatus = context.getString(R.string.cocoon_free_memory_done)
            }
            PersonalRow.BACKUP -> backupStatus = com.nendo.argosy.ui.cocoon.CocoonBackup.backup(context).fold(
                onSuccess = { context.getString(R.string.cocoon_backup_done, it.absolutePath) },
                onFailure = { context.getString(R.string.cocoon_backup_failed, it.message ?: "") }
            )
            PersonalRow.RESTORE -> backupStatus = com.nendo.argosy.ui.cocoon.CocoonBackup.restore(context).fold(
                onSuccess = { context.getString(R.string.cocoon_restore_done) },
                onFailure = { context.getString(R.string.cocoon_backup_failed, it.message ?: "") }
            )
        }
    }

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
            // rows that do something (back up, clear…) only run on A, never on ←/→
            private val actions = setOf(PersonalRow.OPTIMIZE, PersonalRow.FREE_MEMORY, PersonalRow.BACKUP, PersonalRow.RESTORE, PersonalRow.JINGLE_CLEAR, PersonalRow.JINGLE_REPOS)
            override fun onLeft(): InputResult {
                if (rows[focus] in actions) return InputResult.HANDLED
                change(rows[focus], -1); return InputResult.handled(SoundType.TOGGLE)
            }
            override fun onRight(): InputResult {
                if (rows[focus] in actions) return InputResult.HANDLED
                change(rows[focus], 1); return InputResult.handled(SoundType.TOGGLE)
            }
            override fun onConfirm(): InputResult { change(rows[focus], 1); return InputResult.handled(SoundType.TOGGLE) }
            override fun onBack(): InputResult { dismiss(); return InputResult.HANDLED }
            override fun onMenu(): InputResult = InputResult.handled(SoundType.SILENT)
            override fun onSecondaryAction(): InputResult = InputResult.HANDLED
            override fun onContextMenu(): InputResult = InputResult.HANDLED
        }
    }
    ModalInputEffect(active = !pickingJingle && !managingRepos, handler = handler)

    val themeLabel = stringResource(R.string.cocoon_personal_theme)
    fun valueOf(row: PersonalRow): String = when (row) {
        PersonalRow.CURSOR -> when (personal.cursorStyle) {
            null -> themeLabel
            "ANIMATED" -> context.getString(R.string.cocoon_cursor_animated)
            "OUTLINED" -> context.getString(R.string.cocoon_cursor_outlined)
            "ALTERNATIVE_ANIMATED" -> context.getString(R.string.cocoon_cursor_alt_animated)
            else -> context.getString(R.string.cocoon_cursor_alt)
        }
        PersonalRow.THICKNESS -> personal.highlightThickness?.let { "${(it * 100).toInt()}%" } ?: themeLabel
        PersonalRow.DOCK_SHAPE -> when (personal.dockIconShape) {
            null -> themeLabel
            "CIRCLE" -> context.getString(R.string.cocoon_shape_circle)
            "SQUIRCLE" -> context.getString(R.string.cocoon_shape_squircle)
            "ROUNDED_SQUARE" -> context.getString(R.string.cocoon_shape_rounded)
            else -> context.getString(R.string.cocoon_shape_square)
        }
        PersonalRow.GRID_ROWS -> com.nendo.argosy.ui.cocoon.CocoonHomeStore.rows.toString()
        PersonalRow.SURFACE -> when (personal.glass) {
            null -> themeLabel
            true -> context.getString(R.string.cocoon_surface_glass)
            false -> context.getString(R.string.cocoon_surface_solid)
        }
        PersonalRow.NOW_PLAYING -> context.getString(com.nendo.argosy.ui.cocoon.CocoonHomeStore.nowPlaying.labelRes)
        PersonalRow.ICON_STYLE -> context.getString(
            when (com.nendo.argosy.ui.cocoon.CocoonHomeStore.iconStyle) {
                "ACCENT" -> R.string.cocoon_icon_accent
                "FLAT" -> R.string.cocoon_icon_flat
                else -> R.string.cocoon_icon_colored
            }
        )
        PersonalRow.MOTION -> context.getString(
            when (com.nendo.argosy.ui.cocoon.CocoonHomeStore.motion) {
                "SNAPPY" -> R.string.cocoon_motion_snappy
                "REDUCED" -> R.string.cocoon_motion_reduced
                else -> R.string.cocoon_motion_comfortable
            }
        )
        PersonalRow.HERO_STYLE -> context.getString(
            when (com.nendo.argosy.ui.cocoon.CocoonHomeStore.heroStyle) {
                "VIGNETTE" -> R.string.cocoon_hero_vignette
                "NONE" -> R.string.cocoon_hero_none
                else -> R.string.cocoon_hero_full
            }
        )
        PersonalRow.HERO_SIZE -> context.getString(
            when (com.nendo.argosy.ui.cocoon.CocoonHomeStore.heroShare) {
                0.3f -> R.string.cocoon_size_small
                0.6f -> R.string.cocoon_size_large
                else -> R.string.cocoon_size_medium
            }
        )
        PersonalRow.DOCK_POSITION -> context.getString(
            if (com.nendo.argosy.ui.cocoon.CocoonHomeStore.dockBottom) R.string.cocoon_dock_bottom else R.string.cocoon_dock_top
        )
        PersonalRow.DOCK_BACKGROUND -> context.getString(
            if (com.nendo.argosy.ui.cocoon.CocoonHomeStore.dockBackground) R.string.cocoon_on else R.string.cocoon_off
        )
        PersonalRow.DOCK_AUTOHIDE -> context.getString(
            if (com.nendo.argosy.ui.cocoon.CocoonHomeStore.dockAutoHide) R.string.cocoon_on else R.string.cocoon_off
        )
        PersonalRow.JINGLES -> context.getString(
            if (com.nendo.argosy.ui.cocoon.CocoonJingles.enabled(context)) R.string.cocoon_on else R.string.cocoon_off
        )
        PersonalRow.JINGLE_VOLUME -> "${com.nendo.argosy.ui.cocoon.CocoonJingles.volume(context)}%"
        PersonalRow.JINGLE_DEFAULT -> com.nendo.argosy.ui.cocoon.CocoonJingles.globalJingle(context)
            ?.let { java.io.File(it).nameWithoutExtension } ?: context.getString(R.string.cocoon_jingle_none)
        PersonalRow.JINGLE_CLEAR -> ""
        PersonalRow.JINGLE_REPOS -> com.nendo.argosy.ui.cocoon.CocoonJingleRepos.repos(context).size.toString()
        PersonalRow.OPTIMIZE, PersonalRow.FREE_MEMORY, PersonalRow.BACKUP, PersonalRow.RESTORE -> ""
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black).clickableNoFocus { }) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Tune, contentDescription = null, tint = CocoonSurface.TextPrimary, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(if (jinglesOnly) R.string.cocoon_jingles else R.string.cocoon_personal_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = CocoonSurface.TextPrimary
                )
            }
            backupStatus?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = CocoonSurface.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(18.dp))
            // The page is longer than the screen: the list scrolls to keep the focused row in view.
            val list = androidx.compose.foundation.lazy.rememberLazyListState()
            androidx.compose.runtime.LaunchedEffect(focus) {
                // Keep the highlighted row on screen with one short, smooth scroll of just the
                // distance needed, plus a margin so the next row peeks in. Re-centering the list
                // on every step made it jump, and stutter on the way back up.
                val info = list.layoutInfo
                val item = info.visibleItemsInfo.firstOrNull { it.index == focus }
                val margin = (info.viewportEndOffset - info.viewportStartOffset) / 6
                val spec = androidx.compose.animation.core.tween<Float>(
                    220, easing = androidx.compose.animation.core.FastOutSlowInEasing
                )
                if (item == null) {
                    runCatching { list.animateScrollToItem(focus) }
                    return@LaunchedEffect
                }
                val top = item.offset - info.viewportStartOffset
                val bottom = item.offset + item.size - info.viewportEndOffset
                val delta = when {
                    top < margin && list.canScrollBackward -> (top - margin).toFloat()
                    bottom > -margin && list.canScrollForward -> (bottom + margin).toFloat()
                    else -> 0f
                }
                if (delta != 0f) runCatching { list.animateScrollBy(delta, spec) }
            }
            // Settings that change how things look get a live preview beside the list (or above it
            // on a narrow screen) that follows the highlighted row.
            val previewKind = when (rows.getOrNull(focus)) {
                PersonalRow.HERO_STYLE, PersonalRow.HERO_SIZE, PersonalRow.NOW_PLAYING -> CocoonPreviewKind.TOP
                PersonalRow.SURFACE -> CocoonPreviewKind.SURFACE
                else -> CocoonPreviewKind.HOME
            }
            val personalList: @Composable (Modifier) -> Unit = { listModifier ->
                CocoonCursorHost(modifier = listModifier, covers = true) {
                    androidx.compose.foundation.lazy.LazyColumn(
                        state = list,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(rows.size) { index ->
                            val row = rows[index]
                            // Every row is the same height and splits the same way, whatever its
                            // value says: a long value ("Alternative (Animated)") used to take the
                            // width, squeeze the label into a tall column and resize the row (and
                            // the cursor with it) at each press.
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(PERSONAL_ROW_HEIGHT)
                                    .cocoonBrackets(focused = index == focus, outset = 3.dp)
                                    .background(CocoonSurface.Panel, RoundedCornerShape(16.dp))
                                    .clickableNoFocus { focus = index; change(row, 1) }
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    stringResource(row.labelRes),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = CocoonSurface.TextPrimary,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                        contentDescription = null,
                                        tint = CocoonSurface.TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        valueOf(row),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = CocoonSurface.TextPrimary,
                                        maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                                    )
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = CocoonSurface.TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val preview: @Composable (Modifier) -> Unit = { m ->
                    androidx.compose.animation.Crossfade(previewKind, label = "personalPreview", modifier = m) { kind ->
                        CocoonSettingsPreview(kind, Modifier.fillMaxWidth())
                    }
                }
                when {
                    jinglesOnly -> personalList(Modifier.fillMaxSize())
                    // side by side whenever there's room, so the list keeps the full height
                    maxWidth >= 420.dp -> Row(Modifier.fillMaxSize()) {
                        personalList(Modifier.weight(0.6f).fillMaxHeight())
                        Spacer(Modifier.width(16.dp))
                        preview(Modifier.weight(0.4f).padding(top = 4.dp))
                    }
                    else -> Column(Modifier.fillMaxSize()) {
                        preview(Modifier.fillMaxWidth(0.4f).align(Alignment.CenterHorizontally))
                        Spacer(Modifier.height(12.dp))
                        personalList(Modifier.fillMaxWidth().weight(1f))
                    }
                }
            }
        }
    }
    if (managingRepos) {
        CocoonJingleRepoManager(onDismiss = { managingRepos = false })
    }
    if (pickingJingle) {
        com.nendo.argosy.ui.filebrowser.FileBrowserScreen(
            mode = com.nendo.argosy.ui.filebrowser.FileBrowserMode.FILE_SELECTION,
            title = stringResource(R.string.cocoon_jingle_default),
            fileFilter = com.nendo.argosy.ui.filebrowser.FileFilter.AUDIO,
            onPathSelected = { path ->
                pickingJingle = false
                com.nendo.argosy.ui.cocoon.CocoonJingles.setGlobalJingle(context, path)
                com.nendo.argosy.ui.cocoon.CocoonJingles.play(context, path)
            },
            onDismiss = { pickingJingle = false }
        )
    }
}


/** Personalization ▸ Jingle Collections: the GitHub repos jingles come from. */
@Composable
private fun CocoonJingleRepoManager(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    com.nendo.argosy.ui.cocoon.CocoonJingles.version.intValue
    val repos = com.nendo.argosy.ui.cocoon.CocoonJingleRepos.repos(context)
    var focus by remember { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    val reposNow by rememberUpdatedState(repos)
    val dismiss by rememberUpdatedState(onDismiss)
    val handler = remember {
        object : InputHandler {
            fun step(delta: Int): InputResult {
                val next = focus + delta
                if (next !in 0..reposNow.size) return InputResult.handled(SoundType.BOUNDARY)
                focus = next
                return InputResult.HANDLED
            }
            override fun onUp() = step(-1)
            override fun onDown() = step(1)
            override fun onLeft(): InputResult = InputResult.HANDLED
            override fun onRight(): InputResult = InputResult.HANDLED
            override fun onConfirm(): InputResult {
                if (focus == 0) adding = true
                return InputResult.handled(SoundType.SELECT)
            }
            override fun onContextMenu(): InputResult {
                reposNow.getOrNull(focus - 1)?.let { com.nendo.argosy.ui.cocoon.CocoonJingleRepos.removeRepo(context, it) }
                if (focus > reposNow.size - 1) focus = (reposNow.size - 1).coerceAtLeast(0)
                return InputResult.HANDLED
            }
            override fun onBack(): InputResult { dismiss(); return InputResult.HANDLED }
            override fun onMenu(): InputResult = InputResult.handled(SoundType.SILENT)
            override fun onSecondaryAction(): InputResult = InputResult.HANDLED
        }
    }
    ModalInputEffect(active = !adding, handler = handler)
    Box(modifier = Modifier.fillMaxSize().background(Color.Black).clickableNoFocus { }) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp)) {
            Text(stringResource(R.string.cocoon_jingle_repos), style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold, color = CocoonSurface.TextPrimary)
            Spacer(Modifier.height(14.dp))
            CocoonCursorHost(modifier = Modifier.fillMaxWidth().weight(1f), covers = true) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf(stringResource(R.string.cocoon_jingle_repo_add)) + repos).forEachIndexed { index, label ->
                        Text(
                            label,
                            style = MaterialTheme.typography.titleMedium,
                            color = CocoonSurface.TextPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .cocoonBrackets(focused = index == focus, outset = 2.dp)
                                .background(CocoonSurface.Panel, RoundedCornerShape(14.dp))
                                .clickableNoFocus { focus = index; if (index == 0) adding = true }
                                .padding(horizontal = 18.dp, vertical = 14.dp)
                        )
                    }
                }
            }
            Text(stringResource(R.string.cocoon_jingle_repo_hint), color = CocoonSurface.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (adding) {
        com.nendo.argosy.ui.screens.collections.dialogs.CollectionNameDialog(
            title = stringResource(R.string.cocoon_jingle_repo_add),
            label = "owner/repository",
            confirmLabel = stringResource(R.string.cocoon_themes_apply),
            initialName = "",
            gamepadInput = true,
            onDismiss = { adding = false },
            onSubmit = { name ->
                adding = false
                com.nendo.argosy.ui.cocoon.CocoonJingleRepos.addRepo(context, name)
            }
        )
    }
}
