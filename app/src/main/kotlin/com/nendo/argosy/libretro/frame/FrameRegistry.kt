package com.nendo.argosy.libretro.frame

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.nendo.argosy.data.platform.PlatformDefinitions
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FrameRegistry @Inject constructor(@ApplicationContext private val context: Context) {

    enum class Source { LIBRETRO, DUIMON, CUSTOM }

    /**
     * Where the game sits on the frame, as fractions of the surface. Taken from the Mega Bezel
     * preset that ships the artwork, so it lands on the screen the artwork was drawn around.
     */
    data class ScreenRect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )

    /**
     * [screenRect] decides how the frame is composited. Null is a cutout bezel: opaque border,
     * transparent middle, drawn over a full-surface game. Non-null is a plate with no cutout,
     * drawn behind a game placed at that rect.
     */
    data class FrameEntry(
        val id: String,
        val displayName: String,
        val platforms: Set<String>,
        val githubPath: String,
        val source: Source = Source.LIBRETRO,
        val screenRect: ScreenRect? = null
    )

    private var installedCache: Set<String>? = null
    private var customCache: List<FrameEntry>? = null

    fun getInstalledIds(): Set<String> {
        installedCache?.let { return it }
        adoptLegacyFlatLayout()
        val ids = listOf(getFramesDir(), getCustomFramesDir())
            .filter { it.exists() }
            .flatMap { dir ->
                dir.listFiles()
                    ?.filter { it.extension.lowercase() == "png" }
                    ?.map { it.nameWithoutExtension }
                    ?: emptyList()
            }
            .toSet()
        installedCache = ids
        return ids
    }

    /**
     * Moves frames written before the catalog/custom split into the catalog directory. Without
     * it every already-downloaded frame reads as missing and downloads a second time.
     */
    private fun adoptLegacyFlatLayout() {
        val legacyDir = File(context.getExternalFilesDir(null), "frames")
        val stale = legacyDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() == "png" }
            ?: return
        if (stale.isEmpty()) return

        val target = getFramesDir().apply { mkdirs() }
        stale.forEach { file ->
            val moved = File(target, file.name)
            if (!moved.exists() && !file.renameTo(moved)) {
                file.copyTo(moved, overwrite = true)
                file.delete()
            }
        }
    }

    fun invalidateInstalledCache() {
        installedCache = null
        customCache = null
    }

    /**
     * The screen every Duimon CRT preset places the game on: 4:3 at 82.97% of surface height,
     * centred. Upstream picks 82.97 so a 224px core lands on an integer scale at 1080p and 2160p.
     */
    private val CRT_SCREEN = ScreenRect(0.189f, 0.085f, 0.811f, 0.915f)

    /**
     * Duimon entries lead, so a platform that has one takes it as its Auto default: Auto resolves
     * to the first entry matching the slug. The set covers every platform the built-in cores run
     * that Duimon ships a single 16:9 bezel for. Game Boy, Game Boy Color and Game Boy Advance are
     * absent because Duimon ships only device, glass and top layers for the Mega Bezel shader to
     * composite; Virtual Boy is absent because its bezel is a 21:9 two-eye layout.
     */
    private val catalogFrames = listOf(
        FrameEntry(
            "duimon_nes", "NES (Duimon)", setOf("nes"),
            "Nintendo_NES/NES.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_fds", "Famicom Disk System (Duimon)", setOf("fds"),
            "Nintendo_Famicom/Famicom.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_snes", "SNES (Duimon)", setOf("snes"),
            "Nintendo_SNES/SNES.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_n64", "Nintendo 64 (Duimon)", setOf("n64", "n64dd"),
            "Nintendo_N64/N64.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_gc", "GameCube (Duimon)", setOf("gc"),
            "Nintendo_Gamecube/Gamecube.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_wii", "Wii (Duimon)", setOf("wii"),
            "Nintendo_Wii/Wii.png", Source.DUIMON,
            ScreenRect(0.174f, 0.075f, 0.826f, 0.945f)
        ),
        FrameEntry(
            "duimon_pokemini", "Pokemon Mini (Duimon)", setOf("pokemini"),
            "Nintendo_Pokemon_Mini/Pokemon_Mini.png", Source.DUIMON,
            ScreenRect(0.328f, 0.362f, 0.672f, 0.820f)
        ),
        FrameEntry(
            "duimon_sg1000", "SG-1000 (Duimon)", setOf("sg1000"),
            "SEGA_SG-1000/SG-1000.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_mastersystem", "Master System (Duimon)", setOf("sms"),
            "SEGA_Master_System/SMS.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_genesis", "Sega Genesis (Duimon)", setOf("genesis"),
            "SEGA_Genesis/Genesis.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_nomad", "Sega Nomad (Duimon)", setOf("nomad"),
            "SEGA_Nomad/Nomad.png", Source.DUIMON,
            ScreenRect(0.315f, 0.327f, 0.685f, 0.821f)
        ),
        FrameEntry(
            "duimon_scd", "Sega CD (Duimon)", setOf("scd"),
            "SEGA_CD/SEGACD.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_32x", "Sega 32X (Duimon)", setOf("32x"),
            "SEGA_32X/SEGA32X.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_gamegear", "Game Gear (Duimon)", setOf("gg"),
            "SEGA_Game_Gear/Game_Gear.png", Source.DUIMON,
            ScreenRect(0.282f, 0.252f, 0.718f, 0.834f)
        ),
        FrameEntry(
            "duimon_pico", "Sega Pico (Duimon)", setOf("pico"),
            "SEGA_Pico/SEGA_Pico.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_saturn", "Sega Saturn (Duimon)", setOf("saturn"),
            "SEGA_Saturn/Saturn.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_dreamcast", "Dreamcast (Duimon)", setOf("dreamcast"),
            "SEGA_Dreamcast/Dreamcast.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_psx", "PlayStation (Duimon)", setOf("psx"),
            "SONY_Playstation/Playstation.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_psp", "PlayStation Portable (Duimon)", setOf("psp"),
            "SONY_PSP/PSP.png", Source.DUIMON,
            ScreenRect(0.154f, 0.192f, 0.846f, 0.884f)
        ),
        FrameEntry(
            "duimon_tg16", "TurboGrafx-16 (Duimon)", setOf("tg16"),
            "NEC_TurboGrafx_16/TurboGrafx16.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_tgcd", "TurboGrafx-CD (Duimon)", setOf("tgcd"),
            "NEC_TurboGrafx_CD/TurboGrafx_CD.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_supergrafx", "SuperGrafx (Duimon)", setOf("supergrafx"),
            "NEC_SuperGrafx/SuperGrafx.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_pcfx", "PC-FX (Duimon)", setOf("pcfx"),
            "NEC_PC-FX/PC-FX.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_pc9800", "PC-9800 (Duimon)", setOf("pc9800"),
            "NEC_PC-9801/PC-9801.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_neogeo", "Neo Geo (Duimon)", setOf("neogeo"),
            "Neo_Geo_AES/NeoGeo_AES.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_neogeocd", "Neo Geo CD (Duimon)", setOf("neogeocd"),
            "Neo_Geo_CD/NeoGeo_CD.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_ngp", "Neo Geo Pocket (Duimon)", setOf("ngp"),
            "Neo_Geo_Pocket/NGP.png", Source.DUIMON,
            ScreenRect(0.315f, 0.261f, 0.685f, 0.889f)
        ),
        FrameEntry(
            "duimon_ngpc", "Neo Geo Pocket Color (Duimon)", setOf("ngpc"),
            "Neo_Geo_Pocket_Color/NGPC.png", Source.DUIMON,
            ScreenRect(0.318f, 0.238f, 0.682f, 0.854f)
        ),
        FrameEntry(
            "duimon_atari2600", "Atari 2600 (Duimon)", setOf("atari2600"),
            "Atari_2600/2600.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_atari5200", "Atari 5200 (Duimon)", setOf("atari5200"),
            "Atari_5200/5200.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_atari7800", "Atari 7800 (Duimon)", setOf("atari7800"),
            "Atari_7800/7800.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_jaguar", "Atari Jaguar (Duimon)", setOf("jaguar"),
            "Atari_Jaguar/Jaguar.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_lynx", "Atari Lynx (Duimon)", setOf("lynx"),
            "Atari_Lynx/Lynx_Alt.png", Source.DUIMON,
            ScreenRect(0.304f, 0.276f, 0.696f, 0.724f)
        ),
        FrameEntry(
            "duimon_wonderswan", "WonderSwan (Duimon)", setOf("wonderswan"),
            "Bandai_WonderSwan/WonderSwan.png", Source.DUIMON,
            ScreenRect(0.354f, 0.308f, 0.800f, 0.820f)
        ),
        FrameEntry(
            "duimon_wscolor", "WonderSwan Color (Duimon)", setOf("wsc"),
            "Bandai_WonderSwan_Color/WonderSwan_Color.png", Source.DUIMON,
            ScreenRect(0.354f, 0.308f, 0.800f, 0.820f)
        ),
        FrameEntry(
            "duimon_3do", "3DO (Duimon)", setOf("3do"),
            "Panasonic_3DO/3DO.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_cdi", "CD-i (Duimon)", setOf("cdi"),
            "Philips_CD-i/CD-i.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_channelf", "Channel F (Duimon)", setOf("channelf"),
            "Fairchild_Channel_F/ChannelF.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_coleco", "ColecoVision (Duimon)", setOf("coleco"),
            "ColecoVision/ColecoVision.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_intellivision", "Intellivision (Duimon)", setOf("intellivision"),
            "Mattel_Intellivision/Intellivision.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_odyssey2", "Odyssey 2 (Duimon)", setOf("odyssey2"),
            "Magnavox_Odyssey_2/Odyssey2.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_vectrex", "Vectrex (Duimon)", setOf("vectrex"),
            "GCE_Vectrex/Vectrex.png", Source.DUIMON,
            ScreenRect(0.285f, 0.277f, 0.715f, 0.895f)
        ),
        FrameEntry(
            "duimon_arcade", "Arcade Cabinet (Duimon)",
            setOf("arcade", "cps1", "cps2", "cps3", "naomi", "atomiswave"),
            "Arcade/Arcade_Horizontal.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_fbneo", "FB Neo (Duimon)", setOf("fbneo"),
            "FB-Neo/FB-Neo_Horizontal.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_mame", "MAME (Duimon)", setOf("mame"),
            "MAME/MAME_Horizontal.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_amiga", "Amiga (Duimon)", setOf("amiga", "amigacd32", "cdtv"),
            "Amiga_A500_Plus/A500.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_amstradcpc", "Amstrad CPC (Duimon)", setOf("amstradcpc"),
            "Amstrad_CPC/CPC464.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_c64", "Commodore 64 (Duimon)", setOf("c64"),
            "Commodore_64C/64C.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_dos", "DOS (Duimon)", setOf("dos"),
            "DOSBox/DOSBox.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_msx", "MSX (Duimon)", setOf("msx"),
            "MSX/MSX.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_msx2", "MSX2 (Duimon)", setOf("msx2"),
            "MSX2/MSX2.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_zx", "ZX Spectrum (Duimon)", setOf("zx"),
            "Sinclair_ZX_Spectrum/ZX_Spectrum.png", Source.DUIMON, CRT_SCREEN
        ),
        FrameEntry(
            "duimon_pico8", "PICO-8 (Duimon)", setOf("pico8"),
            "PICO-8/PICO-8.png", Source.DUIMON,
            ScreenRect(0.339f, 0.279f, 0.661f, 0.851f)
        ),
        FrameEntry(
            "nes", "NES", setOf("nes"),
            "16x9%20Collections/Nosh01%201440%20Plain/Nintendo-Entertainment-System-Bezel-16x9-2560x1440.png"
        ),
        FrameEntry(
            "snes", "SNES", setOf("snes"),
            "16x9%20Collections/Nosh01%201440%20Plain/Super-Nintendo-Entertainment-System-Bezel-16x9-2560x1440.png"
        ),
        FrameEntry(
            "gb", "Game Boy", setOf("gb"),
            "16x9%20Collections/Nosh01%201440%20Plain/Nintendo-Game-Boy-Bezel-16x9-2560x1440.png"
        ),
        FrameEntry(
            "gbc", "Game Boy Color", setOf("gbc"),
            "16x9%20Collections/Nosh01%201440%20Plain/Nintendo-Game-Boy-Color-Bezel-16x9-2560x1440.png"
        ),
        FrameEntry(
            "gba", "Game Boy Advance", setOf("gba"),
            "16x9%20Collections/Nosh01%201440%20Plain/Nintendo-Game-Boy-Advance-Bezel-16x9-2560x1440.png"
        ),
        FrameEntry(
            "n64", "Nintendo 64", setOf("n64"),
            "16x9%20Collections/Nosh01%201440%20Plain/Nintendo-64-Bezel-16x9-2560x1440.png"
        ),
        FrameEntry(
            "genesis", "Sega Genesis", setOf("genesis"),
            "16x9%20Collections/Nosh01%201440%20Plain/Sega-Genesis-Bezel-16x9-2560x1440.png"
        ),
        FrameEntry(
            "mastersystem", "Master System", setOf("sms"),
            "16x9%20Collections/Nosh01%201440%20Plain/Sega-Master-System-Bezel-16x9-2560x1440.png"
        ),
        FrameEntry(
            "gamegear", "Game Gear", setOf("gg"),
            "16x9%20Collections/Nosh01%201440%20Plain/Sega-Game-Gear-Bezel-16x9-2560x1440.png"
        ),
        FrameEntry(
            "saturn", "Sega Saturn", setOf("saturn"),
            "16x9%20Collections/Nosh01%201440%20Plain/Sega-Saturn-Bezel-16x9-2560x1440.png"
        ),
        FrameEntry(
            "dreamcast", "Dreamcast", setOf("dreamcast"),
            "16x9%20Collections/NyNy77%201080%20Bezel/SegaDreamcast-nyny77.png"
        ),
        FrameEntry(
            "psx", "PlayStation", setOf("psx"),
            "16x9%20Collections/Nosh01%201440%20Plain/Sony-Playstation-Bezel-16x9-2560x1440.png"
        ),
        FrameEntry(
            "psp", "PlayStation Portable", setOf("psp"),
            "16x9%20Collections/Nosh01%201440%20Plain/Sony-Playstation-Portable-Bezel-16x9-2560x1440.png"
        ),
        FrameEntry(
            "tg16", "TurboGrafx-16", setOf("tg16"),
            "16x9%20Collections/Nosh01%201440%20Plain/NEC-TurboGrafx-16-Bezel-16x9-2560x1440.png"
        ),
        FrameEntry(
            "ngp", "Neo Geo Pocket", setOf("ngp", "ngpc"),
            "16x9%20Collections/Nosh01%201440%20Plain/SNK-Neo-Geo-Pocket-Bezel-16x9-2560x1440.png"
        ),
        FrameEntry(
            "atari2600", "Atari 2600", setOf("atari2600"),
            "16x9%20Collections/Nosh01%201440%20Plain/Atari-2600-Bezel-16x9-2560x1440.png"
        ),
        FrameEntry(
            "lynx", "Atari Lynx", setOf("lynx"),
            "16x9%20Collections/Nosh01%201440%20Plain/Atari-Lynx-Horizontal-Bezel-16x9-2560x1440.png"
        ),
        FrameEntry(
            "wonderswan", "WonderSwan", setOf("wonderswan"),
            "16x9%20Collections/Nosh01%201440%20Plain/Bandai-WonderSwan-Horizontal-Bezel-16x9-2560x1440.png"
        ),
        FrameEntry(
            "wscolor", "WonderSwan Color", setOf("wsc"),
            "16x9%20Collections/Nosh01%201440%20Plain/Bandai-WonderSwan-Color-Horizontal-Bezel-16x9-2560x1440.png"
        ),
    )

    fun getCatalogFrames(): List<FrameEntry> = catalogFrames

    fun getFramesForPlatform(platformSlug: String): List<FrameEntry> {
        val canonical = PlatformDefinitions.getCanonicalSlug(platformSlug)
        return catalogFrames.filter { canonical in it.platforms }
    }

    fun getAllFrames(): List<FrameEntry> = catalogFrames + getCustomFrames()

    /**
     * Imported frames are read back off disk rather than tracked in a preference, so the file is
     * the only record: copying one in or deleting one outside the app stays consistent.
     */
    fun getCustomFrames(): List<FrameEntry> {
        customCache?.let { return it }
        val entries = getCustomFramesDir().listFiles()
            ?.filter { it.isFile && it.extension.lowercase() == "png" }
            ?.sortedBy { it.name.lowercase() }
            ?.map {
                val id = it.nameWithoutExtension
                FrameEntry(id, id.removePrefix(CUSTOM_ID_PREFIX), emptySet(), "", Source.CUSTOM)
            }
            ?: emptyList()
        customCache = entries
        return entries
    }

    fun importCustomFrame(sourcePath: String): Result<FrameEntry> = runCatching {
        val source = File(sourcePath)
        require(source.isFile) { "Frame source is not a file: $sourcePath" }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) {
            "Frame source is not a readable image: $sourcePath"
        }

        val dir = getCustomFramesDir().apply { mkdirs() }
        val id = allocateCustomId(source.nameWithoutExtension, dir)
        val target = File(dir, "$id.png")

        if (source.extension.equals("png", ignoreCase = true)) {
            source.copyTo(target, overwrite = true)
        } else {
            val decoded = BitmapFactory.decodeFile(source.absolutePath)
                ?: error("Frame source could not be decoded: $sourcePath")
            try {
                target.outputStream().use { decoded.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally {
                decoded.recycle()
            }
        }

        invalidateInstalledCache()
        FrameEntry(id, id.removePrefix(CUSTOM_ID_PREFIX), emptySet(), "", Source.CUSTOM)
    }

    fun deleteCustomFrame(id: String): Boolean {
        if (!id.startsWith(CUSTOM_ID_PREFIX)) return false
        val deleted = File(getCustomFramesDir(), "$id.png").delete()
        if (deleted) invalidateInstalledCache()
        return deleted
    }

    private fun allocateCustomId(sourceName: String, dir: File): String {
        val base = sourceName
            .map { if (it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_') it else '_' }
            .joinToString("")
            .trim()
            .take(48)
            .ifBlank { "bezel" }
        var candidate = "$CUSTOM_ID_PREFIX$base"
        var suffix = 2
        while (File(dir, "$candidate.png").exists()) {
            candidate = "$CUSTOM_ID_PREFIX$base $suffix"
            suffix++
        }
        return candidate
    }

    fun findById(id: String): FrameEntry? =
        catalogFrames.find { it.id == id } ?: getCustomFrames().find { it.id == id }

    fun isInstalled(entry: FrameEntry): Boolean =
        entry.id in getInstalledIds()

    fun isInstalled(id: String): Boolean =
        id in getInstalledIds()

    fun getInstalledFramesForPlatform(platformSlug: String): List<FrameEntry> =
        getFramesForPlatform(platformSlug).filter { isInstalled(it) }

    /**
     * [maxWidth] and [maxHeight] are the surface the frame is drawn onto. Source art runs to
     * 4K, which decodes to roughly 33MB of ARGB before it reaches GL; sampling halves both axes
     * together, so the aspect ratio is preserved by construction.
     */
    fun loadFrame(id: String, maxWidth: Int = 0, maxHeight: Int = 0): Bitmap? {
        val file = listOf(getFramesDir(), getCustomFramesDir())
            .map { File(it, "$id.png") }
            .firstOrNull { it.exists() }
            ?: return null

        val metrics = context.resources.displayMetrics
        val targetWidth = if (maxWidth > 0) maxWidth else metrics.widthPixels
        val targetHeight = if (maxHeight > 0) maxHeight else metrics.heightPixels
        if (targetWidth <= 0 || targetHeight <= 0) {
            return BitmapFactory.decodeFile(file.absolutePath)
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidth &&
            bounds.outHeight / (sample * 2) >= targetHeight
        ) {
            sample *= 2
        }

        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    }

    fun loadFrame(entry: FrameEntry): Bitmap? = loadFrame(entry.id)

    fun getFramesDir(): File =
        File(context.getExternalFilesDir(null), "frames/catalog")

    /**
     * User-imported frames, kept apart from the catalog so [clearDownloadedFrames] cannot take
     * them: a catalog frame re-downloads on demand and an imported one is gone for good.
     */
    fun getCustomFramesDir(): File =
        File(context.getExternalFilesDir(null), "frames/custom")

    fun installedFileFor(entry: FrameEntry): File = when (entry.source) {
        Source.CUSTOM -> File(getCustomFramesDir(), "${entry.id}.png")
        else -> File(getFramesDir(), "${entry.id}.png")
    }

    fun ensureDirectoryExists() {
        getFramesDir().mkdirs()
        getCustomFramesDir().mkdirs()
    }

    /**
     * Clears catalog frames only. The row offering this says they re-download on demand, which
     * is true of a catalog frame and false of one the user imported.
     */
    fun clearDownloadedFrames() {
        val dir = getFramesDir()
        if (dir.exists()) dir.deleteRecursively()
        installedCache = null
    }

    companion object {
        private const val TAG = "FrameRegistry"

        const val CUSTOM_ID_PREFIX = "custom_"

        /**
         * The stored frame override meaning "no frame". A null override means Auto, which picks the
         * platform's first frame, so the two must never be conflated when saving.
         */
        const val NO_FRAME_ID = "none"

        const val GITHUB_RAW_BASE =
            "https://raw.githubusercontent.com/libretro/overlay-borders/master/"

        /**
         * Pinned rather than tracking a branch: this is one person's repository, and a renamed
         * folder would break bezels for users with no app change involved.
         */
        const val DUIMON_RAW_BASE =
            "https://raw.githubusercontent.com/Duimon/Duimon-Mega-Bezel/" +
                "d03dabf6e6b190dbf9b692efd492d8edd21abbb9/Graphics/"

        fun downloadUrl(entry: FrameEntry): String = when (entry.source) {
            Source.LIBRETRO -> "$GITHUB_RAW_BASE${entry.githubPath}"
            Source.DUIMON -> "$DUIMON_RAW_BASE${entry.githubPath}"
            Source.CUSTOM -> ""
        }
    }
}
