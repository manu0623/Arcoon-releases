package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CartFeatureScanner @Inject constructor(
    private val gameDao: GameDao,
    private val titleIdExtractor: TitleIdExtractor,
    private val baseRomFileResolver: BaseRomFileResolver
) {
    companion object {
        private const val TAG = "CartFeatureScanner"
        val CART_FEATURE_PLATFORMS = setOf("gb", "gbc", "snes")
    }

    fun appliesTo(platformSlug: String): Boolean =
        PlatformDefinitions.getCanonicalSlug(platformSlug) in CART_FEATURE_PLATFORMS

    suspend fun featuresFor(game: GameEntity): Int {
        game.saveFeatures?.let { return it }
        if (!appliesTo(game.platformSlug)) return 0
        return scan(game) ?: 0
    }

    suspend fun scan(game: GameEntity): Int? = withContext(Dispatchers.IO) {
        val recorded = File(game.localPath ?: return@withContext null)
        if (!recorded.exists()) return@withContext null
        val romFile = baseRomFileResolver.resolve(game, recorded).takeIf { it.exists() } ?: recorded
        val result = titleIdExtractor.extractTitleIdWithSource(romFile, game.platformSlug)
        val features = result?.features ?: 0
        gameDao.setSaveFeatures(game.id, features)
        Logger.debug(TAG, "[SaveSync] FEATURES gameId=${game.id} | file=${romFile.name} features=$features")
        features
    }
}
