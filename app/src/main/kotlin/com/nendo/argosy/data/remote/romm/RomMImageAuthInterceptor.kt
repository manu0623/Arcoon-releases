package com.nendo.argosy.data.remote.romm

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Carries the RomM session token on image requests aimed at the signed-in server, so endpoints
 * that require a scope (user avatars) load through the shared image loader. The header is added
 * only when the request's scheme, host and port match the connected server's own.
 */
class RomMImageAuthInterceptor(
    private val connectionManager: RomMConnectionManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = connectionManager.getAccessToken()
        if (token.isNullOrBlank() || request.header(AUTHORIZATION) != null) {
            return chain.proceed(request)
        }
        val server = connectionManager.getBaseUrl().toHttpUrlOrNull()
        if (server == null || !request.url.sameOriginAs(server)) return chain.proceed(request)
        return chain.proceed(
            request.newBuilder().header(AUTHORIZATION, "Bearer $token").build()
        )
    }

    private fun HttpUrl.sameOriginAs(other: HttpUrl): Boolean =
        scheme == other.scheme && host == other.host && port == other.port

    private companion object {
        const val AUTHORIZATION = "Authorization"
    }
}
