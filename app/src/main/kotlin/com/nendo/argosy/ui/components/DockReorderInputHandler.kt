package com.nendo.argosy.ui.components

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.CapturingInputHandler
import com.nendo.argosy.ui.input.InputResult

internal class DockReorderInputHandler(
    private val isRtl: Boolean,
    private val onMove: (Int) -> Unit,
    private val onSave: () -> Unit,
    private val onCancel: () -> Unit
) : CapturingInputHandler {
    override fun onLeft(): InputResult {
        onMove(if (isRtl) 1 else -1)
        return InputResult.HANDLED
    }

    override fun onRight(): InputResult {
        onMove(if (isRtl) -1 else 1)
        return InputResult.HANDLED
    }

    override fun onConfirm(): InputResult {
        onSave()
        return InputResult.handled(SoundType.TOGGLE)
    }

    override fun onBack(): InputResult {
        onCancel()
        return InputResult.handled(SoundType.BACK)
    }
}
