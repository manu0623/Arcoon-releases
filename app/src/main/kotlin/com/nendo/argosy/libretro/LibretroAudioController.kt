package com.nendo.argosy.libretro

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.SystemClock
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.swordfish.libretrodroid.GLRetroView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The volume [elapsedMs] into a linear rise from silence to [target] over [durationMs], clamped
 * to the rise. A zero or negative duration is no rise at all.
 */
fun riseVolume(elapsedMs: Long, durationMs: Int, target: Float): Float {
    if (durationMs <= 0) return target
    val fraction = (elapsedMs.toFloat() / durationMs).coerceIn(0f, 1f)
    return target * fraction
}

/**
 * Owns audio session configuration for a libretro session: audio focus
 * acquisition/abandonment, SoundTouch pitch preservation toggling for
 * fast-forward, and the core's output volume, which it alone writes so the
 * rise from silence on the first frame and a volume the player sets cannot
 * fight over it.
 */
class LibretroAudioController(
    private val context: Context,
    private val getRetroView: () -> GLRetroView
) {
    private var audioFocusRequest: AudioFocusRequest? = null
    private var configuredVolume = 1f
    private var rise: Job? = null

    fun requestAudioFocus() {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setOnAudioFocusChangeListener { }
            .build()
        audioFocusRequest = request
        am.requestAudioFocus(request)
    }

    fun abandonAudioFocus() {
        val request = audioFocusRequest ?: return
        val am = context.getSystemService(AudioManager::class.java) ?: return
        am.abandonAudioFocusRequest(request)
        audioFocusRequest = null
    }

    /**
     * Configures audio before the core loads. The core starts silent and [riseFromFirstFrame]
     * brings it up to [audioVolume].
     */
    fun applyInitialAudioConfig(pitchPreservationEnabled: Boolean, audioVolume: Float) {
        val rv = getRetroView()
        rv.audioEnabled = true
        rv.pitchPreservationEnabled = pitchPreservationEnabled
        configuredVolume = audioVolume
        rv.audioVolume = 0f
    }

    /**
     * Raises the core's volume from silence to the configured gain over
     * [ComponentDefaults.LaunchOverlay.audioRiseMs], stepping every [RISE_TICK_MS]. A later
     * [setVolume] cancels it.
     */
    fun riseFromFirstFrame(scope: CoroutineScope) {
        rise?.cancel()
        val target = configuredVolume
        val durationMs = ComponentDefaults.LaunchOverlay.audioRiseMs
        val startedAt = SystemClock.uptimeMillis()
        rise = scope.launch {
            var volume = 0f
            while (isActive && volume < target) {
                volume = riseVolume(SystemClock.uptimeMillis() - startedAt, durationMs, target)
                getRetroView().audioVolume = volume
                if (volume < target) delay(RISE_TICK_MS)
            }
        }
    }

    /**
     * Sets the core's volume to [volume] at once, ending any rise in progress.
     */
    fun setVolume(volume: Float) {
        rise?.cancel()
        rise = null
        configuredVolume = volume
        getRetroView().audioVolume = volume
    }

    fun setPitchPreservationEnabled(enabled: Boolean) {
        getRetroView().pitchPreservationEnabled = enabled
    }

    private companion object {
        const val RISE_TICK_MS = 50L
    }
}
