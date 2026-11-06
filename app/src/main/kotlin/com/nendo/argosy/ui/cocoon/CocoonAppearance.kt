package com.nendo.argosy.ui.cocoon

import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.graphics.Brush
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

private enum class LookRow { COLOR, SYMBOL, IMAGE, RESET }

/** Cocoon's folder appearance: colour, symbol or your own icon. ←/→ change, A picks, B closes. */
@Composable
fun CocoonAppearanceSheet(
    tile: CocoonTile?,
    look: CocoonFolderLook,
    onChange: (CocoonFolderLook?) -> Unit,
    onDismiss: () -> Unit,
) {
    var focus by remember { mutableIntStateOf(0) }
    var current by remember { mutableStateOf(look) }
    var browsing by remember { mutableStateOf(false) }
    val change by rememberUpdatedState(onChange)
    val dismiss by rememberUpdatedState(onDismiss)
    val rows = LookRow.entries

    fun update(next: CocoonFolderLook) {
        current = next
        change(next)
    }

    fun adjust(row: LookRow, delta: Int) {
        when (row) {
            LookRow.COLOR -> {
                val size = CocoonFolderPalette.size
                val base = current.color ?: tile?.colorIndex ?: 0
                update(current.copy(color = (base + delta).mod(size)))
            }
            LookRow.SYMBOL -> {
                val options = listOf<String?>(null) + COCOON_FOLDER_SYMBOLS
                val index = options.indexOf(current.symbol).coerceAtLeast(0)
                update(current.copy(symbol = options[(index + delta).mod(options.size)], image = null))
            }
            else -> Unit
        }
    }

    fun confirm(row: LookRow) {
        when (row) {
            LookRow.COLOR, LookRow.SYMBOL -> adjust(row, 1)
            LookRow.IMAGE -> browsing = true
            LookRow.RESET -> {
                current = CocoonFolderLook()
                change(null)
            }
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
            override fun onLeft(): InputResult { adjust(rows[focus], -1); return InputResult.handled(SoundType.TOGGLE) }
            override fun onRight(): InputResult { adjust(rows[focus], 1); return InputResult.handled(SoundType.TOGGLE) }
            override fun onConfirm(): InputResult { confirm(rows[focus]); return InputResult.handled(SoundType.SELECT) }
            override fun onBack(): InputResult { dismiss(); return InputResult.HANDLED }
            override fun onMenu(): InputResult = InputResult.handled(SoundType.SILENT)
            override fun onSecondaryAction(): InputResult = InputResult.HANDLED
            override fun onContextMenu(): InputResult = InputResult.HANDLED
        }
    }
    ModalInputEffect(active = !browsing, handler = handler)

    // live preview with the chosen look
    val previewColor = current.color ?: tile?.colorIndex ?: 0
    val previewGlyph = if (current.image != null || current.symbol != null) CocoonGlyph.CUSTOM else tile?.glyph ?: CocoonGlyph.FOLDER
    val previewPayload = when {
        current.image != null -> "image:" + current.image
        current.symbol != null -> "symbol:" + current.symbol
        tile?.glyph == CocoonGlyph.CUSTOM -> null
        else -> tile?.platformSlug
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickableNoFocus(onClick = onDismiss)
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(0.45f), horizontalAlignment = Alignment.CenterHorizontally) {
                // A big preview of the restyled folder, then the same folder between its neighbours
                // at board size, so the change can be judged where it will actually sit.
                val pop = remember { androidx.compose.animation.core.Animatable(0.9f) }
                androidx.compose.runtime.LaunchedEffect(previewColor, previewGlyph, previewPayload) {
                    pop.snapTo(0.92f)
                    pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 500f))
                }
                CocoonFolderIcon(
                    colorIndex = previewColor, glyph = previewGlyph, platformSlug = previewPayload, size = 170.dp,
                    modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .background(CocoonSurface.Panel, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // The neighbours are the colours either side of the chosen one, not
                    // arbitrary offsets, so the strip reads as the board it will sit on.
                    CocoonFolderIcon(colorIndex = previewColor - 1, glyph = CocoonGlyph.FOLDER, platformSlug = null, size = 44.dp,
                        modifier = Modifier.graphicsLayer { alpha = 0.55f })
                    CocoonFolderIcon(colorIndex = previewColor, glyph = previewGlyph, platformSlug = previewPayload, size = 56.dp)
                    CocoonFolderIcon(colorIndex = previewColor + 1, glyph = CocoonGlyph.FOLDER, platformSlug = null, size = 44.dp,
                        modifier = Modifier.graphicsLayer { alpha = 0.55f })
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = tile?.title ?: tile?.titleRes?.let { stringResource(it) }.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    color = CocoonSurface.TextPrimary,
                    textAlign = TextAlign.Center
                )
            }
            CocoonCursorHost(modifier = Modifier.weight(0.6f)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rows.forEachIndexed { index, row ->
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
                                stringResource(
                                    when (row) {
                                        LookRow.COLOR -> R.string.cocoon_look_color
                                        LookRow.SYMBOL -> R.string.cocoon_look_symbol
                                        LookRow.IMAGE -> R.string.cocoon_look_image
                                        LookRow.RESET -> R.string.cocoon_look_reset
                                    }
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (row == LookRow.RESET) Color(0xFFFF6B6B) else CocoonSurface.TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            when (row) {
                                LookRow.COLOR -> {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = CocoonSurface.TextSecondary)
                                    // The swatch is the folder itself, drawn with the very tint
                                    // the tile uses, so the demo cannot disagree with the board.
                                    androidx.compose.foundation.Image(
                                        painter = androidx.compose.ui.res.painterResource(CocoonFolderPalette.drawableFor(previewColor)),
                                        contentDescription = null,
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                                            CocoonFolderPalette.top(previewColor),
                                            androidx.compose.ui.graphics.BlendMode.Modulate
                                        ),
                                        modifier = Modifier.size(26.dp)
                                    )
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = CocoonSurface.TextSecondary)
                                }
                                LookRow.SYMBOL -> {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = CocoonSurface.TextSecondary)
                                    if (current.symbol == null) {
                                        Text(stringResource(R.string.cocoon_look_default), color = CocoonSurface.TextPrimary)
                                    } else {
                                        Icon(cocoonFolderSymbol(current.symbol!!), null, tint = CocoonSurface.TextPrimary, modifier = Modifier.size(24.dp))
                                    }
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = CocoonSurface.TextSecondary)
                                }
                                LookRow.IMAGE -> Text(
                                    if (current.image != null) java.io.File(current.image!!).name else stringResource(R.string.cocoon_look_image_choose),
                                    color = CocoonSurface.TextSecondary,
                                    maxLines = 1
                                )
                                LookRow.RESET -> Unit
                            }
                        }
                    }
                }
            }
        }
    }

    if (browsing) {
        com.nendo.argosy.ui.filebrowser.FileBrowserScreen(
            mode = com.nendo.argosy.ui.filebrowser.FileBrowserMode.FILE_SELECTION,
            title = stringResource(R.string.cocoon_look_image),
            fileFilter = com.nendo.argosy.ui.filebrowser.FileFilter.IMAGE,
            onPathSelected = { path ->
                browsing = false
                update(current.copy(image = path, symbol = null))
            },
            onDismiss = { browsing = false }
        )
    }
}
