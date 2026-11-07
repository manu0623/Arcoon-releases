package com.nendo.argosy.data.remote.steamgriddb

import com.nendo.argosy.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one rule for cover ownership: a live integration owns every cover from the moment it is
 * switched on, and switching it off hands every cover back to RomM. The RomM writers ask this
 * before they record, cache or delete a cover, which stops a server sync from taking the slot
 * while SteamGridDB is on.
 */
@Singleton
class SteamGridDbCoverOwner @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) {
    suspend fun ownsCover(gameId: Long): Boolean {
        val prefs = userPreferencesRepository.userPreferences.first()
        return prefs.steamGridDbEnabled && !prefs.steamGridDbApiKey.isNullOrBlank()
    }
}
