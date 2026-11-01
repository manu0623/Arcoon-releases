package com.nendo.argosy.ui.cocoon

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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

private enum class AppEditRow { NAME, ICON, GAME, SCREEN, RESET }

/** Cocoon's Edit App sheet: name, icon, Treat as Game and the screen the app opens on. */
@Composable
fun CocoonAppEditSheet(
    packageName: String,
    loadScreen: suspend () -> String?,
    onSetScreen: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var focus by remember { mutableIntStateOf(0) }
    var renaming by remember { mutableStateOf(false) }
    var browsing by remember { mutableStateOf(false) }
    var screenKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(packageName) { screenKey = loadScreen() }
    val screens = remember {
        com.nendo.argosy.DualScreenManagerHolder.instance?.focusableDisplays().orEmpty()
    }
    val version = CocoonAppStore.version.intValue
    val label = remember(packageName, version) {
        CocoonAppStore.name(packageName) ?: runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
    }
    val dismiss by rememberUpdatedState(onDismiss)
    val setScreen by rememberUpdatedState(onSetScreen)
    val rows = AppEditRow.entries.filter { it != AppEditRow.SCREEN || screens.size > 1 }

    fun cycleScreen(delta: Int) {
        val keys = listOf<String?>(null) + screens.map { it.key }
        val next = keys[(keys.indexOf(screenKey).coerceAtLeast(0) + delta).mod(keys.size)]
        screenKey = next
        setScreen(next)
    }

    fun confirm(row: AppEditRow) {
        when (row) {
            AppEditRow.NAME -> renaming = true
            AppEditRow.ICON -> browsing = true
            AppEditRow.GAME -> CocoonAppStore.setGame(packageName, !CocoonAppStore.isGame(packageName))
            AppEditRow.SCREEN -> cycleScreen(1)
            AppEditRow.RESET -> {
                CocoonAppStore.setName(packageName, null)
                CocoonAppStore.setIcon(packageName, null)
                CocoonAppStore.setGame(packageName, false)
                screenKey = null
                setScreen(null)
            }
        }
    }

    val handler = remember(rows) {
        object : InputHandler {
            fun step(delta: Int): InputResult {
                val next = focus + delta
                if (next !in rows.indices) return InputResult.handled(SoundType.BOUNDARY)
                focus = next
                return InputResult.HANDLED
            }
            override fun onUp() = step(-1)
            override fun onDown() = step(1)
            override fun onLeft(): InputResult {
                if (rows[focus] == AppEditRow.SCREEN) cycleScreen(-1)
                return InputResult.HANDLED
            }
            override fun onRight(): InputResult {
                if (rows[focus] == AppEditRow.SCREEN) cycleScreen(1)
                return InputResult.HANDLED
            }
            override fun onConfirm(): InputResult { confirm(rows[focus]); return InputResult.handled(SoundType.SELECT) }
            override fun onBack(): InputResult { dismiss(); return InputResult.HANDLED }
            override fun onMenu(): InputResult = InputResult.handled(SoundType.SILENT)
            override fun onSecondaryAction(): InputResult = InputResult.HANDLED
            // X on the icon row puts the app's own icon back
            override fun onContextMenu(): InputResult {
                if (rows[focus] == AppEditRow.ICON) CocoonAppStore.setIcon(packageName, null)
                return InputResult.HANDLED
            }
        }
    }
    ModalInputEffect(active = !renaming && !browsing, handler = handler)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickableNoFocus(onClick = onDismiss)
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(0.4f), horizontalAlignment = Alignment.CenterHorizontally) {
                CocoonAppIcon(packageName, Modifier.size(110.dp))
                Spacer(Modifier.height(10.dp))
                Text(label, style = MaterialTheme.typography.titleLarge, color = CocoonSurface.TextPrimary, textAlign = TextAlign.Center)
            }
            CocoonCursorHost(modifier = Modifier.weight(0.6f)) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEachIndexed { index, row ->
                        val (title, value) = when (row) {
                            AppEditRow.NAME -> stringResource(R.string.cocoon_app_name) to label
                            AppEditRow.ICON -> stringResource(R.string.cocoon_app_icon) to
                                (CocoonAppStore.icon(packageName)?.let { java.io.File(it).name } ?: stringResource(R.string.cocoon_app_icon_default))
                            AppEditRow.GAME -> stringResource(R.string.cocoon_app_treat_game) to
                                stringResource(if (CocoonAppStore.isGame(packageName)) R.string.cocoon_on else R.string.cocoon_off)
                            AppEditRow.SCREEN -> stringResource(R.string.cocoon_app_open_on) to
                                (screens.indexOfFirst { it.key == screenKey }.takeIf { it >= 0 }
                                    ?.let { stringResource(R.string.cocoon_app_screen_n, it + 1) }
                                    ?: stringResource(R.string.cocoon_app_default_screen))
                            AppEditRow.RESET -> stringResource(R.string.cocoon_app_reset) to ""
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .cocoonBrackets(focused = index == focus, outset = 3.dp)
                                .background(CocoonMenuPanel, RoundedCornerShape(16.dp))
                                .clickableNoFocus { focus = index; confirm(row) }
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (row == AppEditRow.RESET) Color(0xFFFF6B6B) else CocoonSurface.TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            if (value.isNotEmpty()) Text(value, color = CocoonSurface.TextSecondary, maxLines = 1)
                        }
                    }
                    if (rows.getOrNull(focus) == AppEditRow.GAME) {
                        Text(
                            stringResource(R.string.cocoon_app_treat_game_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = CocoonSurface.TextSecondary,
                            modifier = Modifier.padding(start = 12.dp, top = 4.dp)
                        )
                    }
                }
            }
        }
    }

    if (renaming) {
        com.nendo.argosy.ui.screens.collections.dialogs.CollectionNameDialog(
            title = stringResource(R.string.cocoon_app_name),
            label = stringResource(R.string.cocoon_app_name),
            confirmLabel = stringResource(R.string.cocoon_themes_apply),
            initialName = label,
            gamepadInput = true,
            onDismiss = { renaming = false },
            onSubmit = { name ->
                renaming = false
                CocoonAppStore.setName(packageName, name)
            }
        )
    }
    if (browsing) {
        com.nendo.argosy.ui.filebrowser.FileBrowserScreen(
            mode = com.nendo.argosy.ui.filebrowser.FileBrowserMode.FILE_SELECTION,
            title = stringResource(R.string.cocoon_app_icon),
            fileFilter = com.nendo.argosy.ui.filebrowser.FileFilter.IMAGE,
            onPathSelected = { path ->
                browsing = false
                CocoonAppStore.setIcon(packageName, path)
            },
            onDismiss = { browsing = false }
        )
    }
}
