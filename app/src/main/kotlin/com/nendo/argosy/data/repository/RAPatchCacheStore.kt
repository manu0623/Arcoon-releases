package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.RAPatchCacheDao
import com.nendo.argosy.data.local.entity.RAPatchCacheEntity
import com.nendo.argosy.data.remote.ra.RAPatchData
import com.nendo.argosy.util.Logger
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RAPatchCacheStore"

@Singleton
class RAPatchCacheStore @Inject constructor(
    private val dao: RAPatchCacheDao
) {
    private val adapter = Moshi.Builder().build().adapter(RAPatchData::class.java)

    suspend fun write(raGameId: Long, ownerUserId: Long, patchData: RAPatchData, fetchedAt: Long) {
        dao.upsert(
            RAPatchCacheEntity(
                raGameId = raGameId,
                ownerUserId = ownerUserId,
                patchJson = adapter.toJson(patchData),
                fetchedAt = fetchedAt
            )
        )
    }

    suspend fun read(raGameId: Long, ownerUserId: Long): RAPatchData? {
        val row = dao.get(raGameId, ownerUserId) ?: return null
        return try {
            adapter.fromJson(row.patchJson)
        } catch (e: JsonDataException) {
            Logger.warn(TAG, "Unreadable cached patch for RA game $raGameId: ${e.message}")
            null
        } catch (e: IOException) {
            Logger.warn(TAG, "Unreadable cached patch for RA game $raGameId: ${e.message}")
            null
        }
    }

    suspend fun contains(raGameId: Long, ownerUserId: Long): Boolean =
        dao.get(raGameId, ownerUserId) != null

    /**
     * RA game ids of installed games that have no cached patch under [ownerUserId], in the order
     * the library returns them.
     */
    suspend fun uncachedInstalledRaGameIds(ownerUserId: Long): List<Long> {
        val cached = dao.getCachedRaGameIds(ownerUserId).toSet()
        return dao.getInstalledRaGameIds().filterNot { it in cached }
    }
}
