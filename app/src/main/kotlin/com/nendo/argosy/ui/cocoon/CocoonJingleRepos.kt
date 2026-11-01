package com.nendo.argosy.ui.cocoon

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cocoon's jingle repositories: public GitHub repos with an index.json listing sounds per
 * platform ({"name": …, then per platform a list of {name, file, regex?}}). Added by their
 * "owner/repository" name in Personalization; their sounds show up in a game's jingle picker.
 */
object CocoonJingleRepos {
    private const val PREFS = "cocoon_jingles"

    data class Entry(val repo: String, val platform: String, val name: String, val file: String, val regex: Regex?) {
        val url: String get() = "https://raw.githubusercontent.com/$repo/HEAD/" + file.trimStart('/')
    }

    private val indexCache = java.util.concurrent.ConcurrentHashMap<String, List<Entry>>()

    fun repos(context: Context): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet("repos", emptySet()).orEmpty().sorted()

    fun addRepo(context: Context, repo: String) {
        val clean = repo.trim().removePrefix("https://github.com/").trim('/')
        if (!clean.contains('/')) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit().putStringSet("repos", p.getStringSet("repos", emptySet()).orEmpty() + clean).apply()
        CocoonJingles.version.intValue++
    }

    fun removeRepo(context: Context, repo: String) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit().putStringSet("repos", p.getStringSet("repos", emptySet()).orEmpty() - repo).apply()
        indexCache.remove(repo)
        CocoonJingles.version.intValue++
    }

    private fun get(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = true
        try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun index(repo: String): List<Entry> = indexCache[repo] ?: withContext(Dispatchers.IO) {
        val json = JSONObject(String(get("https://raw.githubusercontent.com/$repo/HEAD/index.json")))
        // platforms either sit under "platforms" or as top-level lists
        val platforms = json.optJSONObject("platforms") ?: json
        val entries = mutableListOf<Entry>()
        platforms.keys().forEach { key ->
            val list = platforms.optJSONArray(key) ?: return@forEach
            for (i in 0 until list.length()) {
                val o = list.optJSONObject(i) ?: continue
                val file = o.optString("file")
                if (file.isBlank()) continue
                entries += Entry(
                    repo = repo,
                    platform = key.lowercase(),
                    name = o.optString("name").ifBlank { File(file).nameWithoutExtension },
                    file = file,
                    regex = o.optString("regex").takeIf { it.isNotBlank() }?.let { runCatching { Regex(it, RegexOption.IGNORE_CASE) }.getOrNull() },
                )
            }
        }
        indexCache[repo] = entries
        entries
    }

    /** Sounds for this game's platform from every added repo; ones whose regex matches the title first. */
    suspend fun entriesFor(context: Context, platformSlug: String?, title: String): Result<List<Entry>> = runCatching {
        val ids = com.nendo.argosy.ui.cocoon.theme.CocoonPlatformIds.candidates(platformSlug).toSet()
        repos(context).flatMap { repo -> runCatching { index(repo) }.getOrDefault(emptyList()) }
            .filter { it.platform in ids }
            .sortedWith(compareByDescending<Entry> { it.regex?.containsMatchIn(title) == true || it.name.equals(title, ignoreCase = true) }
                .thenBy { it.name.lowercase() })
    }

    /** Downloads a sound (once) and returns its local path. */
    suspend fun download(context: Context, entry: Entry): String = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "jingles/" + entry.repo.replace('/', '_')).apply { mkdirs() }
        val out = File(dir, entry.file.replace('/', '_'))
        if (!out.isFile || out.length() == 0L) out.writeBytes(get(entry.url))
        out.absolutePath
    }
}
