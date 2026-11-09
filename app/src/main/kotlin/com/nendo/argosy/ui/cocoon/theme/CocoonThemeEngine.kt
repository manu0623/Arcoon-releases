package com.nendo.argosy.ui.cocoon.theme

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Cocoon-compatible themes. A theme is a folder (or a ZIP of one) holding `theme.json`, an
 * optional `preview.png` and asset folders, exactly as Cocoon Shell lays them out:
 *
 *     theme.json, preview.png, wallpapers/, sounds/, music/, icon_overlays/, smart_folders/
 *
 * Imported themes live in the app's private storage under `cocoon_themes/<id>/`. This phase reads
 * the theme's name/credits, its colours and its two wallpapers (main = bottom screen,
 * external = top screen); the other folders are kept so later phases can use them.
 */
object CocoonThemeEngine {
    private const val PREFS = "cocoon_theme"
    private const val KEY_ACTIVE = "active"

    private var appContext: Context? = null

    /** The theme in use, or null for Arcoon's built-in look. Compose reads this directly. */
    val active = mutableStateOf<CocoonTheme?>(null)

    /** Every installed theme, for the picker. */
    val installed = mutableStateOf<List<CocoonTheme>>(emptyList())

    /** Asset packs (folder icons, icon overlays…) installed on their own, outside any theme. */
    val packs = mutableStateOf<List<CocoonAssetPack>>(emptyList())

    /** Settings ▸ Theme ▸ Themes opens the picker by setting this. */
    val pickerOpen = mutableStateOf(false)

    val colors: CocoonThemeColors? get() = active.value?.colors
    /** The theme's settings with the user's Personalization choices on top. */
    val settings: CocoonThemeSettings
        get() {
            val base = active.value?.settings ?: CocoonThemeSettings()
            val user = personal.value
            return base.copy(
                cursorStyle = user.cursorStyle ?: base.cursorStyle,
                highlightThickness = user.highlightThickness ?: base.highlightThickness,
                dockIconShape = user.dockIconShape ?: base.dockIconShape,
                glass = user.glass ?: base.glass,
            )
        }

    /** Settings ▸ Theme ▸ Personalization. Null means "as the theme says". */
    data class Personal(
        val cursorStyle: String? = null,
        val highlightThickness: Float? = null,
        val dockIconShape: String? = null,
        val glass: Boolean? = null,
    )

    val personal = mutableStateOf(Personal())
    val personalOpen = mutableStateOf(false)
    val jinglesOpen = mutableStateOf(false)

    fun setPersonal(value: Personal) {
        personal.value = value
        prefs()?.edit()
            ?.putString("p_cursor", value.cursorStyle)
            ?.putFloat("p_highlight", value.highlightThickness ?: -1f)
            ?.putString("p_dock", value.dockIconShape)
            ?.putInt("p_glass", when (value.glass) { null -> -1; true -> 1; false -> 0 })
            ?.apply()
    }

    private fun loadPersonal() {
        val p = prefs() ?: return
        personal.value = Personal(
            cursorStyle = p.getString("p_cursor", null),
            highlightThickness = p.getFloat("p_highlight", -1f).takeIf { it > 0f },
            dockIconShape = p.getString("p_dock", null),
            glass = when (p.getInt("p_glass", -1)) { 1 -> true; 0 -> false; else -> null },
        )
    }

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<(CocoonTheme?) -> Unit>()

    /** Sound and music players follow the theme through this; called once right away. */
    fun addListener(listener: (CocoonTheme?) -> Unit) {
        listeners += listener
        if (appContext != null) runCatching { listener(active.value) }
    }

    private fun notifyListeners() {
        val theme = active.value
        listeners.forEach { runCatching { it(theme) } }
    }

    /**
     * Answers of [folderArt], remembered: finding a file means listing folders on storage, which
     * is slow enough to stall a frame when the top screen asks for a folder's logo as it shows it.
     * Forgotten whenever the theme or the enabled packs change.
     */
    private val folderArtCache = java.util.concurrent.ConcurrentHashMap<String, java.util.Optional<File>>()
    @Volatile private var folderArtRoots: List<File> = emptyList()

    /** Folder art from asset packs or the theme: [part] is icon, hero or logo. */
    fun folderArt(glyph: com.nendo.argosy.ui.cocoon.CocoonGlyph, platformSlug: String?, part: String): File? {
        val roots = artRoots()
        if (roots != folderArtRoots) {
            folderArtCache.clear()
            com.nendo.argosy.ui.cocoon.CocoonFolderIconCache.clear()
            folderArtRoots = roots
        }
        val key = "${glyph.name}|${platformSlug.orEmpty()}|${part.lowercase()}"
        folderArtCache[key]?.let { return it.orElse(null) }
        val found = findFolderArt(roots, glyph, platformSlug, part)
        folderArtCache[key] = java.util.Optional.ofNullable(found)
        return found
    }

    private fun findFolderArt(
        roots: List<File>,
        glyph: com.nendo.argosy.ui.cocoon.CocoonGlyph,
        platformSlug: String?,
        part: String,
    ): File? {
        for (root in roots) {
            val folders = File(root, "smart_folders")
            if (!folders.isDirectory) continue
            val dir = when (glyph) {
                com.nendo.argosy.ui.cocoon.CocoonGlyph.PLATFORM ->
                    CocoonPlatformIds.candidates(platformSlug).firstNotNullOfOrNull { id -> childDir(File(folders, "by_platform"), id) }
                com.nendo.argosy.ui.cocoon.CocoonGlyph.APPS -> childDir(folders, "android_games")
                com.nendo.argosy.ui.cocoon.CocoonGlyph.EMULATORS -> childDir(folders, "emulators")
                com.nendo.argosy.ui.cocoon.CocoonGlyph.SMART ->
                    com.nendo.argosy.ui.cocoon.CocoonSmartKind.entries.firstOrNull { it.name == platformSlug }?.let { childDir(folders, it.themeType) }
                else -> null
            } ?: continue
            dir.listFiles().orEmpty().toList()
                .firstOrNull { it.isFile && it.nameWithoutExtension.equals(part, ignoreCase = true) }
                ?.let { return it }
        }
        return null
    }

    /** The mask and overlay PNGs for a platform's covers, from a pack or the theme. */
    fun iconOverlay(platformSlug: String?): Pair<File?, File?>? {
        for (root in artRoots()) {
            val overlays = File(root, "icon_overlays")
            if (!overlays.isDirectory) continue
            val dir = CocoonPlatformIds.candidates(platformSlug).firstNotNullOfOrNull { childDir(overlays, it) } ?: continue
            val mask = File(dir, "mask.png").takeIf { it.isFile }
            val overlay = File(dir, "overlay.png").takeIf { it.isFile }
            if (mask != null || overlay != null) return mask to overlay
        }
        return null
    }

    private fun childDir(parent: File, name: String): File? =
        parent.listFiles().orEmpty().toList().firstOrNull { it.isDirectory && it.name.equals(name, ignoreCase = true) }

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        runCatching { CocoonSampleTheme.ensureInstalled(root(context)) }
        runCatching { installBundledPacks(context) }
        loadPersonal()
        refresh()
        val id = prefs()?.getString(KEY_ACTIVE, null)
        active.value = installed.value.firstOrNull { it.id == id }
        notifyListeners()
    }

    private fun prefs() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun root(context: Context = appContext!!): File = File(context.filesDir, "cocoon_themes").apply { mkdirs() }

    fun packsRoot(context: Context = appContext!!): File = File(context.filesDir, "cocoon_packs").apply { mkdirs() }

    /** Packs shipped inside the app's assets/cocoon_packs (debug builds) are installed once. */
    private fun installBundledPacks(context: Context) {
        val assets = context.assets
        val names = assets.list("cocoon_packs").orEmpty()
        names.forEach { name ->
            val target = File(packsRoot(context), name)
            if (target.isDirectory) return@forEach
            fun copy(assetPath: String, out: File) {
                val children = assets.list(assetPath).orEmpty()
                if (children.isEmpty()) {
                    out.parentFile?.mkdirs()
                    assets.open(assetPath).use { input -> out.outputStream().use { input.copyTo(it) } }
                } else {
                    out.mkdirs()
                    children.forEach { copy("$assetPath/$it", File(out, it)) }
                }
            }
            copy("cocoon_packs/$name", target)
            setPackEnabled(name, true)
        }
    }

    fun setPackEnabled(id: String, enabled: Boolean) {
        val set = prefs()?.getStringSet("packs_enabled", emptySet()).orEmpty().toMutableSet()
        if (enabled) set += id else set -= id
        prefs()?.edit()?.putStringSet("packs_enabled", set)?.apply()
        packs.value = packs.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
        notifyListeners()
    }

    fun deletePack(pack: CocoonAssetPack) {
        setPackEnabled(pack.id, false)
        pack.dir.deleteRecursively()
        refresh()
    }

    /** Folders that can hold art: enabled packs first (they win), then the active theme. */
    private fun artRoots(): List<File> =
        packs.value.filter { it.enabled }.map { it.dir } + listOfNotNull(active.value?.dir)

    fun refresh() {
        folderArtCache.clear()
        com.nendo.argosy.ui.cocoon.CocoonFolderIconCache.clear()
        val enabled = prefs()?.getStringSet("packs_enabled", emptySet()).orEmpty()
        packs.value = appContext?.let { packsRoot(it) }?.listFiles().orEmpty().toList()
            .filter { it.isDirectory }
            .map { CocoonAssetPack.load(it, it.name in enabled) }
            .sortedBy { it.name.lowercase() }
        val dir = appContext?.let { root(it) } ?: return
        installed.value = dir.listFiles().orEmpty().toList()
            .filter { it.isDirectory && File(it, "theme.json").isFile }
            .mapNotNull { runCatching { CocoonTheme.load(it) }.getOrNull() }
            .sortedWith(compareBy({ !it.isSample }, { it.name.lowercase() }))
        active.value?.let { current -> active.value = installed.value.firstOrNull { it.id == current.id } }
    }

    fun apply(theme: CocoonTheme?) {
        active.value = theme
        prefs()?.edit()?.putString(KEY_ACTIVE, theme?.id)?.apply()
        notifyListeners()
    }

    fun delete(theme: CocoonTheme) {
        if (active.value?.id == theme.id) apply(null)
        theme.dir.deleteRecursively()
        refresh()
    }

    /** Unpacks a Cocoon theme ZIP and returns the installed theme. */
    fun importZip(path: String): Result<CocoonTheme> = runCatching {
        val context = appContext ?: error("theme engine not ready")
        val staging = File(context.cacheDir, "theme_import").apply { deleteRecursively(); mkdirs() }
        try {
            ZipInputStream(File(path).inputStream().buffered()).use { zip ->
                val base = staging.canonicalPath + File.separator
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val out = File(staging, entry.name)
                    // never write outside the staging folder
                    if (!out.canonicalPath.startsWith(base)) continue
                    if (entry.isDirectory) out.mkdirs() else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { zip.copyTo(it) }
                    }
                }
            }
            // theme.json may sit at the top of the ZIP or inside one folder
            val themeJson = staging.walkTopDown().maxDepth(3).firstOrNull { it.isFile && it.name == "theme.json" }
            if (themeJson == null) {
                // Not a theme: an asset pack (metadata.json and/or smart_folders / icon_overlays).
                val packRoot = staging.walkTopDown().maxDepth(3).firstOrNull { dir ->
                    dir.isDirectory && (File(dir, "metadata.json").isFile ||
                        File(dir, "smart_folders").isDirectory || File(dir, "icon_overlays").isDirectory)
                } ?: error("This ZIP has no theme.json or asset pack")
                val meta = File(packRoot, "metadata.json").takeIf { it.isFile }?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }
                val id = slug(meta?.optString("id")?.ifBlank { null } ?: meta?.optString("name")?.ifBlank { null } ?: File(path).nameWithoutExtension)
                val target = File(packsRoot(context), id)
                target.deleteRecursively()
                packRoot.copyRecursively(target, overwrite = true)
                setPackEnabled(id, true)
                refresh()
                error("PACK:" + (meta?.optString("name")?.ifBlank { null } ?: id))
            }
            val source = themeJson.parentFile!!
            val name = JSONObject(themeJson.readText()).optString("name").ifBlank { File(path).nameWithoutExtension }
            val id = slug(name)
            val target = File(root(context), id)
            target.deleteRecursively()
            source.copyRecursively(target, overwrite = true)
            refresh()
            installed.value.first { it.id == id }
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun slug(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "theme" }
}

data class CocoonTheme(
    val id: String,
    val dir: File,
    val name: String,
    val author: String?,
    val version: String?,
    val description: String?,
    val credits: String?,
    val mode: String?,
    val colors: CocoonThemeColors,
    val wallpaperMain: File?,
    val wallpaperExternal: File?,
    val preview: File?,
    val settings: CocoonThemeSettings = CocoonThemeSettings(),
    /** sounds/<name>.<ext> by Cocoon's sound name (navigate, select, back, …). */
    val sounds: Map<String, File> = emptyMap(),
    val music: CocoonThemeMusic? = null,
) {
    val isSample: Boolean get() = id == CocoonSampleTheme.ID

    companion object {
        fun load(dir: File): CocoonTheme {
            val json = JSONObject(File(dir, "theme.json").readText())
            fun text(key: String) = json.optString(key).takeIf { it.isNotBlank() }
            return CocoonTheme(
                id = dir.name,
                dir = dir,
                name = text("name") ?: dir.name,
                author = text("author"),
                version = text("version"),
                description = text("description"),
                credits = text("credits"),
                mode = text("theme_mode")?.uppercase(),
                colors = CocoonThemeColors.from(json.optJSONObject("color_scheme")),
                wallpaperMain = findWallpaper(dir, text("wallpaper_main"), listOf("main", "bottom")),
                wallpaperExternal = findWallpaper(dir, text("wallpaper_external"), listOf("external", "top")),
                preview = File(dir, "preview.png").takeIf { it.isFile },
                settings = CocoonThemeSettings.from(json.optJSONObject("settings")),
                sounds = File(dir, "sounds").listFiles().orEmpty().toList()
                    .filter { it.isFile }
                    .associateBy { it.nameWithoutExtension.lowercase() },
                music = CocoonThemeMusic.from(dir, json),
            )
        }

        /** Cocoon's lookup: the named file (extension optional), else wallpapers/main|bottom or external|top. */
        private fun findWallpaper(dir: File, ref: String?, defaults: List<String>): File? {
            val folder = File(dir, "wallpapers")
            if (ref != null) {
                listOf(File(dir, ref), File(folder, ref)).firstOrNull { it.isFile }?.let { return it }
                val stem = File(ref).nameWithoutExtension
                (folder.listFiles().orEmpty().toList() + dir.listFiles().orEmpty().toList())
                    .firstOrNull { it.isFile && it.nameWithoutExtension.equals(stem, ignoreCase = true) }
                    ?.let { return it }
            }
            return folder.listFiles().orEmpty().toList()
                .filter { it.isFile }
                .firstOrNull { file -> defaults.any { file.nameWithoutExtension.equals(it, ignoreCase = true) } }
        }
    }
}

/** The part of Cocoon's `color_scheme` the launcher draws with. Every key is optional. */
data class CocoonThemeColors(
    val backgroundStart: Color? = null,
    val backgroundEnd: Color? = null,
    val cardStart: Color? = null,
    val cardEnd: Color? = null,
    val textPrimary: Color? = null,
    val textSecondary: Color? = null,
    val iconTint: Color? = null,
    val tileBackground: Color? = null,
    val tileBorder: Color? = null,
    val accentStart: Color? = null,
    val accentEnd: Color? = null,
    val accentGlow: Color? = null,
    val selectionStart: Color? = null,
    val selectionEnd: Color? = null,
    val divider: Color? = null,
) {
    /** The cursor colour: selection, else the accent, as Cocoon resolves it. */
    val cursor: Color? get() = selectionStart ?: accentStart
    val accent: Color? get() = accentStart ?: selectionStart

    companion object {
        fun from(json: JSONObject?): CocoonThemeColors {
            if (json == null) return CocoonThemeColors()
            fun c(key: String): Color? = json.optString(key).takeIf { it.isNotBlank() }?.let {
                runCatching { Color(android.graphics.Color.parseColor(it.trim())) }.getOrNull()
            }
            return CocoonThemeColors(
                backgroundStart = c("background_gradient_start"),
                backgroundEnd = c("background_gradient_end"),
                cardStart = c("card_gradient_start"),
                cardEnd = c("card_gradient_end"),
                textPrimary = c("text_primary"),
                textSecondary = c("text_secondary"),
                iconTint = c("icon_tint"),
                tileBackground = c("tile_background"),
                tileBorder = c("tile_border"),
                accentStart = c("accent_gradient_start"),
                accentEnd = c("accent_gradient_end"),
                accentGlow = c("accent_glow"),
                selectionStart = c("selection_gradient_start"),
                selectionEnd = c("selection_gradient_end"),
                divider = c("divider"),
            )
        }
    }
}


/** The theme's `settings` block (only the parts the launcher draws with). */
data class CocoonThemeSettings(
    /** ANIMATED, OUTLINED, ALTERNATIVE_ANIMATED or ALTERNATIVE. */
    val cursorStyle: String = "ANIMATED",
    /** 0.5–1.0: how thick the selection frame is. */
    val highlightThickness: Float = 1f,
    /** CIRCLE, SQUIRCLE, ROUNDED_SQUARE or SQUARE. */
    val dockIconShape: String? = null,
    val hideDockBackground: Boolean = false,
    /** 0.0–0.5 of the icon size. */
    val iconRoundness: Float? = null,
    /** SOLID or GLASS: Glass makes menus and panels see-through over the blurred screen. */
    val glass: Boolean = false,
) {
    val cursorAnimated: Boolean get() = cursorStyle.contains("ANIMATED")
    val cursorOutlined: Boolean get() = cursorStyle.startsWith("OUTLINED") || cursorStyle == "ALTERNATIVE"

    companion object {
        fun from(json: JSONObject?): CocoonThemeSettings {
            if (json == null) return CocoonThemeSettings()
            return CocoonThemeSettings(
                cursorStyle = json.optString("cursor_style").uppercase().ifBlank { "ANIMATED" },
                highlightThickness = json.optDouble("highlight_thickness", 1.0).toFloat().coerceIn(0.5f, 1f),
                dockIconShape = json.optString("dock_icon_shape").uppercase().ifBlank { null },
                hideDockBackground = json.optBoolean("hide_dock_background", false),
                iconRoundness = json.optDouble("icon_roundness", Double.NaN).takeIf { !it.isNaN() }?.toFloat()?.coerceIn(0f, 0.5f),
                glass = json.optString("surface_material").uppercase() == "GLASS",
            )
        }
    }
}

/** The theme's music: a playlist, or tracks scheduled by time of day. */
data class CocoonThemeMusic(
    val tracks: List<File>,
    val shuffle: Boolean,
) {
    companion object {
        fun from(dir: File, json: JSONObject): CocoonThemeMusic? {
            val mode = json.optString("music_mode").uppercase()
            if (mode == "DISABLED") return null
            fun file(ref: String): File? =
                listOf(File(dir, ref.trim()), File(dir, "music/" + ref.trim())).firstOrNull { it.isFile }
            val shuffle = json.optString("music_playback_mode").uppercase() == "SHUFFLE"
            if (mode == "TIME") {
                // "hour:minute|file" entries joined by "||": play the one whose time came last.
                val now = java.util.Calendar.getInstance().let { it.get(java.util.Calendar.HOUR_OF_DAY) * 60 + it.get(java.util.Calendar.MINUTE) }
                val slots = json.optString("music_time_schedule").split("||").mapNotNull { entry ->
                    val parts = entry.split("|", limit = 2)
                    if (parts.size != 2) return@mapNotNull null
                    val hm = parts[0].trim().split(":")
                    val minutes = (hm.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null) * 60 + (hm.getOrNull(1)?.toIntOrNull() ?: 0)
                    file(parts[1])?.let { minutes to it }
                }.sortedBy { it.first }
                if (slots.isEmpty()) return null
                val current = slots.lastOrNull { it.first <= now } ?: slots.last()
                return CocoonThemeMusic(listOf(current.second), shuffle = false)
            }
            val listed = json.optString("music_playlist").split("||").filter { it.isNotBlank() }.mapNotNull { file(it) }
            val tracks = listed.ifEmpty {
                File(dir, "music").listFiles().orEmpty().toList().filter { it.isFile }.sortedBy { it.name.lowercase() }
            }
            if (tracks.isEmpty()) return null
            return CocoonThemeMusic(tracks, shuffle)
        }
    }
}

/** Cocoon names platforms by short folder IDs (gba, snes, psx…); RomM slugs mostly match, a few don't. */
object CocoonPlatformIds {
    private val aliases = mapOf(
        // Arcoon's own platform slugs -> Cocoon folder IDs
        "vb" to listOf("virtualboy"),
        "dsi" to listOf("ndsi"),
        "vita" to listOf("psvita"),
        "sg1000" to listOf("sg-1000"),
        "scd" to listOf("segacd"),
        "32x" to listOf("sega32x"),
        "gg" to listOf("gamegear"),
        "jaguar" to listOf("atarijaguar"),
        "jaguarcd" to listOf("atarijaguarcd"),
        "lynx" to listOf("atarilynx"),
        "nomad" to listOf("genesis"),
        "naomi2" to listOf("naomi"),
        "n64dd" to listOf("n64"),
        "pc" to listOf("windows"),
        "win" to listOf("windows"),
        "genesis-slash-megadrive" to listOf("genesis", "megadrive", "md"),
        "genesis" to listOf("megadrive", "md"),
        "megadrive" to listOf("genesis", "md"),
        "sms" to listOf("mastersystem", "sms"),
        "master-system" to listOf("mastersystem", "sms"),
        "gamegear" to listOf("gamegear", "gg"),
        "game-gear" to listOf("gamegear", "gg"),
        "ngc" to listOf("gc", "gamecube", "ngc"),
        "gamecube" to listOf("gc", "ngc"),
        "psx" to listOf("psx", "ps1", "playstation"),
        "ps" to listOf("psx", "ps1"),
        "dc" to listOf("dreamcast", "dc"),
        "dreamcast" to listOf("dc"),
        "sega-cd" to listOf("segacd", "megacd"),
        "segacd" to listOf("megacd"),
        "sega-32x" to listOf("32x", "sega32x"),
        "turbografx16--1" to listOf("pcengine", "tg16", "turbografx16"),
        "tg16" to listOf("pcengine", "turbografx16"),
        "neo-geo-pocket-color" to listOf("ngpc"),
        "neo-geo-pocket" to listOf("ngp"),
        "atari2600" to listOf("atari2600", "a2600"),
        "nintendo-ds" to listOf("nds"),
        "3ds" to listOf("n3ds", "3ds"),
        "n3ds" to listOf("3ds"),
        "switch" to listOf("switch", "nsw"),
        "psvita" to listOf("psvita", "vita"),
        "arcade" to listOf("arcade", "mame", "fbneo"),
    )

    fun candidates(slug: String?): List<String> {
        if (slug.isNullOrBlank()) return emptyList()
        val s = slug.lowercase()
        return (listOf(s) + aliases[s].orEmpty() + s.replace("-", "")).distinct()
    }
}


/** A Cocoon asset pack: folder icons, icon overlays, sounds or music, used with any theme. */
data class CocoonAssetPack(
    val id: String,
    val dir: File,
    val name: String,
    val author: String?,
    val category: String?,
    val enabled: Boolean,
    val preview: File?,
) {
    companion object {
        fun load(dir: File, enabled: Boolean): CocoonAssetPack {
            val meta = File(dir, "metadata.json").takeIf { it.isFile }?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }
            // first icon found makes the preview
            val preview = File(dir, "smart_folders").walkTopDown().maxDepth(4)
                .firstOrNull { it.isFile && it.extension.lowercase() == "png" }
            return CocoonAssetPack(
                id = dir.name,
                dir = dir,
                name = meta?.optString("name")?.ifBlank { null } ?: dir.name,
                author = meta?.optString("author")?.ifBlank { null },
                category = meta?.optString("category")?.ifBlank { null },
                enabled = enabled,
                preview = preview,
            )
        }
    }
}
