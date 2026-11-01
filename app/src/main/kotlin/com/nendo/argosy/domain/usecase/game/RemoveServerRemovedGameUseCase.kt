package com.nendo.argosy.domain.usecase.game

import com.nendo.argosy.data.local.dao.HomeTileDao
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.LocalRomFiles
import javax.inject.Inject

class RemoveServerRemovedGameUseCase @Inject constructor(
    private val gameRepository: GameRepository,
    private val localRomFiles: LocalRomFiles,
    private val homeTileDao: HomeTileDao
) {
    suspend operator fun invoke(gameId: Long): Boolean {
        val game = gameRepository.getById(gameId) ?: return false
        if (!game.isRemovedFromRomm) return false
        if (localRomFiles.anyFor(game)) return false
        gameRepository.delete(gameId)
        homeTileDao.deleteTilesForMissingGames()
        return true
    }
}
