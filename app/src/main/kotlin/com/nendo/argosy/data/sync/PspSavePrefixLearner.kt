package com.nendo.argosy.data.sync

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.util.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adopts the save prefix a PSP game wrote under the built-in core when its stored save id
 * matches none of the folders it wrote. Returns the adopted prefix, or null when the folders
 * name no single serial, already match the stored id, or carry a prefix another game owns.
 */
@Singleton
class PspSavePrefixLearner @Inject constructor(
    private val gameDao: GameDao
) {

    suspend fun adopt(gameId: Long, writtenFolders: Set<String>): String? {
        val game = gameDao.getById(gameId) ?: return null
        if (PlatformDefinitions.getCanonicalSlug(game.platformSlug) != PSP_SLUG) return null

        val prefixes = writtenFolders.map { SERIAL_PREFIX.find(it)?.value?.uppercase() }
        if (prefixes.isEmpty() || prefixes.any { it == null }) return null
        val prefix = prefixes.toSet().singleOrNull() ?: return null

        val stored = game.saveId ?: game.titleId
        if (stored.equals(prefix, ignoreCase = true)) return null
        if (gameDao.countOtherGamesKeyedBy(gameId, game.platformSlug, prefix) > 0) {
            Logger.info(TAG, "Not adopting $prefix for gameId=$gameId: another game is keyed by it")
            return null
        }

        Logger.info(TAG, "Adopting save prefix $prefix for gameId=$gameId (stored=$stored)")
        gameDao.setTitleAndSaveIdWithLock(gameId, game.titleId, prefix, locked = true)
        return prefix
    }

    private companion object {
        const val TAG = "PspSavePrefixLearner"
        const val PSP_SLUG = "psp"
        val SERIAL_PREFIX = Regex("^(UL|UC|NP)[A-Z]{2}[0-9]{5}", RegexOption.IGNORE_CASE)
    }
}
