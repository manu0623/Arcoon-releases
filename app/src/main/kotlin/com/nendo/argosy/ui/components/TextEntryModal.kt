package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import com.nendo.argosy.ui.primitives.ModalActionButton
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

const val TEXT_ENTRY_CANCEL_BUTTON = 0
const val TEXT_ENTRY_CONFIRM_BUTTON = 1

enum class TextEntryRow { FIELD, BUTTONS }

/**
 * Where the gamepad cursor sits inside a [TextEntryModal]. Owned by whoever routes the
 * modal's input: a ViewModel when the host screen routes it, the wrapper when the modal
 * captures input itself. A null focus on the modal means touch only.
 */
data class TextEntryFocus(
    val row: TextEntryRow = TextEntryRow.FIELD,
    val buttonIndex: Int = TEXT_ENTRY_CONFIRM_BUTTON
)

data class TextEntryAction(val label: String, val onClick: () -> Unit)

/**
 * One-field editor with [leadingActions], Cancel and confirm, in that order; [TextEntryFocus.buttonIndex]
 * counts across all of them. Pure visuals: it captures no input, and the caller owns [text].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextEntryModal(
    title: String,
    label: String,
    confirmLabel: String,
    cancelLabel: String,
    text: String,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
    focus: TextEntryFocus?,
    placeholder: String? = null,
    message: String? = null,
    errorMessage: String? = null,
    statusMessage: String? = null,
    canSubmit: Boolean = text.isNotBlank(),
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingActions: List<TextEntryAction> = emptyList()
) {
    val theme = LocalArgosyTheme.current
    var fieldValue by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val fieldFocused = focus == null || focus.row == TextEntryRow.FIELD
    val confirmEnabled = enabled && canSubmit
    val submit = { if (confirmEnabled) onSubmit() }
    val cancelIndex = leadingActions.size + TEXT_ENTRY_CANCEL_BUTTON
    val confirmIndex = leadingActions.size + TEXT_ENTRY_CONFIRM_BUTTON
    fun buttonFocused(index: Int): Boolean = focus?.row == TextEntryRow.BUTTONS && focus.buttonIndex == index

    LaunchedEffect(text) {
        if (fieldValue.text != text) fieldValue = TextFieldValue(text, TextRange(text.length))
    }

    LaunchedEffect(fieldFocused) {
        if (fieldFocused) focusRequester.requestFocus() else focusManager.clearFocus()
    }

    val fieldShape = RoundedCornerShape(Dimens.radiusMd)
    Modal(title = title, onDismiss = onDismiss) {
        if (message != null) {
            Text(text = message, style = MaterialTheme.typography.bodySmall, color = theme.textDim)
            Spacer(modifier = Modifier.height(Dimens.spacingMd))
        }
        OutlinedTextField(
            value = fieldValue,
            onValueChange = { next ->
                val changed = next.text != fieldValue.text
                fieldValue = next
                if (changed) onTextChange(next.text)
            },
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            singleLine = true,
            enabled = enabled,
            visualTransformation = visualTransformation,
            shape = fieldShape,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .then(
                    if (focus?.row == TextEntryRow.FIELD) {
                        Modifier.background(theme.focusAccent.copy(alpha = 0.15f), fieldShape)
                    } else {
                        Modifier
                    }
                ),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() })
        )
        val shownStatus = errorMessage ?: statusMessage
        if (shownStatus != null) {
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            Text(
                text = shownStatus,
                style = MaterialTheme.typography.bodySmall,
                color = if (errorMessage != null) MaterialTheme.colorScheme.error else theme.textDim
            )
        }
        Spacer(modifier = Modifier.height(Dimens.spacingLg))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            leadingActions.forEachIndexed { index, action ->
                ModalActionButton(
                    label = action.label,
                    tint = theme.focusAccent,
                    restLabelColor = theme.textPrimary,
                    focused = buttonFocused(index),
                    onClick = action.onClick,
                    enabled = enabled
                )
            }
            ModalActionButton(
                label = cancelLabel,
                tint = theme.focusAccent,
                restLabelColor = theme.textPrimary,
                focused = buttonFocused(cancelIndex),
                onClick = onDismiss,
                enabled = enabled
            )
            ModalActionButton(
                label = confirmLabel,
                tint = theme.focusAccent,
                restLabelColor = theme.textPrimary,
                focused = buttonFocused(confirmIndex),
                onClick = submit,
                enabled = confirmEnabled
            )
        }
    }
}
