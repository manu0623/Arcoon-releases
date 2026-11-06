package com.nendo.argosy.data.remote.steamgriddb

import com.nendo.argosy.data.local.dao.GameArtDao
import com.nendo.argosy.data.model.ArtSlot
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private const val STEAM_GRID_DB_HOST = "steamgriddb.com"

/**
 * Whether [url] is a SteamGridDB asset, which no RomM sync may overwrite.
 */
fun isSteamGridDbArtUrl(url: String?): Boolean = url?.contains(STEAM_GRID_DB_HOST, ignoreCase = true) == true

/**
 * The one rule for cover ownership. A live integration owns every cover from the moment it is
 * switched on, and a cover the scraper already supplied keeps its SteamGridDB source after the
 * integration is switched off. The RomM writers ask this before they record, cache or delete a
 * cover, which stops a later server sync from taking the slot back.
 */
@Singleton
class SteamGridDbCoverOwner @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val gameArtDao: GameArtDao
) {
    suspend fun ownsCover(gameId: Long): Boolean {
        val prefs = userPreferencesRepository.userPreferences.first()
        val active = prefs.steamGridDbEnabled && !prefs.steamGridDbApiKey.isNullOrBlank()
        if (active) return true
        return isSteamGridDbArtUrl(gameArtDao.get(gameId, ArtSlot.COVER.name)?.sourceUrl)
    }
}
