package com.nendo.argosy.data.storage

import androidx.room.withTransaction
import com.nendo.argosy.data.local.ALauncherDatabase
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.dao.GameFileDao
import javax.inject.Inject

class GamePathRewriter @Inject constructor(
    private val database: ALauncherDatabase,
    private val gameDao: GameDao,
    private val gameDiscDao: GameDiscDao,
    private val gameFileDao: GameFileDao
) {
    /**
     * Repoints every stored game, disc, add-on and m3u path at or below each old path to the
     * matching new path, in one transaction.
     */
    suspend fun rewrite(moves: List<Pair<String, String>>) = database.withTransaction {
        for ((old, new) in moves) {
            gameDao.relocateLocalPaths(old, new)
            gameDao.relocateM3uPaths(old, new)
            gameDiscDao.relocateLocalPaths(old, new)
            gameFileDao.relocateLocalPaths(old, new)
            gameFileDao.relocateM3uPaths(old, new)
        }
    }
}
