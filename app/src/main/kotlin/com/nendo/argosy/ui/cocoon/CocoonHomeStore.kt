package com.nendo.argosy.ui.cocoon

import android.content.Context

/**
 * Shared bits of the board's saved layout, usable from outside the board (a game's Y menu adds
 * itself to Home through here). The board reads the same "cocoon_board" preferences.
 */
object CocoonHomeStore {
    /** The Home tile the cursor was last on, so coming back from a folder lands on it again. */
    @Volatile var lastFocusKey: String? = null

    private const val PREFS = "cocoon_board"

    /** Whether this process has read the saved layout yet. */
    @Volatile private var loaded = false

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

    /** On Screen Home Menu: how big the in-game handle is (SMALL, MEDIUM, LARGE). */
    private val handleSizeState = androidx.compose.runtime.mutableStateOf("SMALL")
    val handleSize: String get() = handleSizeState.value

    /**
     * Home Symbol: where the handle sits, as a fraction of the area of the screen it is drawn on
     * (0 is that area's left/top edge, 1 its right/bottom). Free placement rather than a corner:
     * the bar a game's picture leaves free is usually not a corner, and a corner is often under
     * the thumb holding the console.
     */
    private val handleXState = androidx.compose.runtime.mutableFloatStateOf(0f)
    val handleX: Float get() = handleXState.floatValue
    private val handleYState = androidx.compose.runtime.mutableFloatStateOf(1f)
    val handleY: Float get() = handleYState.floatValue

    /** Home Symbol: Clarity, the handle's opacity. Faint by default, on purpose. */
    private val handleClarityState = androidx.compose.runtime.mutableFloatStateOf(0.18f)
    val handleClarity: Float get() = handleClarityState.floatValue

    fun setHandle(
        context: Context,
        size: String = handleSize,
        x: Float = handleX,
        y: Float = handleY,
        clarity: Float = handleClarity
    ) {
        handleSizeState.value = size
        handleXState.floatValue = x.coerceIn(0f, 1f)
        handleYState.floatValue = y.coerceIn(0f, 1f)
        handleClarityState.floatValue = clarity.coerceIn(0.05f, 0.6f)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("handle_size", size)
            .putFloat("handle_x", handleXState.floatValue)
            .putFloat("handle_y", handleYState.floatValue)
            .putFloat("handle_clarity", handleClarityState.floatValue)
            .apply()
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

    /**
     * Reads the saved layout if this process has not done it yet. A game screen starts without the
     * board being composed, so it has to be able to read the saved values itself instead of
     * inheriting whatever defaults the process happens to hold.
     */
    fun ensureLoaded(context: Context) {
        if (!loaded) load(context)
    }

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
        handleSizeState.value = p.getString("handle_size", "SMALL") ?: "SMALL"
        handleClarityState.floatValue = p.getFloat("handle_clarity", 0.18f)
        if (p.contains("handle_x") && p.contains("handle_y")) {
            handleXState.floatValue = p.getFloat("handle_x", 0f)
            handleYState.floatValue = p.getFloat("handle_y", 1f)
        } else {
            // Earlier builds stored one of five corners. A corner is a position too, so the handle
            // keeps the place the player chose instead of jumping to the default on the first run
            // of this build.
            val (x, y) = when (p.getString("handle_corner", null)) {
                "TOP_START" -> 0f to 0f
                "TOP_END" -> 1f to 0f
                "BOTTOM_CENTER" -> 0.5f to 1f
                "BOTTOM_END" -> 1f to 1f
                else -> 0f to 1f
            }
            handleXState.floatValue = x
            handleYState.floatValue = y
        }
        loaded = true
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


/** Personalization ▸ On Screen Home Menu: the handle's tap target, in dp. */
fun cocoonHandleTargetDp(): Int = when (CocoonHomeStore.handleSize) {
    "LARGE" -> 76
    "MEDIUM" -> 56
    else -> 40
}

/** Personalization ▸ On Screen Home Menu: the glyph inside that target, in dp. */
fun cocoonHandleIconDp(): Int = when (CocoonHomeStore.handleSize) {
    "LARGE" -> 52
    "MEDIUM" -> 38
    else -> 26
}

/** Personalization ▸ Animation Style as Argosy's motion tier. */
fun cocoonMotionTier(): com.nendo.argosy.ui.theme.MotionTier = when (CocoonHomeStore.motion) {
    "SNAPPY" -> com.nendo.argosy.ui.theme.MotionTier.Snappy
    "REDUCED" -> com.nendo.argosy.ui.theme.MotionTier.Reduced
    else -> com.nendo.argosy.ui.theme.MotionTier.Comfortable
}
