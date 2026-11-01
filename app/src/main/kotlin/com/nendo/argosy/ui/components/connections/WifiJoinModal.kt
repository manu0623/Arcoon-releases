package com.nendo.argosy.ui.components.connections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.components.TEXT_ENTRY_CANCEL_BUTTON
import com.nendo.argosy.ui.components.TEXT_ENTRY_CONFIRM_BUTTON
import com.nendo.argosy.ui.components.TextEntryModal
import com.nendo.argosy.ui.components.TextEntryRow
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.ModalInputEffect

private class WifiJoinInputHandler(private val controller: QuickConnectionsController) : InputHandler {
    private fun row(): TextEntryRow? = controller.state.value.join?.focus?.row

    override fun onUp(): InputResult {
        controller.setJoinFocus(TextEntryRow.FIELD)
        return InputResult.HANDLED
    }

    override fun onDown(): InputResult {
        controller.setJoinFocus(TextEntryRow.BUTTONS)
        return InputResult.HANDLED
    }

    override fun onLeft(): InputResult {
        if (row() == TextEntryRow.BUTTONS) controller.setJoinFocus(TextEntryRow.BUTTONS, TEXT_ENTRY_CANCEL_BUTTON)
        return InputResult.HANDLED
    }

    override fun onRight(): InputResult {
        if (row() == TextEntryRow.BUTTONS) controller.setJoinFocus(TextEntryRow.BUTTONS, TEXT_ENTRY_CONFIRM_BUTTON)
        return InputResult.HANDLED
    }

    override fun onConfirm(): InputResult {
        controller.confirmJoinFocus()
        return InputResult.HANDLED
    }

    override fun onBack(): InputResult {
        controller.dismissJoin()
        return InputResult.handled(SoundType.CLOSE_MODAL)
    }

    override fun onMenu(): InputResult = InputResult.HANDLED
    override fun onSecondaryAction(): InputResult = InputResult.HANDLED
    override fun onContextMenu(): InputResult = InputResult.HANDLED
    override fun onPrevSection(): InputResult = InputResult.HANDLED
    override fun onNextSection(): InputResult = InputResult.HANDLED
    override fun onPrevTrigger(): InputResult = InputResult.HANDLED
    override fun onNextTrigger(): InputResult = InputResult.HANDLED
    override fun onSelect(): InputResult = InputResult.HANDLED
    override fun onLeftStickClick(): InputResult = InputResult.HANDLED
    override fun onRightStickClick(): InputResult = InputResult.HANDLED
    override fun onLongConfirm(): InputResult = InputResult.HANDLED
}

@Composable
fun QuickConnectionsModals(
    state: QuickConnectionsState,
    controller: QuickConnectionsController
) {
    val draft = state.join ?: return
    val handler = remember(controller) { WifiJoinInputHandler(controller) }
    val passwordMask = remember { PasswordVisualTransformation() }
    ModalInputEffect(active = true, handler = handler)

    TextEntryModal(
        title = stringResource(R.string.ui_quick_connections_join_title, draft.ssid),
        label = stringResource(R.string.ui_quick_connections_join_password),
        confirmLabel = stringResource(
            if (draft.busy) R.string.ui_quick_connections_join_busy else R.string.ui_quick_connections_join_confirm
        ),
        cancelLabel = stringResource(R.string.ui_quick_connections_join_cancel),
        text = draft.password,
        onTextChange = controller::setJoinPassword,
        onDismiss = controller::dismissJoin,
        onSubmit = controller::submitJoin,
        focus = draft.focus,
        errorMessage = if (draft.failed) stringResource(R.string.ui_quick_connections_join_failed) else null,
        canSubmit = draft.canSubmit,
        enabled = !draft.busy,
        keyboardType = KeyboardType.Password,
        visualTransformation = passwordMask
    )
}
