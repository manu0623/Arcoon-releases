package com.nendo.argosy.ui.cocoon

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.mutableIntStateOf
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.nendo.argosy.data.media.EmbeddedMediaRepository

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CocoonJingleEntryPoint {
    fun ambientAudioManager(): com.nendo.argosy.ui.audio.AmbientAudioManager
}

/**
 * Cocoon's jingles: a short sound when you rest on a game. A game's own jingle wins, then its
 * console's default, then the global default. Background music steps aside while one plays.
 */
object CocoonJingles {
    private const val PREFS = "cocoon_jingles"
    private const val MAX_MS = 8_000L

    val version = mutableIntStateOf(0)
    private var player: MediaPlayer? = null
    private var holdingMusic = false
    private var stopper: Runnable? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(context: Context) = prefs(context).getBoolean("enabled", true)
    fun setEnabled(context: Context, value: Boolean) { prefs(context).edit().putBoolean("enabled", value).apply(); version.intValue++ }
    fun volume(context: Context) = prefs(context).getInt("volume", 70)
    fun setVolume(context: Context, value: Int) { prefs(context).edit().putInt("volume", value.coerceIn(0, 100)).apply(); version.intValue++ }

    fun gameJingle(context: Context, gameId: Long) = prefs(context).getString("game_$gameId", null)
    fun globalJingle(context: Context) = prefs(context).getString("global", null)

    fun setGameJingle(context: Context, gameId: Long, path: String?) = put(context, "game_$gameId", path)
    fun setGlobalJingle(context: Context, path: String?) = put(context, "global", path)

    /** Clear All Jingles: game assignments go; console and global defaults stay (as in Cocoon). */
    fun clearGameJingles(context: Context) {
        val p = prefs(context)
        p.edit().apply { p.all.keys.filter { it.startsWith("game_") }.forEach { remove(it) } }.apply()
        version.intValue++
    }

    private fun put(context: Context, key: String, path: String?) {
        prefs(context).edit().apply { if (path == null) remove(key) else putString(key, path) }.apply()
        version.intValue++
    }

    /**
     * The jingle a 3DS ROM carries in its own banner, extracted beside its art. A game the user has
     * assigned a jingle to still wins, because that assignment is an explicit choice.
     */
    private fun embeddedJingle(context: Context, gameId: Long): String? =
        EmbeddedMediaRepository.cachedJingleFile(context, gameId.toString())?.absolutePath

    /** A jingle is the game's own sound, or the one default; consoles have no say in it. */
    fun resolve(context: Context, gameId: Long): String? =
        listOfNotNull(
            gameJingle(context, gameId),
            embeddedJingle(context, gameId),
            globalJingle(context)
        )
            .firstOrNull { java.io.File(it).isFile }

    fun playFor(context: Context, gameId: Long) {
        if (!enabled(context)) return
        val path = resolve(context, gameId) ?: return
        play(context, path)
    }

    fun play(context: Context, path: String) {
        stop(context)
        val volume = (volume(context) / 100f).let { it * it }
        val mp = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(path)
                setVolume(volume, volume)
                setOnCompletionListener { stop(context) }
                prepare()
            }
        }.getOrNull() ?: return
        player = mp
        runCatching { music(context).holdSilence(); holdingMusic = true }
        mp.start()
        // long clips are faded out rather than played to the end
        val stopRun = Runnable { fadeOutAndStop(context) }
        stopper = stopRun
        handler.postDelayed(stopRun, MAX_MS)
    }

    private fun fadeOutAndStop(context: Context) {
        val mp = player ?: return
        val start = volume(context) / 100f
        val steps = 10
        for (i in 1..steps) {
            handler.postDelayed({
                if (player === mp) {
                    val v = start * (1f - i / steps.toFloat())
                    runCatching { mp.setVolume(v * v, v * v) }
                    if (i == steps) stop(context)
                }
            }, i * 40L)
        }
    }

    fun stop(context: Context) {
        stopper?.let { handler.removeCallbacks(it) }
        stopper = null
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
        if (holdingMusic) {
            holdingMusic = false
            runCatching { music(context).releaseSilence() }
        }
    }

    private fun music(context: Context) =
        EntryPointAccessors.fromApplication(context.applicationContext, CocoonJingleEntryPoint::class.java).ambientAudioManager()
}
