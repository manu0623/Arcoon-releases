package com.nendo.argosy.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ThreeDsBannerJingleTest {

    @Test
    fun `extracts the cwav that follows the cgfx into a wav`() {
        val banner = buildBanner(cwav = pcm16Cwav())

        val result = ThreeDsBannerParser.extractJingle(banner)

        assertTrue(result.toString(), result is BcwavResult.Success)
        val wav = (result as BcwavResult.Success).wav
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(wav, 8, 4, Charsets.US_ASCII))
    }

    @Test
    fun `returns null when the banner header declares no jingle`() {
        val banner = buildBanner(cwav = null)

        assertNull(ThreeDsBannerParser.extractJingle(banner))
    }

    @Test
    fun `returns null when the declared jingle offset lies past the header but holds no cwav`() {
        val banner = buildBanner(cwav = ByteArray(0x40))

        val result = ThreeDsBannerParser.extractJingle(banner)

        assertTrue(result is BcwavResult.Failure)
    }

    @Test
    fun `still renders the art when a jingle is present`() {
        val banner = buildBanner(cwav = pcm16Cwav())

        val art = ThreeDsBannerParser.parse(banner, Locale.US)

        assertTrue(art.toString(), art is ThreeDsBannerResult.Success)
    }

    private fun buildBanner(cwav: ByteArray?): ByteArray {
        val cgfxOffset = 0x88
        val cgfx = compressedCgfx()
        val cwavOffset = if (cwav != null) align32(cgfxOffset + cgfx.size) else 0
        val total = if (cwav != null) cwavOffset + cwav.size else cgfxOffset + cgfx.size
        val banner = ByteArray(total)
        putAscii(banner, 0, "CBMD")
        putU32(banner, 0x08, cgfxOffset)
        putU32(banner, 0x84, cwavOffset)
        cgfx.copyInto(banner, cgfxOffset)
        cwav?.copyInto(banner, cwavOffset)
        return banner
    }

    private fun align32(value: Int): Int = (value + 0x1F) and 0x1F.inv()

    private fun compressedCgfx(): ByteArray =
        byteArrayOf(0x11, 0x04, 0, 0, 0, 0x43, 0x47, 0x46, 0x58)

    private fun pcm16Cwav(): ByteArray {
        val infoOffset = 0x40
        val channelTable = infoOffset + 0x1C
        val channelEntry = channelTable + 0x24
        val dataOffset = (channelEntry + 0x20 + 0x20 + 0x1F) and 0x1F.inv()
        val dataField = dataOffset + 8
        val sampleBytes = byteArrayOf(0x10, 0x00, 0x20, 0x00)
        val total = dataField + sampleBytes.size
        val bytes = ByteArray(total)

        putAscii(bytes, 0, "CWAV")
        putU16(bytes, 0x04, 0xFEFF)
        putU16(bytes, 0x06, 0x40)
        putU32(bytes, 0x08, 0x02010000)
        putU32(bytes, 0x0C, total)
        putU16(bytes, 0x10, 2)
        putReference(bytes, 0x14, infoOffset)
        putReference(bytes, 0x20, dataOffset)

        putAscii(bytes, infoOffset, "INFO")
        putU32(bytes, infoOffset + 0x04, 0x40)
        bytes[infoOffset + 0x08] = 1
        putU32(bytes, infoOffset + 0x0C, 32_000)
        putU32(bytes, infoOffset + 0x14, 2)

        putU32(bytes, channelTable, 1)
        putReference(bytes, channelTable + 0x04, channelEntry - channelTable)
        putReference(bytes, channelEntry, 0)
        putReference(bytes, channelEntry + 0x08, 0x20)

        putAscii(bytes, dataOffset, "DATA")
        putU32(bytes, dataOffset + 0x04, 8 + sampleBytes.size)
        sampleBytes.copyInto(bytes, dataField)
        return bytes
    }

    private fun putReference(bytes: ByteArray, offset: Int, target: Int) {
        putU32(bytes, offset + 4, target)
    }

    private fun putAscii(bytes: ByteArray, offset: Int, value: String) {
        value.toByteArray(Charsets.US_ASCII).copyInto(bytes, offset)
    }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        bytes[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        bytes[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }
}
