package com.nendo.argosy.data.emulator

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemDataFetcher @Inject constructor() {

    fun <T> fetch(url: String, read: (InputStream) -> T): T {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        try {
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw RuntimeException("HTTP $responseCode from $url")
            }
            return connection.inputStream.use(read)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 30_000
        const val READ_TIMEOUT_MS = 60_000
    }
}
