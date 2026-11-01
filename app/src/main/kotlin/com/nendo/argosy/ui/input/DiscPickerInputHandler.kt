package com.nendo.argosy.ui.input

import com.nendo.argosy.data.emulator.DiscOption
import com.nendo.argosy.data.preferences.MenuWrapMode

class DiscPickerInputHandler(
    private val getDiscs: () -> List<DiscOption>,
    private val getFocusIndex: () -> Int,
    private val onFocusChange: (Int) -> Unit,
    private val onSelect: (String) -> Unit,
    private val onDismiss: () -> Unit,
    private val wrapMode: () -> MenuWrapMode
) : InputHandler {

    override fun onUp(): InputResult = move(-1)

    override fun onDown(): InputResult = move(1)

    private fun move(delta: Int): InputResult {
        val size = getDiscs().size
        if (size > 0) {
            onFocusChange(InputDispatcher.computeWrappedIndex(getFocusIndex(), delta, size - 1, wrapMode()))
        }
        return InputResult.HANDLED
    }

    override fun onConfirm(): InputResult {
        val discs = getDiscs()
        val index = getFocusIndex()
        val disc = discs.getOrNull(index)
        if (disc != null) {
            onSelect(disc.filePath)
        }
        return InputResult.HANDLED
    }

    override fun onBack(): InputResult {
        onDismiss()
        return InputResult.HANDLED
    }
}
