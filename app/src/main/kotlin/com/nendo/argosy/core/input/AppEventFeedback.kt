package com.nendo.argosy.core.input

/**
 * Plays the sound and haptic cue for an app event raised outside the UI layer, such as a
 * download starting or finishing.
 */
interface AppEventFeedback {

    fun play(type: SoundType)
}
