package com.nendo.argosy.ui.cocoon

import android.content.Context

/**
 * Shared bits of the board's saved layout, usable from outside the board (a game's Y menu adds
 * itself to Home through here). The board reads the same "cocoon_board" preferences.
 */
object CocoonHomeStore {
    private const val PREFS = "cocoon_board"

    /** Cocoon's Grid setting: how many rows the board has (2–4). */
    // Compose state, so the board re-lays itself out the moment the setting changes.
    private val rowsState = androidx.compose.runtime.mutableIntStateOf(3)
    val rows: Int get() = rowsState.intValue

    private val nowPlayingState = androidx.compose.runtime.mutableStateOf(CocoonNowPlayingStyle.ARTWORK)
    /** What the other screen shows while a game runs. */
    val nowPlaying: CocoonNowPlayingStyle get() = nowPlayingState.value

    fun setNowPlaying(context: Context, style: CocoonNowPlayingStyle) {
        nowPlayingState.value = style
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("now_playing", style.name).apply()
    }

    /** Hero Settings: how the game art fills the top screen (FULL, VIGNETTE, NONE). */
    private val heroStyleState = androidx.compose.runtime.mutableStateOf("FULL")
    val heroStyle: String get() = heroStyleState.value
    fun setHeroStyle(context: Context, value: String) {
        heroStyleState.value = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("hero_style", value).apply()
    }

    /** Single-screen mode: how much of the screen the preview gets above the board. */
    private val heroShareState = androidx.compose.runtime.mutableFloatStateOf(0.45f)
    val heroShare: Float get() = heroShareState.floatValue
    fun setHeroShare(context: Context, value: Float) {
        heroShareState.floatValue = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat("hero_share", value).apply()
    }

    /** Icon Style: COLORED folders, ACCENT (all in the accent colour) or FLAT (grey). */
    private val iconStyleState = androidx.compose.runtime.mutableStateOf("COLORED")
    val iconStyle: String get() = iconStyleState.value
    fun setIconStyle(context: Context, value: String) {
        iconStyleState.value = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("icon_style", value).apply()
    }

    /** Animation Style: COMFORTABLE, SNAPPY or REDUCED (Argosy's motion tiers). */
    private val motionState = androidx.compose.runtime.mutableStateOf("COMFORTABLE")
    val motion: String get() = motionState.value
    fun setMotion(context: Context, value: String) {
        motionState.value = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("motion", value).apply()
    }

    private val dockBottomState = androidx.compose.runtime.mutableStateOf(false)
    private val dockBackgroundState = androidx.compose.runtime.mutableStateOf(false)
    private val dockAutoHideState = androidx.compose.runtime.mutableStateOf(false)

    /** Dock Style: under the board instead of above it. */
    val dockBottom: Boolean get() = dockBottomState.value
    /** Dock Style: a rounded bar behind the dock icons. */
    val dockBackground: Boolean get() = dockBackgroundState.value
    /** Dock Style: the dock only shows while the cursor is on it. */
    val dockAutoHide: Boolean get() = dockAutoHideState.value

    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        rowsState.intValue = p.getInt("rows", 3).coerceIn(2, 4)
        dockBottomState.value = p.getBoolean("dock_bottom", false)
        heroStyleState.value = p.getString("hero_style", "FULL") ?: "FULL"
        iconStyleState.value = p.getString("icon_style", "COLORED") ?: "COLORED"
        motionState.value = p.getString("motion", "COMFORTABLE") ?: "COMFORTABLE"
        heroShareState.floatValue = p.getFloat("hero_share", 0.45f)
        nowPlayingState.value = CocoonNowPlayingStyle.entries.firstOrNull { it.name == p.getString("now_playing", null) }
            ?: CocoonNowPlayingStyle.ARTWORK
        dockBackgroundState.value = p.getBoolean("dock_bg", false)
        dockAutoHideState.value = p.getBoolean("dock_autohide", false)
    }

    fun setDock(context: Context, bottom: Boolean = dockBottom, background: Boolean = dockBackground, autoHide: Boolean = dockAutoHide) {
        dockBottomState.value = bottom
        dockBackgroundState.value = background
        dockAutoHideState.value = autoHide
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("dock_bottom", bottom).putBoolean("dock_bg", background).putBoolean("dock_autohide", autoHide).apply()
    }

    fun setRows(context: Context, value: Int) {
        rowsState.intValue = value.coerceIn(2, 4)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("rows", rows).apply()
    }

    /** Appends a shortcut to Home (the board picks it up when it next shows). */
    fun addItem(context: Context, type: String, value: String?) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val array = runCatching { org.json.JSONArray(prefs.getString("home_items", "[]")) }.getOrDefault(org.json.JSONArray())
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            if (o.optString("type") == type && o.optString("value") == (value ?: "")) return
        }
        array.put(
            org.json.JSONObject()
                .put("id", java.util.UUID.randomUUID().toString().take(8))
                .put("type", type)
                .put("value", value ?: "")
        )
        prefs.edit().putString("home_items", array.toString()).apply()
    }
}


/** Personalization ▸ Animation Style as Argosy's motion tier. */
fun cocoonMotionTier(): com.nendo.argosy.ui.theme.MotionTier = when (CocoonHomeStore.motion) {
    "SNAPPY" -> com.nendo.argosy.ui.theme.MotionTier.Snappy
    "REDUCED" -> com.nendo.argosy.ui.theme.MotionTier.Reduced
    else -> com.nendo.argosy.ui.theme.MotionTier.Comfortable
}
