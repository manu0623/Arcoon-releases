package com.nendo.argosy.data.platform

import android.content.Context
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlatformFolderResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val platformDao: PlatformDao,
    private val gameDao: GameDao,
    private val preferencesRepository: UserPreferencesRepository
) {
    val defaultStorageBase: File by lazy {
        File(context.getExternalFilesDir(null), "downloads")
    }

    suspend fun storageBase(): File =
        preferencesRepository.userPreferences.first().romStoragePath?.let { File(it) }
            ?: defaultStorageBase

    suspend fun downloadDirForPlatform(platformId: Long, fallbackSlug: String): File =
        platformDownloadDir(platformDao.getById(platformId), fallbackSlug, storageBase())

    suspend fun downloadDirForGame(gameId: Long, fallbackSlug: String): File {
        val platformId = gameDao.getById(gameId)?.platformId
            ?: return platformDownloadDir(null, fallbackSlug, storageBase())
        return downloadDirForPlatform(platformId, fallbackSlug)
    }

    suspend fun romRootsForPlatform(platformId: Long, fallbackSlug: String): List<File> {
        val base = storageBase()
        val platform = platformDao.getById(platformId) ?: return listOf(File(base, fallbackSlug))
        return platformRomRoots(platform, base, platformDao.getAllPlatforms())
    }
}
