package com.nendo.argosy.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmbeddedMediaMetadataCodecTest {

    @Test
    fun `encodes and decodes localized metadata`() {
        val encoded = EmbeddedMediaMetadataCodec.encode("ゲーム", "Éditeur")

        val decoded = encoded?.let(EmbeddedMediaMetadataCodec::decode)

        assertEquals(EmbeddedMediaMetadata("ゲーム", "Éditeur"), decoded)
    }

    @Test
    fun `does not create empty metadata`() {
        assertNull(EmbeddedMediaMetadataCodec.encode(null, " "))
    }

    @Test
    fun `rejects unsupported metadata version`() {
        val encoded = "version=2\ntitle=Game\n".toByteArray()

        assertNull(EmbeddedMediaMetadataCodec.decode(encoded))
    }
}
