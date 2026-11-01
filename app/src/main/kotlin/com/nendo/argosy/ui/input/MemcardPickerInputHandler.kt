package com.nendo.argosy.ui.input

import com.nendo.argosy.data.preferences.MenuWrapMode
import com.nendo.argosy.data.sync.platform.MemcardInfo

class MemcardPickerInputHandler(
    private val getCards: () -> List<MemcardInfo>,
    private val getFocusIndex: () -> Int,
    private val onFocusChange: (Int) -> Unit,
    private val onSelect: (String) -> Unit,
    private val onDismiss: () -> Unit,
    private val wrapMode: () -> MenuWrapMode
) : InputHandler {

    override fun onUp(): InputResult = move(-1)

    override fun onDown(): InputResult = move(1)

    private fun move(delta: Int): InputResult {
        val size = getCards().size
        if (size > 0) {
            onFocusChange(InputDispatcher.computeWrappedIndex(getFocusIndex(), delta, size - 1, wrapMode()))
        }
        return InputResult.HANDLED
    }

    override fun onConfirm(): InputResult {
        val cards = getCards()
        val index = getFocusIndex()
        val card = cards.getOrNull(index)
        if (card != null) {
            onSelect(card.path)
        }
        return InputResult.HANDLED
    }

    override fun onBack(): InputResult {
        onDismiss()
        return InputResult.HANDLED
    }
}
