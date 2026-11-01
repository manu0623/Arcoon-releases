package com.nendo.argosy.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.primitives.ArgosyConfirmModalHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface GameConfirm {
    val gameId: Long
    val title: String

    data class DeleteDownload(
        override val gameId: Long,
        override val title: String,
        val hasSoundtrack: Boolean,
        val unsyncedSaves: Int
    ) : GameConfirm

    data class Hide(
        override val gameId: Long,
        override val title: String
    ) : GameConfirm
}

/**
 * Holds one screen's pending game confirmation. The action passed to a request runs only once the
 * player confirms, and receives whether the soundtrack goes too.
 */
class GameConfirmController(
    private val buildDelete: suspend (Long) -> GameConfirm.DeleteDownload?,
    private val buildHide: suspend (Long) -> GameConfirm.Hide?,
    private val scope: CoroutineScope
) {
    private val _pending = MutableStateFlow<GameConfirm?>(null)
    val pending: StateFlow<GameConfirm?> = _pending.asStateFlow()
    private var onConfirmed: ((Boolean) -> Unit)? = null

    fun requestDelete(gameId: Long, onConfirmed: (withSoundtrack: Boolean) -> Unit) {
        scope.launch { show(buildDelete(gameId) ?: return@launch, onConfirmed) }
    }

    fun requestHide(gameId: Long, onConfirmed: () -> Unit) {
        scope.launch { show(buildHide(gameId) ?: return@launch) { onConfirmed() } }
    }

    fun confirm(withSoundtrack: Boolean) {
        val action = onConfirmed ?: return
        dismiss()
        action(withSoundtrack)
    }

    fun dismiss() {
        onConfirmed = null
        _pending.value = null
    }

    private fun show(confirm: GameConfirm, action: (Boolean) -> Unit) {
        onConfirmed = action
        _pending.value = confirm
    }
}

@Composable
fun GameConfirmHost(controller: GameConfirmController) {
    val pending by controller.pending.collectAsState()
    GameConfirmModal(
        confirm = pending,
        onConfirm = controller::confirm,
        onDismiss = controller::dismiss
    )
}

@Composable
fun GameConfirmModal(
    confirm: GameConfirm?,
    onConfirm: (withSoundtrack: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    when (confirm) {
        null -> Unit
        is GameConfirm.Hide -> ArgosyConfirmModalHost(
            visible = true,
            title = stringResource(R.string.common_game_confirm_hide_title, confirm.title),
            message = stringResource(R.string.common_game_confirm_hide_message),
            confirmLabel = stringResource(R.string.common_game_confirm_hide_confirm),
            onConfirm = { onConfirm(false) },
            onDismiss = onDismiss
        )
        is GameConfirm.DeleteDownload -> {
            val saveWarning = if (confirm.unsyncedSaves > 0) {
                " " + pluralStringResource(
                    R.plurals.settings_shell_unsynced_saves_warning,
                    confirm.unsyncedSaves,
                    confirm.unsyncedSaves
                )
            } else {
                ""
            }
            if (confirm.hasSoundtrack) {
                ArgosyConfirmModalHost(
                    visible = true,
                    title = stringResource(R.string.settings_shell_modal_delete_game_soundtrack_title, confirm.title),
                    message = stringResource(R.string.settings_shell_modal_delete_game_soundtrack_message, saveWarning),
                    cancelLabel = stringResource(R.string.settings_shell_modal_delete_game_cancel),
                    neutralLabel = stringResource(R.string.settings_shell_modal_delete_game_only),
                    onNeutral = { onConfirm(false) },
                    confirmLabel = stringResource(R.string.settings_shell_modal_delete_game_with_soundtrack),
                    destructive = true,
                    onConfirm = { onConfirm(true) },
                    onDismiss = onDismiss
                )
            } else {
                ArgosyConfirmModalHost(
                    visible = true,
                    title = stringResource(R.string.settings_shell_modal_delete_game_plain_title, confirm.title),
                    message = stringResource(R.string.settings_shell_modal_delete_game_plain_message, saveWarning),
                    confirmLabel = stringResource(R.string.settings_shell_modal_delete_game_plain_confirm),
                    destructive = true,
                    onConfirm = { onConfirm(false) },
                    onDismiss = onDismiss
                )
            }
        }
    }
}
