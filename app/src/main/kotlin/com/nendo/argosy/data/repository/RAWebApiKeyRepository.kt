package com.nendo.argosy.data.repository

import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.ra.RAWebClient
import com.nendo.argosy.data.remote.ra.RAWebCredentials
import com.nendo.argosy.data.remote.ra.RAWebResult
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

enum class RAWebApiKeyCheck { SAVED, REJECTED, UNREACHABLE, NOT_SIGNED_IN, EMPTY }

/**
 * The RetroAchievements web API key stored for the signed-in RA user. A key is saved only after
 * one read-only `API_GetUserSummary` call accepts it.
 */
@Singleton
class RAWebApiKeyRepository @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val webClient: RAWebClient,
    private val keyHealth: RAWebKeyHealth
) {
    suspend fun storedKey(): String? =
        preferencesRepository.userPreferences.first().raWebApiKey?.takeIf { it.isNotBlank() }

    suspend fun validateAndSave(candidate: String): RAWebApiKeyCheck {
        val key = candidate.trim()
        if (key.isEmpty()) return RAWebApiKeyCheck.EMPTY
        val username = preferencesRepository.userPreferences.first().raUsername?.takeIf { it.isNotBlank() }
            ?: return RAWebApiKeyCheck.NOT_SIGNED_IN
        val result = webClient.userSummary(RAWebCredentials(username, key))
        keyHealth.record(key, result)
        return when (result) {
            is RAWebResult.Success -> {
                preferencesRepository.setRAWebApiKey(key)
                webClient.clearCache()
                RAWebApiKeyCheck.SAVED
            }
            is RAWebResult.Rejected -> RAWebApiKeyCheck.REJECTED
            is RAWebResult.Failed -> RAWebApiKeyCheck.UNREACHABLE
        }
    }

    suspend fun clear() {
        preferencesRepository.setRAWebApiKey(null)
        webClient.clearCache()
    }

    companion object {
        const val VISIBLE_SUFFIX_LENGTH = 4

        fun lastCharsOf(key: String?): String? =
            key?.trim()?.takeIf { it.length > VISIBLE_SUFFIX_LENGTH }?.takeLast(VISIBLE_SUFFIX_LENGTH)
    }
}
