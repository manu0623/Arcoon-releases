package com.nendo.argosy.ui.cocoon.theme

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.CocoonCursorHost
import com.nendo.argosy.ui.primitives.InputGlyph
import com.nendo.argosy.ui.primitives.cocoonBrackets
import com.nendo.argosy.ui.theme.CocoonSurface
import com.nendo.argosy.ui.util.clickableNoFocus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val COLUMNS = 3

/** One card in the picker: the built-in look, an installed theme, or "Import ZIP". */
private sealed class PickerCard {
    data object Default : PickerCard()
    data class Theme(val theme: CocoonTheme) : PickerCard()
    data class Pack(val pack: CocoonAssetPack) : PickerCard()
    data object Import : PickerCard()
}

/**
 * Settings ▸ Theme ▸ Themes. A applies the focused theme (or imports a ZIP), X deletes an
 * imported theme (press twice), B goes back.
 */
@Composable
fun CocoonThemePicker(onDismiss: () -> Unit) {
    val installed by CocoonThemeEngine.installed
    val packs by CocoonThemeEngine.packs
    val active by CocoonThemeEngine.active
    val cards = remember(installed, packs) {
        listOf(PickerCard.Default) + installed.map { PickerCard.Theme(it) } +
            packs.map { PickerCard.Pack(it) } + PickerCard.Import
    }
    var focus by remember { mutableIntStateOf(0) }
    var browsing by remember { mutableStateOf(false) }
    var deleteArmed by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentDismiss by rememberUpdatedState(onDismiss)

    LaunchedEffect(Unit) {
        CocoonThemeEngine.refresh()
        val index = cards.indexOfFirst { it is PickerCard.Theme && it.theme.id == CocoonThemeEngine.active.value?.id }
        if (index >= 0) focus = index
    }
    if (focus > cards.lastIndex) focus = cards.lastIndex

    fun activate(index: Int) {
        focus = index
        deleteArmed = false
        when (val card = cards.getOrNull(index)) {
            PickerCard.Default -> CocoonThemeEngine.apply(null)
            is PickerCard.Theme -> CocoonThemeEngine.apply(card.theme)
            // packs switch on and off; they sit on top of whichever theme is applied
            is PickerCard.Pack -> CocoonThemeEngine.setPackEnabled(card.pack.id, !card.pack.enabled)
            PickerCard.Import -> browsing = true
            null -> {}
        }
    }

    val handler = remember(cards) {
        object : InputHandler {
            fun move(delta: Int, sameRow: Boolean): InputResult {
                val next = focus + delta
                if (next !in cards.indices || (sameRow && next / COLUMNS != focus / COLUMNS)) {
                    return InputResult.handled(SoundType.BOUNDARY)
                }
                focus = next
                deleteArmed = false
                return InputResult.HANDLED
            }
            override fun onUp() = move(-COLUMNS, false)
            override fun onDown() = move(COLUMNS, false)
            override fun onLeft() = move(-1, true)
            override fun onRight() = move(1, true)
            override fun onConfirm(): InputResult {
                activate(focus)
                return InputResult.handled(SoundType.SELECT)
            }
            override fun onBack(): InputResult {
                currentDismiss()
                return InputResult.HANDLED
            }
            override fun onSecondaryAction(): InputResult = InputResult.HANDLED
            override fun onContextMenu(): InputResult {
                val card = cards.getOrNull(focus)
                if (card !is PickerCard.Theme && card !is PickerCard.Pack) return InputResult.handled(SoundType.BOUNDARY)
                if (!deleteArmed) {
                    deleteArmed = true
                    return InputResult.HANDLED
                }
                deleteArmed = false
                when (card) {
                    is PickerCard.Theme -> CocoonThemeEngine.delete(card.theme)
                    is PickerCard.Pack -> CocoonThemeEngine.deletePack(card.pack)
                    else -> Unit
                }
                return InputResult.HANDLED
            }
            override fun onMenu(): InputResult = InputResult.handled(SoundType.SILENT)
            override fun onPrevSection(): InputResult = InputResult.handled(SoundType.SILENT)
            override fun onNextSection(): InputResult = InputResult.handled(SoundType.SILENT)
        }
    }
    ModalInputEffect(active = !browsing, handler = handler)

    val grid = rememberLazyGridState()
    LaunchedEffect(focus) {
        val visible = grid.layoutInfo.visibleItemsInfo
        if (visible.isEmpty()) return@LaunchedEffect
        if (focus < visible.first().index || focus > visible.last().index) grid.animateScrollToItem(focus)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickableNoFocus { }
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Palette, contentDescription = null, tint = CocoonSurface.TextPrimary, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.cocoon_themes_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = CocoonSurface.TextPrimary
                )
                Spacer(Modifier.weight(1f))
                status?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = CocoonSurface.TextSecondary, maxLines = 1)
                }
            }
            Spacer(Modifier.height(16.dp))
            // The highlighted theme is previewed beside the grid before it is applied.
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                CocoonCursorHost(modifier = Modifier.weight(0.62f).fillMaxHeight()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(COLUMNS),
                        state = grid,
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(22.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp)
                    ) {
                        items(cards.size) { index ->
                            val card = cards[index]
                            ThemeCard(
                                card = card,
                                focused = index == focus,
                                inUse = when (card) {
                                    PickerCard.Default -> active == null
                                    is PickerCard.Theme -> active?.id == card.theme.id
                                    is PickerCard.Pack -> card.pack.enabled
                                    PickerCard.Import -> false
                                },
                                deleteArmed = deleteArmed && index == focus,
                                onClick = { activate(index) }
                            )
                        }
                    }
                }
                Spacer(Modifier.width(22.dp))
                androidx.compose.animation.Crossfade(
                    targetState = cards.getOrNull(focus),
                    label = "themePreview",
                    modifier = Modifier.weight(0.38f).fillMaxHeight()
                ) { card -> ThemeHighlightPreview(card) }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Hint(InputButton.A, stringResource(R.string.cocoon_themes_apply))
                if (cards.getOrNull(focus).let { it is PickerCard.Theme || it is PickerCard.Pack }) {
                    Hint(InputButton.X, stringResource(if (deleteArmed) R.string.cocoon_themes_delete_confirm else R.string.cocoon_themes_delete))
                }
                Hint(InputButton.B, stringResource(R.string.cocoon_back))
            }
        }
    }

    if (browsing) {
        com.nendo.argosy.ui.filebrowser.FileBrowserScreen(
            mode = com.nendo.argosy.ui.filebrowser.FileBrowserMode.FILE_SELECTION,
            title = stringResource(R.string.cocoon_themes_import),
            fileFilter = com.nendo.argosy.ui.filebrowser.FileFilter(extensions = setOf("zip")),
            onPathSelected = { path ->
                browsing = false
                status = context.getString(R.string.cocoon_themes_importing)
                scope.launch {
                    val result = withContext(Dispatchers.IO) { CocoonThemeEngine.importZip(path) }
                    result.onSuccess { theme ->
                        CocoonThemeEngine.apply(theme)
                        status = context.getString(R.string.cocoon_themes_imported, theme.name)
                        val index = CocoonThemeEngine.installed.value.indexOfFirst { it.id == theme.id }
                        if (index >= 0) focus = index + 1
                    }.onFailure {
                        val message = it.message.orEmpty()
                        status = if (message.startsWith("PACK:")) {
                            context.getString(R.string.cocoon_themes_imported, message.removePrefix("PACK:"))
                        } else {
                            context.getString(R.string.cocoon_themes_import_failed, message)
                        }
                    }
                }
            },
            onDismiss = { browsing = false }
        )
    }
}

@Composable
private fun Hint(button: InputButton, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        InputGlyph(button = button, size = 20.dp, tint = CocoonSurface.TextSecondary)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = CocoonSurface.TextSecondary)
    }
}

@Composable
private fun ThemeCard(
    card: PickerCard,
    focused: Boolean,
    inUse: Boolean,
    deleteArmed: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(modifier = Modifier.fillMaxWidth().clickableNoFocus(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .cocoonBrackets(focused = focused, outset = 4.dp)
                .clip(shape)
                .background(Color(0xFF111111))
                .border(1.dp, if (deleteArmed) Color(0xFFFF6B6B) else Color.White.copy(alpha = 0.08f), shape),
            contentAlignment = Alignment.Center
        ) {
            when (card) {
                PickerCard.Default -> com.nendo.argosy.ui.theme.CocoonWallpaper(animated = false)
                is PickerCard.Theme -> {
                    val theme = card.theme
                    val image = theme.preview ?: theme.wallpaperExternal ?: theme.wallpaperMain
                    if (image != null && image.extension.lowercase() !in setOf("mp4", "webm", "mkv", "mov", "3gp", "m4v")) {
                        coil.compose.AsyncImage(
                            model = image,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val c = theme.colors
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.linearGradient(
                                    listOf(
                                        c.backgroundStart ?: Color(0xFF101010),
                                        c.accent ?: c.backgroundEnd ?: Color(0xFF202020)
                                    )
                                )
                            )
                        )
                    }
                }
                is PickerCard.Pack -> {
                    // a strip of the pack's first icons
                    val icons = remember(card.pack.id) {
                        java.io.File(card.pack.dir, "smart_folders").walkTopDown().maxDepth(4)
                            .filter { it.isFile && it.extension.lowercase() == "png" && it.nameWithoutExtension == "icon" }
                            .take(4).toList()
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        icons.forEach { file ->
                            coil.compose.AsyncImage(model = file, contentDescription = null, modifier = Modifier.size(48.dp))
                        }
                    }
                }
                PickerCard.Import -> Icon(
                    Icons.Outlined.FileOpen,
                    contentDescription = null,
                    tint = CocoonSurface.TextSecondary,
                    modifier = Modifier.size(44.dp)
                )
            }
            if (inUse) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(26.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        val (title, subtitle) = when (card) {
            PickerCard.Default -> stringResource(R.string.cocoon_themes_default) to stringResource(R.string.cocoon_themes_default_sub)
            is PickerCard.Theme -> card.theme.name to (card.theme.author?.let { stringResource(R.string.cocoon_themes_by, it) } ?: "")
            is PickerCard.Pack -> card.pack.name to stringResource(
                if (card.pack.enabled) R.string.cocoon_pack_on else R.string.cocoon_pack_off
            )
            PickerCard.Import -> stringResource(R.string.cocoon_themes_import) to stringResource(R.string.cocoon_themes_import_sub)
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (focused) CocoonSurface.TextPrimary else CocoonSurface.TextPrimary.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle.isNotBlank()) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = CocoonSurface.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}


/** A miniature Home in the highlighted theme's wallpaper and colours, before it is applied. */
@Composable
private fun ThemeHighlightPreview(card: PickerCard?) {
    if (card == null || card == PickerCard.Import) return
    val theme = (card as? PickerCard.Theme)?.theme
    val c = theme?.colors
    val background = c?.backgroundStart ?: Color.Black
    val panel = c?.cardStart ?: Color(0xFF141414)
    val chip = c?.cardEnd ?: c?.cardStart ?: Color(0xFF1C1C1C)
    val text = c?.textPrimary ?: Color(0xFFF5F5F5)
    val textDim = c?.textSecondary ?: Color(0xFF8A8A8A)
    val accent = c?.accent ?: Color(0xFF8E7DFF)
    val videoTypes = setOf("mp4", "webm", "mkv", "mov", "3gp", "m4v")
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1240f / 1080f)
                .clip(RoundedCornerShape(16.dp))
                .background(background)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
        ) {
            val wall = theme?.wallpaperMain ?: theme?.wallpaperExternal
            when {
                card == PickerCard.Default -> com.nendo.argosy.ui.theme.CocoonWallpaper(animated = false)
                wall != null && wall.extension.lowercase() !in videoTypes -> coil.compose.AsyncImage(
                    model = wall, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                )
                else -> Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(background, c?.backgroundEnd ?: background))))
            }
            androidx.compose.runtime.CompositionLocalProvider(com.nendo.argosy.ui.primitives.LocalCocoonCursor provides null) {
                Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                    // dock
                    Row(
                        modifier = Modifier.fillMaxWidth().background(chip.copy(alpha = 0.85f), RoundedCornerShape(50)).padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        repeat(5) { i ->
                            Box(Modifier.size(18.dp).clip(androidx.compose.foundation.shape.CircleShape).background(com.nendo.argosy.ui.cocoon.CocoonFolderPalette.top(i + 2)))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        repeat(3) { col ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                repeat(2) { row ->
                                    val index = col * 2 + row
                                    com.nendo.argosy.ui.cocoon.CocoonFolderIcon(
                                        colorIndex = index,
                                        glyph = com.nendo.argosy.ui.cocoon.CocoonGlyph.PLATFORM,
                                        platformSlug = listOf("snes", "gba", "psx", "n64", "nds", "genesis")[index],
                                        size = 38.dp
                                    )
                                }
                            }
                        }
                    }
                    // a menu card in the theme's panel colour, with its accent
                    Column(
                        modifier = Modifier.fillMaxWidth().background(panel.copy(alpha = 0.92f), RoundedCornerShape(10.dp)).padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(Modifier.fillMaxWidth(0.45f).height(7.dp).background(text, RoundedCornerShape(50)))
                        Box(Modifier.fillMaxWidth(0.8f).height(6.dp).background(textDim, RoundedCornerShape(50)))
                        Box(Modifier.fillMaxWidth(0.3f).height(6.dp).background(accent, RoundedCornerShape(50)))
                    }
                }
            }
        }
        // name, author and the theme's colours
        val name = when (card) {
            PickerCard.Default -> stringResource(R.string.cocoon_personal_theme)
            is PickerCard.Theme -> card.theme.name
            is PickerCard.Pack -> card.pack.name
            PickerCard.Import -> ""
        }
        Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = CocoonSurface.TextPrimary, maxLines = 1)
        theme?.author?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = CocoonSurface.TextSecondary, maxLines = 1) }
        if (c != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOfNotNull(c.backgroundStart, c.cardStart, c.cardEnd, c.accent, c.textPrimary).forEach { swatch ->
                    Box(Modifier.size(18.dp).clip(androidx.compose.foundation.shape.CircleShape).background(swatch)
                        .border(1.dp, Color.White.copy(alpha = 0.2f), androidx.compose.foundation.shape.CircleShape))
                }
            }
        }
        theme?.description?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = CocoonSurface.TextSecondary, maxLines = 4,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}
