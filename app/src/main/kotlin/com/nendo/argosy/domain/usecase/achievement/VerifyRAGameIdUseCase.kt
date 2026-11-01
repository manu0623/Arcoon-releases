package com.nendo.argosy.domain.usecase.achievement

import com.nendo.argosy.data.download.ZipExtractor
import com.nendo.argosy.data.emulator.M3uManager
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.remote.ra.RAConsoleIds
import com.nendo.argosy.data.repository.RetroAchievementsRepository
import com.nendo.argosy.util.Logger
import com.swordfish.libretrodroid.LibretroDroid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject

private const val TAG = "VerifyRAGameIdUseCase"

data class HashVerification(val raId: Long?, val matchedHash: String?)

class VerifyRAGameIdUseCase @Inject constructor(
    private val gameDao: GameDao,
    private val raRepository: RetroAchievementsRepository
) {
    suspend operator fun invoke(gameId: Long, forceRehash: Boolean = false): Long? =
        withContext(Dispatchers.IO) { verify(gameId, forceRehash).raId }

    /**
     * Rehashes the file the core loads, [launchedPath], and resolves it against RetroAchievements.
     * [HashVerification.matchedHash] is set only when RetroAchievements resolved that exact hash to
     * [HashVerification.raId]; every fallback to the RomM id leaves it null.
     */
    suspend fun verifyHash(gameId: Long, launchedPath: String): HashVerification =
        withContext(Dispatchers.IO) {
            val game = gameDao.getById(gameId) ?: return@withContext HashVerification(null, null)
            if (isLibraryCopy(launchedPath, game.localPath)) verify(gameId, forceRehash = true)
            else verifyLaunchedFile(game, launchedPath)
        }

    private suspend fun verifyLaunchedFile(game: GameEntity, launchedPath: String): HashVerification {
        val fallback = HashVerification(game.effectiveRaId ?: game.raId, null)
        val consoleId = RAConsoleIds.getConsoleId(game.platformSlug) ?: return fallback
        val hashPath = resolveHashPath(launchedPath, game.platformSlug) ?: return fallback
        val hash = try {
            computeHash(hashPath.path, consoleId)
        } finally {
            if (hashPath.isTemporary) File(hashPath.path).delete()
        } ?: return fallback
        val resolvedId = try {
            raRepository.resolveGameId(hash)
        } catch (e: Exception) {
            Logger.warn(TAG, "Network error resolving the launched file's hash $hash: ${e.message}")
            return fallback
        }
        Logger.info(TAG, "Launched file differs from the library copy for game ${game.id}: hash=$hash resolved=$resolvedId")
        return if (resolvedId != null) HashVerification(resolvedId, hash) else fallback
    }

    private suspend fun verify(gameId: Long, forceRehash: Boolean): HashVerification {
        val game = gameDao.getById(gameId) ?: return HashVerification(null, null)

        if (game.raIdVerified && !forceRehash) {
            return HashVerification(game.effectiveRaId, null)
        }

        val localPath = game.localPath
        if (localPath == null) {
            gameDao.updateVerifiedRaId(gameId, game.raId)
            return HashVerification(game.raId, null)
        }

        val consoleId = RAConsoleIds.getConsoleId(game.platformSlug)
        if (consoleId == null) {
            gameDao.updateVerifiedRaId(gameId, game.raId)
            return HashVerification(game.raId, null)
        }

        val hashPath = resolveHashPath(localPath, game.platformSlug)
        if (hashPath == null) {
            gameDao.updateVerifiedRaId(gameId, game.raId)
            return HashVerification(game.raId, null)
        }

        val hash = if (!forceRehash && game.romHash != null) {
            game.romHash
        } else {
            try {
                computeHash(hashPath.path, consoleId)?.also { gameDao.updateRomHash(gameId, it) }
            } finally {
                if (hashPath.isTemporary) File(hashPath.path).delete()
            }
        }

        if (hash == null) {
            Logger.warn(TAG, "Failed to compute hash for game $gameId ($localPath)")
            gameDao.updateVerifiedRaId(gameId, game.raId)
            return HashVerification(game.raId, null)
        }

        val resolvedId = try {
            raRepository.resolveGameId(hash)
        } catch (e: Exception) {
            Logger.warn(TAG, "Network error resolving game ID for hash $hash: ${e.message}")
            return HashVerification(game.effectiveRaId ?: game.raId, null)
        }

        if (resolvedId != null) {
            gameDao.updateVerifiedRaId(gameId, resolvedId)
            if (resolvedId != game.raId) {
                Logger.info(TAG, "Verified RA ID differs from RomM: verified=$resolvedId, romm=${game.raId} (game $gameId)")
            }
            return HashVerification(resolvedId, hash)
        }

        Logger.warn(TAG, "RA hash lookup returned no match for game $gameId (hash=$hash); not persisting verified=null, falling back to romm raId=${game.raId}")
        return HashVerification(game.raId, null)
    }

    /**
     * The file rcheevos should hash. Only the archive branch materialises anything: the
     * m3u branch points at one of the user's real discs, so the caller must not delete
     * what it is handed without checking [HashSource.isTemporary].
     */
    private class HashSource(val path: String, val isTemporary: Boolean)

    private fun resolveHashPath(localPath: String, platformSlug: String): HashSource? {
        if (localPath.endsWith(".m3u", ignoreCase = true)) {
            val firstDisc = M3uManager.parseFirstDisc(File(localPath))
            if (firstDisc == null) {
                Logger.warn(TAG, "Could not parse first disc from m3u: $localPath")
                return null
            }
            return HashSource(firstDisc.absolutePath, isTemporary = false)
        }

        val file = File(localPath)
        if (ZipExtractor.isArchiveFile(file) && !ZipExtractor.usesZipAsRomFormat(platformSlug)) {
            val extracted = extractRomFromZipForHash(file) ?: return null
            return HashSource(extracted, isTemporary = true)
        }

        return HashSource(localPath, isTemporary = false)
    }

    private fun extractRomFromZipForHash(zipFile: File): String? {
        return try {
            ZipFile(zipFile).use { zip ->
                val entry = zip.entries().asSequence()
                    .filter { !it.isDirectory }
                    .maxByOrNull { it.size }
                    ?: return null

                val tempFile = File.createTempFile(
                    "ra_hash_",
                    "_${entry.name.substringAfterLast('/')}"
                )
                zip.getInputStream(entry).use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
                Logger.info(TAG, "Extracted ${entry.name} from archive for hashing")
                tempFile.absolutePath
            }
        } catch (e: Exception) {
            Logger.warn(TAG, "Failed to extract ROM from archive for hashing: ${e.message}")
            null
        }
    }

    private fun computeHash(path: String, consoleId: Int): String? {
        return try {
            LibretroDroid.computeRomHash(path, consoleId)
        } catch (e: Exception) {
            Logger.warn(TAG, "Hash computation failed for $path: ${e.message}")
            null
        }
    }

    companion object {
        internal fun isLibraryCopy(launchedPath: String, localPath: String?): Boolean {
            localPath ?: return false
            val launched = File(launchedPath).canonicalPath
            if (launched == File(localPath).canonicalPath) return true
            return localPath.endsWith(".m3u", ignoreCase = true) &&
                M3uManager.parseFirstDisc(File(localPath))?.canonicalPath == launched
        }
    }
}
