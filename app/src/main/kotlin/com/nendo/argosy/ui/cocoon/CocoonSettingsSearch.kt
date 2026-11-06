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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.CocoonCursorHost
import com.nendo.argosy.ui.primitives.cocoonBrackets
import com.nendo.argosy.ui.screens.settings.SettingsSection
import com.nendo.argosy.ui.theme.CocoonSurface
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * Settings search is modal: its row in Settings and the A-press on that row both open it through
 * this flag, so touch and gamepad reach the same overlay.
 */
object CocoonSettingsSearchState {
    val open = kotlinx.coroutines.flow.MutableStateFlow(false)
}

/** One place Settings search can take you. */
private data class SearchEntry(val label: String, val keywords: String, val open: () -> Unit)

private fun humanize(section: SettingsSection): String =
    section.name.lowercase().split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
        .replace("Romm", "RomM").replace("Bios", "BIOS").replace("Builtin", "Built-in")

/** Extra words people search for, per page. */
private val KEYWORDS = mapOf(
    SettingsSection.ROMM to "server sync library connection",
    SettingsSection.SAVES to "save sync cloud backup",
    SettingsSection.RETRO_ACHIEVEMENTS to "achievements ra cheevos login",
    SettingsSection.THEME to "colour color accent appearance dark light",
    SettingsSection.AUDIO to "sound volume ui sounds haptics",
    SettingsSection.THEME_SOUNDS to "sound effects sfx",
    SettingsSection.THEME_MUSIC to "background music bgm playlist",
    SettingsSection.THEME_FONTS to "font text typeface",
    SettingsSection.BOX_ART to "cover art border glow",
    SettingsSection.DISPLAYS to "screen dual swap external",
    SettingsSection.SCREENS to "screen dual swap layout",
    SettingsSection.PLATFORMS to "emulators consoles systems",
    SettingsSection.BUILTIN_EMULATOR to "retroarch libretro core",
    SettingsSection.BUILTIN_VIDEO to "shader filter scaling",
    SettingsSection.BUILTIN_CONTROLS to "buttons mapping controller",
    SettingsSection.STORAGE to "space downloads folder",
    SettingsSection.PLAY_TIME to "playtime stats log",
    SettingsSection.SOCIAL to "friends discord",
    SettingsSection.PERMISSIONS to "access storage usage",
)

private val HIDDEN = setOf(SettingsSection.MAIN, SettingsSection.PLATFORM_DETAIL, SettingsSection.FRAME_PICKER)

/**
 * Cocoon's Settings search (Settings ▸ Search Settings): type a word, pick a page. ↑/↓ move through
 * the results, A opens one, B closes. It lives in Settings, not on Home: Home's Search searches
 * games.
 */
@Composable
fun CocoonSettingsSearch(onOpenSection: (SettingsSection) -> Unit, onDismiss: () -> Unit) {
    val themesLabel = stringResource(R.string.cocoon_themes_title)
    val personalLabel = stringResource(R.string.cocoon_personal_title)
    val open by rememberUpdatedState(onOpenSection)
    val dismiss by rememberUpdatedState(onDismiss)
    val entries = remember(themesLabel, personalLabel) {
        listOf(
            SearchEntry(themesLabel, "theme cocoon silk import zip wallpaper icons pack") {
                com.nendo.argosy.ui.cocoon.theme.CocoonThemeEngine.pickerOpen.value = true
                open(SettingsSection.THEME)
            },
            SearchEntry(personalLabel, "cursor highlight dock grid rows jingles glass now playing") {
                com.nendo.argosy.ui.cocoon.theme.CocoonThemeEngine.personalOpen.value = true
                open(SettingsSection.THEME)
            },
        ) + SettingsSection.entries.filter { it !in HIDDEN }.map { section ->
            SearchEntry(humanize(section), KEYWORDS[section].orEmpty()) { open(section) }
        }
    }
    var query by remember { mutableStateOf("") }
    val results = remember(query, entries) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) entries else entries.filter { e ->
            q.split(' ').filter { it.isNotBlank() }.all { word -> e.label.lowercase().contains(word) || e.keywords.contains(word) }
        }
    }
    var focus by remember { mutableIntStateOf(0) }
    if (focus > results.lastIndex) focus = results.lastIndex.coerceAtLeast(0)
    val resultsNow by rememberUpdatedState(results)
    val handler = remember {
        object : InputHandler {
            fun step(delta: Int): InputResult {
                val next = focus + delta
                if (next !in resultsNow.indices) return InputResult.handled(SoundType.BOUNDARY)
                focus = next
                return InputResult.HANDLED
            }
            override fun onUp() = step(-1)
            override fun onDown() = step(1)
            override fun onLeft(): InputResult = InputResult.HANDLED
            override fun onRight(): InputResult = InputResult.HANDLED
            override fun onConfirm(): InputResult {
                resultsNow.getOrNull(focus)?.let { dismiss(); it.open() }
                return InputResult.handled(SoundType.SELECT)
            }
            override fun onBack(): InputResult { dismiss(); return InputResult.HANDLED }
            override fun onMenu(): InputResult = InputResult.handled(SoundType.SILENT)
            override fun onSecondaryAction(): InputResult = InputResult.HANDLED
            override fun onContextMenu(): InputResult = InputResult.HANDLED
        }
    }
    ModalInputEffect(active = true, handler = handler)
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
    val list = rememberLazyListState()
    LaunchedEffect(focus) { if (results.isNotEmpty()) runCatching { list.animateScrollToItem((focus - 2).coerceAtLeast(0)) } }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)).clickableNoFocus { }) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CocoonMenuPanel, RoundedCornerShape(50))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = CocoonSurface.TextSecondary)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(stringResource(R.string.cocoon_search_hint), color = CocoonSurface.TextSecondary)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it; focus = 0 },
                        singleLine = true,
                        textStyle = TextStyle(color = CocoonSurface.TextPrimary, fontSize = 18.sp),
                        cursorBrush = SolidColor(CocoonSurface.TextPrimary),
                        modifier = Modifier.fillMaxWidth().focusRequester(field)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            if (results.isEmpty()) {
                Text(stringResource(R.string.cocoon_search_none), color = CocoonSurface.TextSecondary, modifier = Modifier.padding(8.dp))
            }
            CocoonCursorHost(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
                    items(results.size) { index ->
                        val entry = results[index]
                        Text(
                            entry.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = CocoonSurface.TextPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .cocoonBrackets(focused = index == focus, outset = 2.dp)
                                .background(CocoonMenuPanel, RoundedCornerShape(14.dp))
                                .clickableNoFocus { dismiss(); entry.open() }
                                .padding(horizontal = 18.dp, vertical = 14.dp)
                        )
                    }
                }
            }
        }
    }
}
