package com.nendo.argosy.ui.cocoon

import android.content.Context
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Backup & Restore for everything Arcoon adds on top of Argosy: the Home board (order, folders,
 * shortcuts, widgets, looks), app edits, jingles, theme choice, Personalization and screenshot
 * favourites. Written as one JSON file in Download/Arcoon; Argosy's own settings backup is separate.
 */
object CocoonBackup {
    private val PREFS = listOf("cocoon_board", "cocoon_apps", "cocoon_jingles", "cocoon_theme", "cocoon_screenshots")

    fun file(): File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Arcoon/arcoon-home-backup.json"
    )

    fun backup(context: Context): Result<File> = runCatching {
        val root = JSONObject().put("version", 1).put("created", System.currentTimeMillis())
        PREFS.forEach { name ->
            val values = JSONObject()
            context.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (key, value) ->
                val entry = when (value) {
                    is Boolean -> JSONObject().put("t", "b").put("v", value)
                    is Int -> JSONObject().put("t", "i").put("v", value)
                    is Long -> JSONObject().put("t", "l").put("v", value)
                    is Float -> JSONObject().put("t", "f").put("v", value.toDouble())
                    is String -> JSONObject().put("t", "s").put("v", value)
                    is Set<*> -> JSONObject().put("t", "set").put("v", JSONArray(value.map { it.toString() }))
                    else -> null
                }
                if (entry != null) values.put(key, entry)
            }
            root.put(name, values)
        }
        val out = file()
        out.parentFile?.mkdirs()
        out.writeText(root.toString(2))
        out
    }

    /** Restores the backup; Arcoon should be restarted afterwards so everything reloads. */
    fun restore(context: Context): Result<Unit> = runCatching {
        val root = JSONObject(file().readText())
        PREFS.forEach { name ->
            val values = root.optJSONObject(name) ?: return@forEach
            val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            values.keys().forEach { key ->
                val entry = values.optJSONObject(key) ?: return@forEach
                when (entry.optString("t")) {
                    "b" -> editor.putBoolean(key, entry.optBoolean("v"))
                    "i" -> editor.putInt(key, entry.optInt("v"))
                    "l" -> editor.putLong(key, entry.optLong("v"))
                    "f" -> editor.putFloat(key, entry.optDouble("v").toFloat())
                    "s" -> editor.putString(key, entry.optString("v"))
                    "set" -> {
                        val array = entry.optJSONArray("v") ?: JSONArray()
                        editor.putStringSet(key, (0 until array.length()).map { array.optString(it) }.toSet())
                    }
                }
            }
            editor.commit()
        }
        CocoonHomeStore.load(context)
    }
}
