package com.nendo.argosy.ui.dualscreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.nendo.argosy.DualScreenManagerHolder

/**
 * Publishes [slot] on the presentation screen for as long as this composable is on screen, and
 * releases it on the way out.
 *
 * Tying publication to composition is what keeps the two screens in step: a screen cannot be shown
 * without publishing or left without releasing, whichever way it is entered or left, and a screen
 * returned to republishes from the state its own ViewModel still holds.
 */
@Composable
fun PresentOnCompanion(owner: SlotOwner, slot: PresentationSlot) {
    val manager = DualScreenManagerHolder.instance ?: return
    LaunchedEffect(owner, slot) { manager.presentSlot(owner, slot) }
    DisposableEffect(owner) { onDispose { manager.releaseSlot(owner) } }
}
