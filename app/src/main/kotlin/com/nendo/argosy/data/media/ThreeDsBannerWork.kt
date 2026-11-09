package com.nendo.argosy.data.media

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether 3DS banner drawing may run, and how far the banner being drawn is. Mirrors the "3DS
 * banner art" setting: turning it off flips [enabled] at once, and every banner being drawn checks
 * it between frames and stops.
 */
object ThreeDsBannerWork {
    @Volatile
    var enabled: Boolean = true

    /** The banner being drawn: which game (null when not known) and how far, 0 to 1. */
    data class Progress(val title: String?, val fraction: Float)

    private val _progress = MutableStateFlow<Progress?>(null)
    /** Shown as a progress notification while a banner is drawn; null when none is. */
    val progress: StateFlow<Progress?> = _progress.asStateFlow()

    @Volatile private var title: String? = null
    @Volatile private var owned = false

    /** A game's banner is about to be drawn; [end] once it is done (or stopped). */
    fun begin(gameTitle: String?) {
        title = gameTitle
        owned = true
        _progress.value = Progress(gameTitle, 0f)
    }

    fun end() {
        owned = false
        title = null
        _progress.value = null
    }

    /** Called by the renderer as frames are finished. */
    internal fun report(fraction: Float) {
        _progress.value = Progress(title, fraction.coerceIn(0f, 1f))
    }

    /** The renderer finished; clears the bar unless whoever began it will end it. */
    internal fun sceneDone() {
        if (!owned) _progress.value = null
    }
}
