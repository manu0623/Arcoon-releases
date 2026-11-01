package com.nendo.argosy.ui.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.first

/**
 * Marks a modal as on screen for as long as it is composed, so app-wide shortcuts stay closed
 * while it is open whichever handler routes its input.
 */
@Composable
fun ModalPresenceEffect() {
    val inputDispatcher = LocalModalPresence.current ?: return
    DisposableEffect(inputDispatcher) {
        val token = Any()
        inputDispatcher.markModalShown(token)
        onDispose { inputDispatcher.markModalHidden(token) }
    }
}

val LocalModalPresence = staticCompositionLocalOf<InputDispatcher?> { null }

/**
 * Modal input registration with optional cancellation when another owner removes the handler.
 */
@Composable
fun ModalInputEffect(
    active: Boolean,
    handler: InputHandler,
    onInputLost: (() -> Unit)? = null,
    inputDispatcher: InputDispatcher = LocalInputDispatcher.current
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnInputLost by rememberUpdatedState(onInputLost)
    DisposableEffect(lifecycleOwner, handler, active, inputDispatcher) {
        if (!active) return@DisposableEffect onDispose { }
        inputDispatcher.pushModal(handler)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.pushModal(handler)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            inputDispatcher.removeModal(handler)
        }
    }
    LaunchedEffect(inputDispatcher, lifecycleOwner, handler, active, onInputLost != null) {
        if (active && onInputLost != null) {
            snapshotFlow { inputDispatcher.hasModal(handler) }.first { !it }
            currentOnInputLost?.invoke()
        }
    }
}
