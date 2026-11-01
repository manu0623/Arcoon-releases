package com.nendo.argosy.ui.input

import com.nendo.argosy.data.emulator.VariantOption
import com.nendo.argosy.data.preferences.MenuWrapMode

class VariantPickerInputHandler(
    private val getVariants: () -> List<VariantOption>,
    private val getFocusIndex: () -> Int,
    private val onFocusChange: (Int) -> Unit,
    private val onSelect: (Long?) -> Unit,
    private val onDismiss: () -> Unit,
    private val wrapMode: () -> MenuWrapMode
) : InputHandler {

    override fun onUp(): InputResult = move(-1)

    override fun onDown(): InputResult = move(1)

    private fun move(delta: Int): InputResult {
        val size = getVariants().size
        if (size > 0) {
            onFocusChange(InputDispatcher.computeWrappedIndex(getFocusIndex(), delta, size - 1, wrapMode()))
        }
        return InputResult.HANDLED
    }

    override fun onConfirm(): InputResult {
        val variants = getVariants()
        val variant = variants.getOrNull(getFocusIndex())
        if (variant != null) {
            onSelect(variant.fileId)
        }
        return InputResult.HANDLED
    }

    override fun onBack(): InputResult {
        onDismiss()
        return InputResult.HANDLED
    }
}
