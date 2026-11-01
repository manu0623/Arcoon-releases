package com.nendo.argosy.data.repository

import com.nendo.argosy.data.remote.ra.RAWebResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The last RetroAchievements web API key the server answered 401 or 403 for, until a call with
 * that key succeeds. Held in memory only; the stored key is never cleared from here.
 */
@Singleton
class RAWebKeyHealth @Inject constructor() {
    private val _rejectedKey = MutableStateFlow<String?>(null)
    val rejectedKey: StateFlow<String?> = _rejectedKey.asStateFlow()

    fun record(apiKey: String, result: RAWebResult<*>) {
        when (result) {
            is RAWebResult.Rejected -> _rejectedKey.value = apiKey
            is RAWebResult.Success -> _rejectedKey.compareAndSet(apiKey, null)
            is RAWebResult.Failed -> Unit
        }
    }

    companion object {
        fun isRejected(storedKey: String?, rejectedKey: String?): Boolean =
            !storedKey.isNullOrBlank() && storedKey == rejectedKey
    }
}
