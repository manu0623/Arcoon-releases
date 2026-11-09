package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.download.ZipExtractor
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.VariantCategory
import com.nendo.argosy.data.platform.PlatformFolderResolver
import com.nendo.argosy.util.Logger
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BaseRomFileResolver"

/**
 * Resolves the file that is the game itself from the path the library recorded for it.
 *
 * A game's local path can land on an update, a dlc or a file inside a content subfolder, and on
 * title id platforms the largest file in the game's folder is the game. Launching and title id
 * extraction share this so they can never read different files for the same game.
 */
@Singleton
class BaseRomFileResolver @Inject constructor(
    private val gameDao: GameDao,
    private val platformFolderResolver: PlatformFolderResolver,
    // Always supplied by the app; null only in tests written before packs were told apart.
    private val azaharTexturePacks: AzaharTexturePacks? = null
) {
    /**
     * An update or DLC file is unbootable alone, so redirect to the base rom and persist it.
     *
     * Title id platforms always re-elect the base, other platforms only when the target is
     * provably update/dlc content, preferring an m3u so multi-disc folders keep their playlist.
     */
    suspend fun resolve(game: GameEntity, romFile: File): File {
        val excluded = game.platformSlug in VariantCategory.TITLE_ID_PLATFORMS
        val parent = romFile.parentFile ?: return romFile
        val inContentSubfolder = parent.name.lowercase() in ZipExtractor.ADDON_FOLDERS
        if (!excluded && !inContentSubfolder && !isUpdateOrDlc(romFile.name)) return romFile
        val gameFolder = if (inContentSubfolder) {
            parent.parentFile ?: return romFile
        } else {
            parent
        }
        if (isPlatformRoot(gameFolder, game)) return romFile
        val candidates = (gameFolder.listFiles()?.filter { it.isFile } ?: return romFile)
            .filterNot { isUpdateOrDlc(it.name) }
        val base = if (excluded) {
            // A 3DS game's folder can also hold its HD texture pack, often bigger than the game:
            // chosen as "the largest file", the pack was launched and the core refused it
            // (Ocarina of Time 3D). Packs are never the game, and a plain ROM file beats any archive.
            val games = candidates.filterNot { isTexturePack(game, it) }.ifEmpty { candidates }
            val romExtensions = com.nendo.argosy.data.platform.PlatformDefinitions
                .definitionFor(game.platformSlug)?.extensions.orEmpty() - ARCHIVE_EXTENSIONS
            games.filter { it.extension.lowercase() in romExtensions }.maxByOrNull { it.length() }
                ?: games.maxByOrNull { it.length() }
        } else {
            candidates.firstOrNull { it.extension.equals("m3u", ignoreCase = true) }
                ?: candidates.maxByOrNull { it.length() }
        } ?: return romFile
        if (base.absolutePath == romFile.absolutePath) return romFile
        Logger.info(TAG, "redirecting ${romFile.name} -> ${base.name} for ${game.title}")
        gameDao.updateLocalPath(game.id, base.absolutePath, game.source, game.fileOrigin)
        return base
    }

    private fun isTexturePack(game: GameEntity, file: File): Boolean {
        val packs = azaharTexturePacks ?: return false
        return packs.appliesTo(game.platformSlug) &&
            runCatching { packs.looksLikeTexturePack(file.name, null, file) }.getOrDefault(false)
    }

    private fun isUpdateOrDlc(fileName: String): Boolean {
        val stem = fileName.substringBeforeLast('.')
        return UPDATE_DLC_TAG.containsMatchIn(stem) || UPDATE_DLC_SUFFIX.containsMatchIn(stem)
    }

    /**
     * True when [dir] is a directory roms sit directly inside rather than a game's own folder.
     *
     * Checked against every root discovery is allowed to link from, not just the download
     * destination: a rom found in one of the other roots would otherwise look like it lived in a
     * game folder, and the folder logic would repoint the game at the platform directory's
     * largest file.
     */
    private suspend fun isPlatformRoot(dir: File, game: GameEntity): Boolean {
        val target = runCatching { dir.canonicalFile }.getOrDefault(dir.absoluteFile)
        return platformFolderResolver.romRootsForPlatform(game.platformId, game.platformSlug).any { root ->
            runCatching { root.canonicalFile }.getOrDefault(root.absoluteFile) == target
        }
    }

    private companion object {
        val UPDATE_DLC_SUFFIX = Regex("(?i)[ _-]+(update|dlc)([ _-]?\\d+)?$")
        val UPDATE_DLC_TAG = Regex("(?i)\\[(upd|update|dlc)]")
        val ARCHIVE_EXTENSIONS = setOf("zip", "7z")
    }
}
