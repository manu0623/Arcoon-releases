package com.nendo.argosy.ui.dualscreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
    /*
     * Follows the manager rather than reading it once: on a cold start the screen can be drawn
     * before the manager exists, or the manager can be replaced, and a slot published to nobody
     * (or to the old one) left the top screen showing nothing for the highlighted folder.
     */
    val manager = DualScreenManagerHolder.instanceFlow.collectAsState().value ?: return
    LaunchedEffect(manager, owner, slot) { manager.presentSlot(owner, slot) }
    DisposableEffect(manager, owner) { onDispose { manager.releaseSlot(owner) } }
}
