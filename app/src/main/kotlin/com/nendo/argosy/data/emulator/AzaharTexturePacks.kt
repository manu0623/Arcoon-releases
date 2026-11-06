package com.nendo.argosy.data.emulator

import android.content.Context
import com.nendo.argosy.data.local.dao.CoreOptionOverrideDao
import com.nendo.argosy.data.local.dao.GameCoreOptionOverrideDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameCoreOptionOverrideEntity
import com.nendo.argosy.data.local.entity.GameEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Azahar core's texture pack directory and installer: `<save dir>/Azahar/load/textures/<16 hex
 * title id>/`, mirrored into `variants/<file id>/` where a variant launch moves the save directory.
 */
@Singleton
class AzaharTexturePacks @Inject constructor(
    @ApplicationContext private val context: Context,
    private val builtinSaveBase: BuiltinSaveBase,
    private val gameCoreOptionOverrideDao: GameCoreOptionOverrideDao,
    private val coreOptionOverrideDao: CoreOptionOverrideDao,
    private val gameDao: GameDao,
) {
    data class State(
        val supported: Boolean = false,
        val enabled: Boolean = false,
        val directory: String? = null,
        val fileCount: Int = 0,
        val totalBytes: Long = 0L,
        val hasPackConfig: Boolean = false,
    )

    data class ImportResult(
        val fileCount: Int,
        val totalBytes: Long,
        val hasPackConfig: Boolean,
        val directory: String,
    )

    suspend fun load(game: GameEntity): State {
        val folder = folderName(game.titleId) ?: return State()
        val directory = withContext(Dispatchers.IO) {
            repairLayout(game, folder)
            primaryDir(game, folder)
        }
        val summary = withContext(Dispatchers.IO) { summarize(directory) }
        return State(
            supported = true,
            enabled = resolveEnabled(game.id),
            directory = directory.absolutePath,
            fileCount = summary.fileCount,
            totalBytes = summary.bytes,
            hasPackConfig = summary.hasPackConfig,
        )
    }

    suspend fun setEnabled(gameId: Long, enabled: Boolean) {
        if (enabled) gameDao.setPerGameSettingsEnabled(gameId, true)
        gameCoreOptionOverrideDao.upsert(
            GameCoreOptionOverrideEntity(
                gameId = gameId,
                coreId = CORE_ID,
                optionKey = OPTION_KEY,
                value = if (enabled) ENABLED else DISABLED,
            )
        )
    }

    fun looksLikeTexturePack(fileName: String, category: String?): Boolean {
        if (category == MOD_CATEGORY) return true
        val name = fileName.substringBeforeLast('.').replace('_', ' ').replace('-', ' ')
        return TEXTURE_NAME.containsMatchIn(name)
    }

    fun detectTitleId(source: File): String? = runCatching {
        when {
            !source.exists() -> null
            source.isDirectory -> source.walkTopDown().firstNotNullOfOrNull { folderName(it.name) }
            isZipArchive(source) -> ZipFile(source).use { zip ->
                zip.entries().asSequence().firstNotNullOfOrNull { entry ->
                    entry.name.replace('\\', '/').split('/').firstNotNullOfOrNull { folderName(it) }
                }
            }
            isSevenZArchive(source) -> SevenZFile.builder().setFile(source).get().use { sevenZ ->
                sevenZ.entries.firstNotNullOfOrNull { entry ->
                    entry.name.replace('\\', '/').split('/').firstNotNullOfOrNull { folderName(it) }
                }
            }
            else -> null
        }
    }.getOrNull()

    suspend fun isInstalledFrom(game: GameEntity, sourceKey: String): Boolean =
        withContext(Dispatchers.IO) {
            val folder = folderName(game.titleId) ?: return@withContext false
            val marker = File(primaryDir(game, folder), MARKER_FILE)
            marker.isFile && marker.readLines().any { it.trim() == sourceKey }
        }

    suspend fun installedSources(game: GameEntity): Set<String> = withContext(Dispatchers.IO) {
        val folder = folderName(game.titleId) ?: return@withContext emptySet()
        val marker = File(primaryDir(game, folder), MARKER_FILE)
        if (!marker.isFile) emptySet() else marker.readLines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    suspend fun markInstalledFrom(game: GameEntity, sourceKey: String) = withContext(Dispatchers.IO) {
        val folder = folderName(game.titleId) ?: return@withContext
        targetDirs(game, folder).forEach { target ->
            target.mkdirs()
            val marker = File(target, MARKER_FILE)
            val existing = if (marker.isFile) marker.readText() else ""
            if (existing.lines().none { it.trim() == sourceKey }) {
                marker.writeText(existing.trimEnd() + "\n" + sourceKey)
            }
        }
    }

    suspend fun install(game: GameEntity, open: () -> InputStream?): ImportResult? =
        withContext(Dispatchers.IO) {
            val archive = File(context.cacheDir, ARCHIVE_NAME)
            try {
                val input = open() ?: return@withContext null
                input.use { source ->
                    archive.outputStream().buffered().use { target -> source.copyTo(target) }
                }
                installFromFile(game, archive)
            } finally {
                archive.delete()
            }
        }

    suspend fun installFromFile(game: GameEntity, source: File): ImportResult? =
        withContext(Dispatchers.IO) {
            val folder = folderName(game.titleId) ?: return@withContext null
            if (!source.exists()) return@withContext null
            val staging = File(context.cacheDir, STAGING_DIR_NAME).apply {
                deleteRecursively()
                mkdirs()
            }
            try {
                val staged = when {
                    source.isDirectory -> stageDirectory(source, staging)
                    isZipArchive(source) -> ZipFile(source).use { zip -> unpack(zip, staging) }
                    isSevenZArchive(source) -> stageSevenZ(source, staging)
                    else -> stageSingleFile(source, staging)
                }
                if (!staged) return@withContext null
                val packRoot = locatePackRoot(staging, folder) ?: return@withContext null
                commit(game, folder, packRoot)
            } finally {
                staging.deleteRecursively()
            }
        }

    private suspend fun commit(game: GameEntity, folder: String, root: File): ImportResult? {
        if (summarize(root).fileCount == 0) return null
        targetDirs(game, folder).forEach { target ->
            target.mkdirs()
            root.copyRecursively(target, overwrite = true)
        }
        val primary = primaryDir(game, folder)
        val summary = summarize(primary)
        return ImportResult(
            fileCount = summary.fileCount,
            totalBytes = summary.bytes,
            hasPackConfig = summary.hasPackConfig,
            directory = primary.absolutePath,
        )
    }

    private fun stageDirectory(source: File, staging: File): Boolean {
        val files = source.walkTopDown().filter { it.isFile }.toList()
        if (files.isEmpty() || files.size > MAX_ENTRIES) return false
        val relative = files.associateWith { file ->
            file.relativeTo(source).path.replace('\\', '/')
        }
        val stripPrefix = sharedRootPrefix(relative.values.toList())
        val root = staging.canonicalFile
        var written = 0L
        for ((file, path) in relative) {
            val name = if (stripPrefix.isNotEmpty() && path.startsWith(stripPrefix, true)) {
                path.substring(stripPrefix.length)
            } else {
                path
            }
            if (name.isBlank()) continue
            val target = File(root, name).canonicalFile
            if (!target.path.startsWith(root.path + File.separator)) return false
            target.parentFile?.mkdirs()
            file.copyTo(target, overwrite = true)
            written += file.length()
            if (written > MAX_TOTAL_BYTES) return false
        }
        return true
    }

    private fun stageSingleFile(source: File, staging: File): Boolean {
        if (source.length() > MAX_ENTRY_BYTES) return false
        source.copyTo(File(staging, source.name), overwrite = true)
        return true
    }

    private fun stageSevenZ(source: File, staging: File): Boolean = runCatching {
        SevenZFile.builder().setFile(source).get().use { sevenZ ->
            val entries = sevenZ.entries.filter { !it.isDirectory }
            if (entries.isEmpty() || entries.size > MAX_ENTRIES) return false
            val stripPrefix = sharedRootPrefix(entries.map { it.name })
            val root = staging.canonicalFile
            var written = 0L
            for (entry in entries) {
                val name = entry.name.replace('\\', '/')
                if (name.startsWith(SKIP_DIR) || name.substringAfterLast('/') in SKIP_FILES) continue
                val relative = if (stripPrefix.isNotEmpty() && name.startsWith(stripPrefix, true)) {
                    name.substring(stripPrefix.length)
                } else {
                    name
                }
                if (relative.isBlank() || relative.endsWith("/")) continue
                if (entry.size > MAX_ENTRY_BYTES) return false
                val target = File(root, relative).canonicalFile
                if (!target.path.startsWith(root.path + File.separator)) return false
                target.parentFile?.mkdirs()
                sevenZ.getInputStream(entry).use { input ->
                    target.outputStream().buffered().use { output ->
                        written += input.copyTo(output)
                    }
                }
                if (written > MAX_TOTAL_BYTES) return false
            }
            true
        }
    }.getOrDefault(false)

    private fun isZipArchive(file: File): Boolean = file.isFile && file.length() >= 4 &&
        file.inputStream().use { stream ->
            val magic = ByteArray(2)
            stream.read(magic) == 2 && magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte()
        }

    private fun isSevenZArchive(file: File): Boolean = file.isFile && file.length() >= 6 &&
        file.inputStream().use { stream ->
            val magic = ByteArray(6)
            if (stream.read(magic) != 6) return@use false
            magic[0] == 0x37.toByte() && magic[1] == 0x7A.toByte() && magic[2] == 0xBC.toByte() &&
                magic[3] == 0xAF.toByte() && magic[4] == 0x27.toByte() && magic[5] == 0x1C.toByte()
        }

    private suspend fun resolveEnabled(gameId: Long): Boolean {
        val perGame = gameCoreOptionOverrideDao.getForGame(gameId, CORE_ID)
            .firstOrNull { it.optionKey == OPTION_KEY }
        if (perGame != null) return perGame.value.equals(ENABLED, ignoreCase = true)
        val global = coreOptionOverrideDao.getOverridesForCore(CORE_ID)
            .firstOrNull { it.optionKey == OPTION_KEY }
        return global?.value?.equals(ENABLED, ignoreCase = true) ?: false
    }

    private suspend fun primaryDir(game: GameEntity, folder: String): File =
        textureDir(builtinSaveBase.forGame(game), folder)

    private suspend fun targetDirs(game: GameEntity, folder: String): List<File> {
        val base = builtinSaveBase.forGame(game)
        val variants = File(base, VARIANT_DIR_NAME).listFiles()
            ?.filter { it.isDirectory }
            .orEmpty()
            .map { textureDir(it.path, folder) }
        return listOf(textureDir(base, folder)) + variants
    }

    private fun textureDir(base: String, folder: String): File =
        File(File(base, USER_DIR_NAME), LOAD_TEXTURE_DIR).let { File(it, folder) }

    private suspend fun repairLayout(game: GameEntity, folder: String) {
        targetDirs(game, folder).forEach { dir ->
            if (!dir.isDirectory || File(dir, PACK_CONFIG).isFile) return@forEach
            val packRoot = locatePackRoot(dir, folder) ?: return@forEach
            packRoot.listFiles()?.forEach { moveInto(it, File(dir, it.name)) }
            packRoot.deleteRecursively()
            generateSequence(packRoot.parentFile) { it.parentFile }
                .takeWhile { it != dir && it.path.startsWith(dir.path) }
                .forEach { ancestor -> if (ancestor.listFiles().isNullOrEmpty()) ancestor.delete() }
        }
    }

    private fun locatePackRoot(root: File, folder: String): File? {
        if (File(root, PACK_CONFIG).isFile) return root
        val dirs = root.walkTopDown().filter { it.isDirectory }.toList()
        val nested = "load/textures/$folder"
        val packed = dirs.filter { File(it, PACK_CONFIG).isFile }
        packed.firstOrNull { it.relativeTo(root).path.endsWith(nested, true) }
            ?.let { return it }
        packed.firstOrNull { it.name.equals(folder, true) }?.let { return it }
        packed.firstOrNull()?.let { return it }
        dirs.firstOrNull { it.relativeTo(root).path.endsWith(nested, true) }?.let { return it }
        val children = root.listFiles()?.filter { it.name != MARKER_FILE }.orEmpty()
        if (children.any { it.isFile }) return null
        val only = children.singleOrNull()?.takeIf { it.isDirectory } ?: return null
        val holdsTextures = only.walkTopDown()
            .any { it.isFile && it.extension.lowercase() in TEXTURE_EXTENSIONS }
        return only.takeIf { holdsTextures }
    }

    private fun moveInto(source: File, target: File) {
        target.parentFile?.mkdirs()
        if (source.renameTo(target)) return
        source.copyRecursively(target, overwrite = true)
        source.deleteRecursively()
    }

    private fun summarize(dir: File): Summary {
        if (!dir.isDirectory) return Summary(0, 0L, false)
        var files = 0
        var bytes = 0L
        dir.walkTopDown().forEach { file ->
            if (file.isFile) {
                files++
                bytes += file.length()
            }
        }
        return Summary(files, bytes, File(dir, PACK_CONFIG).isFile)
    }

    private fun unpack(zip: ZipFile, staging: File): Boolean {
        val entries = zip.entries().asSequence()
            .filter { !it.isDirectory }
            .filter { entry -> entry.name.isNotBlank() }
            .toList()
        if (entries.isEmpty() || entries.size > MAX_ENTRIES) return false

        val stripPrefix = sharedRootPrefix(entries.map { it.name })
        val root = staging.canonicalFile
        var written = 0L
        for (entry in entries) {
            val name = entry.name.replace('\\', '/')
            val relative = when {
                name.startsWith(SKIP_DIR) -> continue
                name.substringAfterLast('/') in SKIP_FILES -> continue
                stripPrefix.isNotEmpty() && name.startsWith(stripPrefix, ignoreCase = true) ->
                    name.substring(stripPrefix.length)
                else -> name
            }
            if (relative.isBlank() || relative.endsWith("/")) continue
            val target = File(root, relative).canonicalFile
            if (!target.path.startsWith(root.path + File.separator)) return false
            val size = entry.size
            if (size > MAX_ENTRY_BYTES) return false
            target.parentFile?.mkdirs()
            zip.getInputStream(entry).use { input ->
                target.outputStream().buffered().use { output ->
                    written += input.copyTo(output)
                }
            }
            if (written > MAX_TOTAL_BYTES) return false
        }
        return true
    }

    private fun sharedRootPrefix(names: List<String>): String {
        val normalized = names.map { it.replace('\\', '/').trimStart('.', '/') }
        val first = normalized.map { it.substringBefore('/') }.toSet()
        if (first.size != 1) return ""
        val root = first.first()
        if (root.isEmpty() || normalized.any { !it.contains('/') }) return ""
        return "$root/"
    }

    private fun folderName(titleId: String?): String? {
        val trimmed = titleId?.trim()?.uppercase() ?: return null
        if (trimmed.length != TITLE_ID_LENGTH) return null
        if (!trimmed.all { it in "0123456789ABCDEF" }) return null
        return trimmed
    }

    private data class Summary(val fileCount: Int, val bytes: Long, val hasPackConfig: Boolean)

    companion object {
        const val CORE_ID = "azahar"
        const val OPTION_KEY = "citra_custom_textures"

        private const val ENABLED = "enabled"
        private const val DISABLED = "disabled"
        private const val USER_DIR_NAME = "Azahar"
        private const val LOAD_TEXTURE_DIR = "load/textures"
        private const val VARIANT_DIR_NAME = "variants"
        private const val PACK_CONFIG = "pack.json"
        private const val MARKER_FILE = ".argosy-source"
        private const val MOD_CATEGORY = "mod"
        private val TEXTURE_NAME = Regex("(?i)texture|tex pack|custom tex")
        private const val TITLE_ID_LENGTH = 16
        private const val STAGING_DIR_NAME = "azahar_texture_pack"
        private const val ARCHIVE_NAME = "azahar_texture_pack.zip"
        private const val SKIP_DIR = "__MACOSX/"
        private val SKIP_FILES = setOf(".DS_Store", "Thumbs.db")
        private val TEXTURE_EXTENSIONS = setOf("png", "dds", "ktx")
        private const val MAX_ENTRIES = 20_000
        private const val MAX_ENTRY_BYTES = 512L * 1024 * 1024
        private const val MAX_TOTAL_BYTES = 4L * 1024 * 1024 * 1024
    }
}
