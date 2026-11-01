package com.nendo.argosy.ui.input

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import android.util.Log
import com.nendo.argosy.R
import com.nendo.argosy.core.input.AppEventFeedback
import com.nendo.argosy.core.input.SoundConfig
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.core.media.SfxCacheCleaner
import com.nendo.argosy.core.media.SfxTranscoder
import com.nendo.argosy.data.storage.StorageAttributionRepository
import com.nendo.argosy.data.storage.StorageCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "SoundFeedback"

enum class SoundPreset(val resourceId: Int?, val displayName: String) {
    CLICK_SOFT(R.raw.click_soft, "Click Soft"),
    TAP_LIGHT(R.raw.tap_light, "Tap Light"),
    POP_CONFIRM(R.raw.pop_confirm, "Pop Confirm"),
    SWIPE_BACK(R.raw.swipe_back, "Swipe Back"),
    POP_CLOSE(R.raw.pop_close, "Pop Close"),
    BUZZ_ERROR(R.raw.buzz_error, "Buzz Error"),
    CHIME_OPEN(R.raw.chime_open, "Chime Open"),
    HOVER_SOFT(R.raw.hover_soft, "Hover Soft"),
    DING_PICKUP(R.raw.ding_pickup, "Ding Pickup"),
    NOTIFY_START(R.raw.notify_start, "Notify Start"),
    CHIME_SUCCESS(R.raw.chime_success, "Chime Success"),
    TICK_ACCEPT(R.raw.tick_accept, "Tick Accept"),
    BELL_HIGH(R.raw.bell_high, "Bell High"),
    COLLECT(R.raw.collect, "Collect"),
    DISMISS_FAIL(R.raw.dismiss_fail, "Dismiss Fail"),
    POP(R.raw.pop, "Pop"),
    LAUNCH(R.raw.launch, "Launch"),
    SAVING(R.raw.saving, "Saving"),
    SELECT(R.raw.select, "Select"),
    NAVIGATE(R.raw.navigate, "Navigate"),
    OPEN_EDIT(R.raw.open_edit, "Open Edit"),
    SAVING_END(R.raw.saving_end, "Saving End"),
    FOLDER_OPEN(R.raw.folder_open, "Folder Open"),
    SCREEN_SWAP(R.raw.screen_swap, "Screen Swap"),
    DISCORD_OPEN(R.raw.discord_open, "Discord Open"),
    FOLDER_CLOSE(R.raw.folder_close, "Folder Close"),
    GRID_ZOOM_IN(R.raw.grid_zoom_in, "Grid Zoom In"),
    DISCORD_CLOSE(R.raw.discord_close, "Discord Close"),
    GRID_ZOOM_OUT(R.raw.grid_zoom_out, "Grid Zoom Out"),
    REORDER_PLACE(R.raw.reorder_place, "Reorder Place"),
    REORDER_PICKUP(R.raw.reorder_pickup, "Reorder Pickup"),
    SLIDER_DECREASE(R.raw.slider_decrease, "Slider Decrease"),
    SLIDER_INCREASE(R.raw.slider_increase, "Slider Increase"),
    RETROACHIEVEMENTS(R.raw.retroachievements, "RetroAchievements"),
    SILENT(null, "Silent"),
    CUSTOM(null, "Custom File"),
    ROMM_MUSIC(null, "RomM Music"),
    DEFAULT(null, "Default");
}

@Singleton
class SoundFeedbackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val attributionRepository: StorageAttributionRepository,
    private val hapticManager: HapticFeedbackManager
) : AppEventFeedback, SfxCacheCleaner {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initLock = Any()

    @Volatile
    private var enabled = true

    @Volatile
    private var volume = 0.4f

    @Volatile
    private var soundPool: SoundPool? = null
    private val soundIds = ConcurrentHashMap<SoundPreset, Int>()
    private val customSoundIds = ConcurrentHashMap<SoundType, Int>()
    private val customSoundPaths = ConcurrentHashMap<SoundType, String>()
    private val loadedSampleIds: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    @Volatile
    private var soundConfigs: Map<SoundType, SoundConfig> = emptyMap()

    /** What the user set in Settings ▸ Sounds; their own files beat the theme's. */
    @Volatile
    private var userConfigs: Map<SoundType, SoundConfig> = emptyMap()

    /** Sound files from the active Cocoon theme, by sound type. */
    @Volatile
    private var themeSounds: Map<SoundType, String> = emptyMap()

    init {
        preload()
        com.nendo.argosy.ui.cocoon.theme.CocoonThemeEngine.addListener { theme ->
            themeSounds = cocoonThemeSoundMap(theme?.sounds.orEmpty())
            soundConfigs = mergeConfigs()
            refreshCustomSounds()
        }
    }

    private fun mergeConfigs(): Map<SoundType, SoundConfig> {
        val merged = userConfigs.toMutableMap()
        themeSounds.forEach { (type, path) ->
            if (userConfigs[type]?.customFilePath == null) merged[type] = SoundConfig(customFilePath = path)
        }
        return merged
    }

    @Volatile
    private var lastBoundaryPlayMs = 0L

    private val defaultPresetMap = mapOf(
        // navigate.ogg in res/raw is a copy of the achievement jingle (same audio, 2 s long),
        // so moving the cursor uses the short tick until a real navigate sound replaces it.
        SoundType.NAVIGATE to SoundPreset.TAP_LIGHT,
        SoundType.BOUNDARY to SoundPreset.BUZZ_ERROR,
        SoundType.SECTION_CHANGE to SoundPreset.TAP_LIGHT,
        SoundType.SELECT to SoundPreset.SELECT,
        SoundType.BACK to SoundPreset.FOLDER_CLOSE,
        SoundType.OPEN_MODAL to SoundPreset.FOLDER_OPEN,
        SoundType.CLOSE_MODAL to SoundPreset.FOLDER_CLOSE,
        SoundType.FAVORITE to SoundPreset.POP,
        SoundType.UNFAVORITE to SoundPreset.POP,
        SoundType.DOWNLOAD_START to SoundPreset.SAVING,
        SoundType.DOWNLOAD_COMPLETE to SoundPreset.SAVING_END,
        SoundType.DOWNLOAD_CANCEL to SoundPreset.DISMISS_FAIL,
        SoundType.ERROR to SoundPreset.BUZZ_ERROR,
        SoundType.VOLUME_PREVIEW to SoundPreset.CLICK_SOFT,
        SoundType.TOGGLE to SoundPreset.CLICK_SOFT,
        SoundType.LAUNCH_GAME to SoundPreset.COLLECT
    )

    private val presetPriorities = mapOf(
        SoundPreset.TAP_LIGHT to PRIORITY_LOW,
        SoundPreset.HOVER_SOFT to PRIORITY_LOW,
        SoundPreset.CLICK_SOFT to PRIORITY_LOW,
        SoundPreset.CHIME_SUCCESS to PRIORITY_HIGH,
        SoundPreset.COLLECT to PRIORITY_HIGH,
        SoundPreset.BUZZ_ERROR to PRIORITY_HIGH,
        SoundPreset.NOTIFY_START to PRIORITY_HIGH
    )

    companion object {
        private const val PRIORITY_LOW = 1
        private const val PRIORITY_DEFAULT = 2
        private const val PRIORITY_HIGH = 3
        private const val BOUNDARY_RATE_LIMIT_MS = 500L
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (enabled) preload() else releaseSoundPool()
    }

    private fun preload() {
        scope.launch {
            synchronized(initLock) {
                if (!enabled || soundPool != null) return@launch
                initSoundPool()
            }
        }
    }

    private fun initSoundPool() {
        val audioAttributes = AudioAttributes.Builder()
            // USAGE_GAME plays on the media volume. The old "sonification" usage went to the system
            // volume, which handhelds like the Thor keep muted (and tie to "Touch sounds"), so the
            // interface was silent.
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val pool = SoundPool.Builder()
            .setMaxStreams(10)
            .setAudioAttributes(audioAttributes)
            .build().apply {
                setOnLoadCompleteListener { _, sampleId, status ->
                    if (status == 0) loadedSampleIds.add(sampleId)
                }
            }
        soundPool = pool

        SoundPreset.entries.forEach { preset ->
            preset.resourceId?.let { resId ->
                try {
                    soundIds[preset] = pool.load(context, resId, 1)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load ${preset.name}", e)
                }
            }
        }
        syncCustomSounds(pool)
    }

    private fun syncCustomSounds(pool: SoundPool) {
        val desired = soundConfigs.entries
            .mapNotNull { (type, config) -> config.customFilePath?.let { type to it } }
            .toMap()
        customSoundPaths.keys.filterNot { it in desired }.forEach { type ->
            unloadCustomSound(pool, type)
        }
        desired.forEach { (type, path) ->
            if (customSoundPaths[type] != path) {
                unloadCustomSound(pool, type)
                loadCustomSound(pool, type, path)
            }
        }
    }

    private fun unloadCustomSound(pool: SoundPool, type: SoundType) {
        customSoundPaths.remove(type)
        customSoundIds.remove(type)?.let { sampleId ->
            loadedSampleIds.remove(sampleId)
            pool.unload(sampleId)
        }
    }

    private fun loadCustomSound(pool: SoundPool, type: SoundType, path: String) {
        if (!File(path).exists()) {
            Log.w(TAG, "Custom sound missing for ${type.name}: $path")
            return
        }
        val playablePath = resolvePlayablePath(path)
        if (playablePath == null) {
            Log.w(TAG, "Skipping custom sound for ${type.name}: transcode failed for $path")
            return
        }
        try {
            customSoundIds[type] = pool.load(playablePath, 1)
            customSoundPaths[type] = path
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load custom sound for ${type.name}", e)
        }
    }

    private fun resolvePlayablePath(path: String): String? {
        if (path.substringAfterLast('.', "").lowercase() == "wav") return path
        return SfxTranscoder.transcodeToWavCache(context, path)?.absolutePath
    }

    private fun releaseSoundPool() {
        synchronized(initLock) {
            soundPool?.release()
            soundPool = null
            soundIds.clear()
            customSoundIds.clear()
            customSoundPaths.clear()
            loadedSampleIds.clear()
        }
    }

    private fun shouldPlaySound(type: SoundType): Boolean {
        if (!enabled) return false
        if (type == SoundType.SILENT) return false
        val config = soundConfigs[type]
        if (config?.presetName == SoundPreset.SILENT.name) return false
        return true
    }

    fun setVolume(volume: Int) {
        val linear = (volume / 100f).coerceIn(0f, 1f)
        this.volume = linear * linear
    }

    fun setSoundConfigs(configs: Map<SoundType, SoundConfig>) {
        userConfigs = configs
        soundConfigs = mergeConfigs()
        refreshCustomSounds()
    }

    fun setSoundConfig(type: SoundType, config: SoundConfig) {
        userConfigs = userConfigs + (type to config)
        soundConfigs = mergeConfigs()
        refreshCustomSounds()
    }

    fun clearSoundConfig(type: SoundType) {
        userConfigs = userConfigs - type
        soundConfigs = mergeConfigs()
        refreshCustomSounds()
    }

    fun playDefault(type: SoundType) {
        defaultPresetMap[type]?.let { playPreset(it) }
    }

    private fun refreshCustomSounds() {
        scope.launch {
            synchronized(initLock) {
                soundPool?.let { syncCustomSounds(it) }
            }
        }
    }

    fun getSoundConfig(type: SoundType): SoundConfig? = userConfigs[type]

    fun playPreset(preset: SoundPreset) {
        if (!enabled) return
        if (preset == SoundPreset.SILENT || preset == SoundPreset.CUSTOM) return

        val pool = soundPool
        if (pool == null) {
            preload()
            return
        }
        val soundId = soundIds[preset] ?: return
        if (soundId !in loadedSampleIds) return

        try {
            val priority = presetPriorities[preset] ?: PRIORITY_DEFAULT
            pool.play(soundId, volume, volume, priority, 0, 1f)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play preset ${preset.name}", e)
        }
    }

    /**
     * Plays the feedback for an app event: the event's haptic cue ([hapticCue]) and its sound.
     * Use [playSound] where the caller plays its own haptic cue or previews a sound.
     */
    override fun play(type: SoundType) {
        type.hapticCue?.let(hapticManager::vibrate)
        playSound(type)
    }

    fun playSound(type: SoundType) {
        if (!shouldPlaySound(type)) return
        if (type == SoundType.BOUNDARY) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastBoundaryPlayMs < BOUNDARY_RATE_LIMIT_MS) return
            lastBoundaryPlayMs = now
        }

        val config = soundConfigs[type]
        if (config?.customFilePath != null && playCustomSample(type)) return
        val preset = config?.presetName
            ?.let { name -> SoundPreset.entries.find { it.name == name } }
            ?.takeIf { it.resourceId != null }
            ?: defaultPresetMap[type]
            ?: return
        playPreset(preset)
    }

    /** Plays the loaded custom sample assigned to this type, if any; for picker previews. */
    fun playCustom(type: SoundType) {
        if (!enabled) return
        playCustomSample(type)
    }

    private fun playCustomSample(type: SoundType): Boolean {
        val pool = soundPool ?: return false
        val soundId = customSoundIds[type] ?: return false
        if (soundId !in loadedSampleIds) return false
        return try {
            pool.play(soundId, volume, volume, PRIORITY_DEFAULT, 0, 1f) != 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play custom sound for ${type.name}", e)
            false
        }
    }

    fun release() {
        releaseSoundPool()
    }

    override suspend fun clearSfxCache() = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "sfx")
        if (dir.exists()) dir.deleteRecursively()
        attributionRepository.markDirty(StorageCategory.SFX_CACHE)
    }
}


/** Cocoon's theme sound names mapped onto Arcoon's sound types. */
private fun cocoonThemeSoundMap(files: Map<String, java.io.File>): Map<SoundType, String> {
    fun f(vararg names: String): String? = names.firstNotNullOfOrNull { files[it]?.absolutePath }
    return buildMap {
        f("navigate")?.let { put(SoundType.NAVIGATE, it); put(SoundType.SECTION_CHANGE, it) }
        f("select")?.let { put(SoundType.SELECT, it) }
        f("back", "folder_close")?.let { put(SoundType.BACK, it) }
        f("folder_open", "open_edit")?.let { put(SoundType.OPEN_MODAL, it) }
        f("folder_close", "open_edit")?.let { put(SoundType.CLOSE_MODAL, it) }
        f("launch")?.let { put(SoundType.LAUNCH_GAME, it) }
        f("error")?.let { put(SoundType.ERROR, it); put(SoundType.BOUNDARY, it) }
        f("pop")?.let { put(SoundType.TOGGLE, it); put(SoundType.FAVORITE, it); put(SoundType.UNFAVORITE, it) }
        f("saving")?.let { put(SoundType.DOWNLOAD_START, it) }
        f("saving_end")?.let { put(SoundType.DOWNLOAD_COMPLETE, it) }
        f("slider_increase", "slider_decrease")?.let { put(SoundType.VOLUME_PREVIEW, it) }
    }
}
