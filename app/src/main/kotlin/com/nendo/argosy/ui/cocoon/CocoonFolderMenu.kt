package com.nendo.argosy.ui.cocoon

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.ModalPresenceEffect
import com.nendo.argosy.ui.primitives.InputGlyph
import com.nendo.argosy.ui.primitives.cocoonBrackets
import com.nendo.argosy.ui.theme.CocoonSurface
import com.nendo.argosy.ui.util.clickableNoFocus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Rows of the folder menu (Start inside a folder), each mapped to the Argosy function it opens. */
enum class CocoonFolderMenuRow(@StringRes val labelRes: Int, val icon: ImageVector, val hint: InputButton?, val opensSubmenu: Boolean) {
    APPS(R.string.cocoon_menu_apps, Icons.Filled.Apps, null, false),
    CREATE_FOLDER(R.string.cocoon_tile_menu_new_folder, Icons.Outlined.CreateNewFolder, null, false),
    SORT(R.string.cocoon_menu_sort, Icons.AutoMirrored.Filled.Sort, null, true),
    SEARCH(R.string.cocoon_menu_search, Icons.Filled.Search, null, false),
    SETTINGS(R.string.cocoon_menu_settings, Icons.Filled.Settings, InputButton.START, false),
}

/** Open/closed and cursor state of the folder menu; owned by the folder's ViewModel. */
class CocoonFolderMenu {
    private val _open = MutableStateFlow(false)
    val open: StateFlow<Boolean> = _open.asStateFlow()
    private val _index = MutableStateFlow(0)
    val index: StateFlow<Int> = _index.asStateFlow()

    fun show() { _index.value = 0; _open.value = true }
    fun hide() { _open.value = false }
    fun focus(i: Int) { _index.value = i.coerceIn(0, CocoonFolderMenuRow.entries.size - 1) }
    fun move(delta: Int): Boolean {
        val next = _index.value + delta
        if (next !in CocoonFolderMenuRow.entries.indices) return false
        _index.value = next
        return true
    }
}

/**
 * Wraps a folder's input handler so Start opens Cocoon's folder menu and, while it is open, the
 * d-pad and A/B drive the menu instead of the covers behind it.
 */
fun cocoonFolderMenuInput(
    base: InputHandler,
    menu: CocoonFolderMenu,
    onRow: (CocoonFolderMenuRow) -> Unit,
    grid: CocoonGridNavigation? = null,
    dockSize: () -> Int = { 0 },
    onDockSlot: (Int) -> Unit = {},
    suspendedFocused: () -> Boolean = { false },
    onResumeSuspended: () -> Unit = {},
    onCloseSuspended: () -> Unit = {},
    interceptLaunch: () -> Boolean = { false },
): InputHandler = object : InputHandler by base {
    private val isOpen get() = menu.open.value
    // While Cocoon's Quit / Switch dialog is up, only A (confirm) and B (back) mean anything.
    private val dialog get() = CocoonSessionDialog.isOpen
    private val swallow get() = InputResult.HANDLED
    private val dock = CocoonFolderDock.focus
    private val inDock get() = dock.value != null

    private fun dockStep(delta: Int): InputResult {
        val next = (dock.value ?: 0) + delta
        if (next !in 0 until dockSize()) return InputResult.handled(com.nendo.argosy.core.input.SoundType.BOUNDARY)
        dock.value = next
        return InputResult.HANDLED
    }

    private fun gridStep(dRow: Int, dCol: Int, fallback: () -> InputResult): InputResult {
        val g = grid
        if (g == null || !g.active()) return fallback()
        return if (g.move(dRow, dCol)) InputResult.handled(com.nendo.argosy.core.input.SoundType.SILENT)
        else InputResult.handled(com.nendo.argosy.core.input.SoundType.BOUNDARY)
    }

    // Start opens the shared Cocoon drawer (the same one as on the board); Select opens Sort.
    override fun onMenu(): InputResult {
        if (dialog) return swallow
        if (isOpen) {
            menu.hide()
            return InputResult.HANDLED
        }
        return base.onMenu()
    }
    override fun onSelect(): InputResult {
        if (dialog) return swallow
        if (isOpen) return InputResult.HANDLED
        onRow(CocoonFolderMenuRow.SORT)
        return InputResult.HANDLED
    }
    override fun onUp(): InputResult {
        if (dialog) return swallow
        if (isOpen) return step(-1)
        if (inDock) return InputResult.handled(com.nendo.argosy.core.input.SoundType.BOUNDARY)
        val g = grid
        if (g == null || !g.active()) return base.onUp()
        if (g.move(-1, 0)) return InputResult.handled(com.nendo.argosy.core.input.SoundType.SILENT)
        if (dockSize() > 0) {
            // Up from the top row lifts the cursor onto the dock, as on the board.
            dock.value = 0
            return InputResult.HANDLED
        }
        return InputResult.handled(com.nendo.argosy.core.input.SoundType.BOUNDARY)
    }
    override fun onDown(): InputResult {
        if (dialog) return swallow
        if (isOpen) return step(1)
        if (inDock) { dock.value = null; return InputResult.HANDLED }
        return gridStep(1, 0) { base.onDown() }
    }
    override fun onLeft(): InputResult = when {
        dialog -> swallow
        isOpen -> InputResult.HANDLED
        inDock -> dockStep(-1)
        else -> gridStep(0, -1) { base.onLeft() }
    }
    override fun onRight(): InputResult = when {
        dialog -> swallow
        isOpen -> confirm()
        inDock -> dockStep(1)
        else -> gridStep(0, 1) { base.onRight() }
    }
    override fun onConfirm(): InputResult {
        if (dialog) {
            CocoonSessionDialog.confirm()
            return InputResult.handled(com.nendo.argosy.core.input.SoundType.SELECT)
        }
        if (isOpen) return confirm()
        dock.value?.let { slot ->
            dock.value = null
            onDockSlot(slot)
            return InputResult.HANDLED
        }
        // Cocoon: A on the suspended game resumes it; A on another game while one is suspended
        // asks to switch games first.
        if (suspendedFocused()) {
            onResumeSuspended()
            return InputResult.handled(com.nendo.argosy.core.input.SoundType.SELECT)
        }
        if (interceptLaunch()) return InputResult.handled(com.nendo.argosy.core.input.SoundType.SELECT)
        return base.onConfirm()
    }
    override fun onBack(): InputResult {
        if (dialog) {
            CocoonSessionDialog.dismiss()
            return InputResult.handled(com.nendo.argosy.core.input.SoundType.BACK)
        }
        if (inDock) { dock.value = null; return InputResult.HANDLED }
        if (!isOpen) return base.onBack()
        menu.hide()
        return InputResult.HANDLED
    }
    // Y opens the game's options menu, the suspended game included.
    override fun onSecondaryAction(): InputResult {
        if (dialog) return swallow
        if (isOpen) return InputResult.HANDLED
        return base.onSecondaryAction()
    }
    // X shows or hides the game's info panel on the top screen; R3 changes how many covers fit a row.
    override fun onContextMenu(): InputResult {
        if (dialog) return swallow
        if (isOpen) return InputResult.HANDLED
        // On the suspended game, X asks to quit it (Cocoon's [X] Close); elsewhere it toggles info.
        if (!inDock && suspendedFocused()) {
            onCloseSuspended()
            return InputResult.handled(com.nendo.argosy.core.input.SoundType.SELECT)
        }
        CocoonHeroInfo.toggle()
        return InputResult.HANDLED
    }
    override fun onRightStickClick(): InputResult {
        if (!isOpen && !dialog) CocoonFolderGrid.cycle()
        return InputResult.HANDLED
    }
    override fun onPrevSection(): InputResult = if (dialog) swallow else base.onPrevSection()
    override fun onNextSection(): InputResult = if (dialog) swallow else base.onNextSection()
    override fun onPrevTrigger(): InputResult = if (dialog) swallow else base.onPrevTrigger()
    override fun onNextTrigger(): InputResult = if (dialog) swallow else base.onNextTrigger()
    override fun onLeftStickClick(): InputResult = if (dialog) swallow else base.onLeftStickClick()
    override fun onRightStickUp(): InputResult = if (dialog) swallow else base.onRightStickUp()
    override fun onRightStickDown(): InputResult = if (dialog) swallow else base.onRightStickDown()
    override fun onRightStickLeft(): InputResult = if (dialog) swallow else base.onRightStickLeft()
    override fun onRightStickRight(): InputResult = if (dialog) swallow else base.onRightStickRight()
    override fun onLongConfirm(): InputResult = if (dialog) swallow else base.onLongConfirm()
    override fun onLongSelect(): InputResult = if (dialog) swallow else base.onLongSelect()

    private fun step(delta: Int): InputResult =
        if (menu.move(delta)) InputResult.HANDLED else InputResult.handled(com.nendo.argosy.core.input.SoundType.BOUNDARY)

    private fun confirm(): InputResult {
        val row = CocoonFolderMenuRow.entries[menu.index.value]
        menu.hide()
        onRow(row)
        return InputResult.HANDLED
    }
}

/** Cocoon's left-anchored folder menu over a darkened folder. */
@Composable
fun CocoonFolderMenuSheet(
    focusedIndex: Int,
    onRow: (CocoonFolderMenuRow) -> Unit,
    onDismiss: () -> Unit,
    onFocus: (Int) -> Unit,
) {
    ModalPresenceEffect()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickableNoFocus(onClick = onDismiss)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.42f)
                .background(CocoonSurface.Panel, RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
                .clickableNoFocus(enabled = false) {}
                .padding(horizontal = 10.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CocoonFolderMenuRow.entries.forEachIndexed { index, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .cocoonBrackets(focused = index == focusedIndex, outset = 3.dp)
                        .background(CocoonSurface.Chip, RoundedCornerShape(14.dp))
                        .clickableNoFocus {
                            onFocus(index)
                            onRow(row)
                        }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(row.icon, contentDescription = null, tint = CocoonSurface.TextPrimary, modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = stringResource(row.labelRes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = CocoonSurface.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    when {
                        row.hint != null -> InputGlyph(button = row.hint, size = 18.dp, tint = CocoonSurface.TextSecondary)
                        row.opensSubmenu -> Icon(
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
