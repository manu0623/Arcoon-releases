package com.nendo.argosy.data.media

/**
 * Whether 3DS banner drawing may run. Mirrors the "3DS banner art" setting: turning it off flips
 * this at once, and every banner being drawn checks it between frames and stops.
 */
object ThreeDsBannerWork {
    @Volatile
    var enabled: Boolean = true
}
