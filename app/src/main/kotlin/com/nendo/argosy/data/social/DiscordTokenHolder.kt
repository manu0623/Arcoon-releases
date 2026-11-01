package com.nendo.argosy.data.social

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class DiscordToken(
    val accessToken: String,
    val tokenType: String,
    val expiresAtMillis: Long
)

@Singleton
class DiscordTokenHolder @Inject constructor() {

    private val _token = MutableStateFlow<DiscordToken?>(null)
    val token: StateFlow<DiscordToken?> = _token.asStateFlow()

    fun update(accessToken: String, tokenType: String, expiresIn: Long) {
        _token.value = DiscordToken(
            accessToken = accessToken,
            tokenType = tokenType,
            expiresAtMillis = System.currentTimeMillis() + (expiresIn * 1000)
        )
    }

    fun clear() {
        _token.value = null
    }

    fun isValid(): Boolean {
        val t = _token.value ?: return false
        return System.currentTimeMillis() < t.expiresAtMillis
    }
}
