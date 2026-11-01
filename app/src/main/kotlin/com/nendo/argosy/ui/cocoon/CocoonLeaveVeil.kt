package com.nendo.argosy.ui.cocoon

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.flow.first

/**
 * Armed the moment a game starts or resumes, so the launcher paints itself black *before* it is
 * covered. Android keeps that last frame, and when Home brings the launcher back it slides that
 * frame in: black on black, so the slide shows nothing. The launcher then fades up from black.
 */
object CocoonVeil {
    val armed = mutableStateOf(false)
    @Volatile var armedAt = 0L
    /** Last time a launcher window took focus on the screen the game was on. */
    @Volatile var returnedAt = 0L
    fun arm() { armedAt = System.currentTimeMillis(); armed.value = true }
    fun onLauncherReturned() { returnedAt = System.currentTimeMillis() }
}

@Composable
fun CocoonLeaveVeil(modifier: Modifier = Modifier, blackOnArm: Boolean = true) {
    val armed by CocoonVeil.armed
    val alpha = remember { Animatable(0f) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(armed) {
        if (!armed) return@LaunchedEffect
        if (blackOnArm) alpha.snapTo(1f)
        // Wait for the game to cover us; if it never does (launch failed, or this window stays
        // up beside the game), let go again.
        val left = kotlinx.coroutines.withTimeoutOrNull(2_500) {
            lifecycle.currentStateFlow.first { !it.isAtLeast(Lifecycle.State.RESUMED) }
        }
        if (left != null) {
            alpha.snapTo(1f)
            // Wait for the game to leave: it suspended, or the launcher took focus back.
            val since = CocoonVeil.armedAt
            val sessions = com.nendo.argosy.DualScreenManagerHolder.instance?.gameSessionManager
            while (true) {
                val suspended = (sessions?.lastSuspendedAt ?: 0L) > since
                if (suspended || CocoonVeil.returnedAt > since) break
                kotlinx.coroutines.delay(40)
            }
            // Hold black while Android's slide runs, then fade in.
            kotlinx.coroutines.delay(com.nendo.argosy.ui.dualscreen.COCOON_LEAVE_HOLD_MS)
        }
        alpha.animateTo(0f, tween(260))
        CocoonVeil.armed.value = false
    }
    if (alpha.value > 0f) {
        Box(modifier = modifier.fillMaxSize().background(Color.Black.copy(alpha = alpha.value)))
    }
}
