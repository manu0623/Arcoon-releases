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
    /*
     * Each screen that publishes has its own token. When the same screen is rebuilt (a board moved
     * to the other window, or recreated) the new one publishes before the old one leaves, and the
     * old one's release used to wipe the new slot, leaving the top screen empty until the cursor
     * moved. A leaving screen now releases only a slot it published itself.
     */
    val token = androidx.compose.runtime.remember { Any() }
    LaunchedEffect(manager, owner, slot) {
        SlotPublishers.latest[owner] = token
        manager.presentSlot(owner, slot)
    }
    DisposableEffect(manager, owner) {
        onDispose {
            if (SlotPublishers.latest[owner] === token) {
                SlotPublishers.latest.remove(owner)
                manager.releaseSlot(owner)
            }
        }
    }
}

/** Which on-screen publisher last presented each slot owner. */
private object SlotPublishers {
    val latest = java.util.concurrent.ConcurrentHashMap<SlotOwner, Any>()
}
