package com.nendo.argosy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LogSanitizerTest {

    @Test
    fun `query strings leave the url`() {
        val out = LogSanitizer.sanitize("GET https://romm.lan/api/roms/12/content?token=abc123def&x=1 ok")
        assertEquals("GET https://[host]/api/roms/12/content?[query] ok", out)
    }

    @Test
    fun `bearer tokens are redacted`() {
        val out = LogSanitizer.sanitize("Authorization: Bearer eyJhbGciOi.payload.sig")
        assertFalse(out.contains("eyJhbGciOi"))
    }

    @Test
    fun `key value credentials are redacted`() {
        assertEquals("access_token=[redacted]", LogSanitizer.sanitize("access_token=s3cr3tvalue"))
        assertEquals("password: [redacted]", LogSanitizer.sanitize("password: hunter22"))
        assertEquals("\"api_key\":\"[redacted]\"", LogSanitizer.sanitize("\"api_key\":\"0123456789\""))
    }

    @Test
    fun `prose that mentions a token is left alone`() {
        assertEquals("token refreshed for user", LogSanitizer.sanitize("token refreshed for user"))
    }
}
