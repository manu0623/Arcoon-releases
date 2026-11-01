package com.nendo.argosy.ui.cocoon

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cocoon's per-app edits (Y ▸ Edit App): a custom name and icon, and "Treat as Game", which
 * times every session of that app launched from Home or the dock so it shows in Log and the
 * Playtime widget.
 */
object CocoonAppStore {
    private const val PREFS = "cocoon_apps"
    private var appContext: Context? = null

    /** Bumped on every change, so anything drawn from here redraws. */
    val version = mutableIntStateOf(0)

    private var running: Pair<String, Long>? = null

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    private fun prefs() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun bump() { version.intValue++ }

    fun name(pkg: String): String? = prefs()?.getString("name_$pkg", null)
    fun setName(pkg: String, value: String?) {
        prefs()?.edit()?.apply { if (value.isNullOrBlank()) remove("name_$pkg") else putString("name_$pkg", value.trim()) }?.apply()
        bump()
    }

    fun icon(pkg: String): String? = prefs()?.getString("icon_$pkg", null)?.takeIf { java.io.File(it).isFile }
    fun setIcon(pkg: String, path: String?) {
        prefs()?.edit()?.apply { if (path == null) remove("icon_$pkg") else putString("icon_$pkg", path) }?.apply()
        bump()
    }

    fun isGame(pkg: String): Boolean = prefs()?.getBoolean("game_$pkg", false) ?: false
    fun setGame(pkg: String, value: Boolean) {
        prefs()?.edit()?.putBoolean("game_$pkg", value)?.apply()
        bump()
    }

    /** Called when an app is launched from Home or the dock. */
    fun onLaunched(pkg: String) {
        running = if (isGame(pkg)) pkg to System.currentTimeMillis() else null
    }

    /** Called when the launcher comes back to the front: closes the running app's session. */
    fun onLauncherReturned() {
        val (pkg, start) = running ?: return
        running = null
        val minutes = (System.currentTimeMillis() - start) / 60_000
        if (minutes < 1) return
        val prefs = prefs() ?: return
        val sessions = runCatching { JSONArray(prefs.getString("sessions", "[]")) }.getOrDefault(JSONArray())
        sessions.put(JSONObject().put("pkg", pkg).put("start", start).put("minutes", minutes))
        // keep about 90 days
        val cutoff = System.currentTimeMillis() - 90L * 24 * 3600 * 1000
        val kept = JSONArray()
        for (i in 0 until sessions.length()) {
            val o = sessions.optJSONObject(i) ?: continue
            if (o.optLong("start") >= cutoff) kept.put(o)
        }
        prefs.edit().putString("sessions", kept.toString()).apply()
        bump()
    }

    data class AppSession(val pkg: String, val start: Long, val minutes: Long)

    fun sessionsSince(since: Long): List<AppSession> {
        val array = runCatching { JSONArray(prefs()?.getString("sessions", "[]") ?: "[]") }.getOrDefault(JSONArray())
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            AppSession(o.optString("pkg"), o.optLong("start"), o.optLong("minutes")).takeIf { it.start >= since }
        }
    }
}
