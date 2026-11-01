package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.entity.GameEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether a game row still points at rom content on this device: its own path, a downloaded
 * file row, or a disc path. Cached saves and states are not rom content.
 */
@Singleton
class LocalRomFiles @Inject constructor(
    private val gameFileDao: GameFileDao,
    private val gameDiscDao: GameDiscDao
) {
    suspend fun anyFor(game: GameEntity): Boolean =
        game.localPath != null ||
            gameFileDao.getDownloadedCount(game.id) > 0 ||
            gameDiscDao.getDiscsForGame(game.id).any { it.localPath != null }
}
