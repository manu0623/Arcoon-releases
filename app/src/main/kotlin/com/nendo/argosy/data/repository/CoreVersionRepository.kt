package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.CoreVersionDao
import com.nendo.argosy.libretro.LibretroCoreManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoreVersionRepository @Inject constructor(
    private val coreVersionDao: CoreVersionDao
) {
    fun observeUpdateCount(): Flow<Int> =
        coreVersionDao.observeWithUpdatesAvailable().map { rows ->
            rows.count { LibretroCoreManager.pendingUpdateOf(it) != null }
        }

    /**
     * The buildbot stamp of the downloaded build of [coreId], or null when no build was
     * downloaded.
     */
    suspend fun installedBuild(coreId: String): String? =
        coreVersionDao.getByCoreId(coreId)?.installedVersion
}
