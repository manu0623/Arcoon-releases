package com.nendo.argosy.data.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BcwavDecoderTest {

    @Test
    fun `decodes pcm16 samples into a riff wave file`() {
        val cwav = buildCwav(
            encoding = 1,
            sampleRate = 22_050,
            samples = shortArrayOf(0, 1_000, -1_000, 32_767, -32_768)
        )

        val result = BcwavDecoder.toWav(cwav)

        assertTrue(result.toString(), result is BcwavResult.Success)
        val success = result as BcwavResult.Success
        assertEquals("RIFF", String(success.wav, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(success.wav, 8, 4, Charsets.US_ASCII))
        assertEquals(22_050, success.sampleRate)
        assertEquals(1, success.channelCount)
        assertEquals(5, success.frameCount)
        assertEquals(44 + 10, success.wav.size)
        assertArrayEquals(
            shortArrayOf(0, 1_000, -1_000, 32_767, -32_768),
            samplesOf(success.wav)
        )
    }

    @Test
    fun `decodes dsp adpcm nibbles with the nintendo frame framing`() {
        // Zero coefficients leave each sample as the sign-extended nibble, which pins the framing:
        // 0x0, 0x1, 0x8 (= -8), 0x7 in the high nibbles, then the same in the low nibbles.
        val frame = byteArrayOf(
            0x00,
            0x01, 0x87.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00
        )
        val cwav = buildDspCwav(frame)

        val result = BcwavDecoder.toWav(cwav)

        assertTrue(result.toString(), result is BcwavResult.Success)
        val samples = samplesOf((result as BcwavResult.Success).wav)
        assertEquals(14, samples.size)
        assertEquals(0, samples[0].toInt())
        assertEquals(1, samples[1].toInt())
        assertEquals(-8, samples[2].toInt())
        assertEquals(7, samples[3].toInt())
    }

    @Test
    fun `rejects IMA adpcm instead of emitting silence`() {
        val result = BcwavDecoder.toWav(buildCwav(encoding = 3, sampleRate = 8_000, samples = shortArrayOf(0)))

        assertTrue(result is BcwavResult.Failure)
        assertEquals("CWAV IMA ADPCM is unsupported", (result as BcwavResult.Failure).reason)
    }

    @Test
    fun `rejects a resource that is not a cwav`() {
        val result = BcwavDecoder.toWav(ByteArray(0x80))

        assertTrue(result is BcwavResult.Failure)
        assertEquals("Resource is not a CWAV", (result as BcwavResult.Failure).reason)
    }

    @Test
    fun `rejects a truncated header`() {
        val result = BcwavDecoder.toWav(ByteArray(8))

        assertTrue(result is BcwavResult.Failure)
        assertEquals("CWAV header is truncated", (result as BcwavResult.Failure).reason)
    }

    private fun samplesOf(wav: ByteArray): ShortArray {
        val count = (wav.size - 44) / 2
        return ShortArray(count) { index ->
            val offset = 44 + index * 2
            ((wav[offset].toInt() and 0xFF) or ((wav[offset + 1].toInt() and 0xFF) shl 8)).toShort()
        }
    }

    /** A one-channel little-endian CWAV whose INFO and DATA blocks follow 3dbrew's layout. */
    private fun buildCwav(encoding: Int, sampleRate: Int, samples: ShortArray): ByteArray {
        val sampleBytes = ByteArray(samples.size * 2)
        samples.forEachIndexed { index, value ->
            sampleBytes[index * 2] = (value.toInt() and 0xFF).toByte()
            sampleBytes[index * 2 + 1] = ((value.toInt() shr 8) and 0xFF).toByte()
        }
        return assembleCwav(encoding, sampleRate, sampleBytes)
    }

    private fun buildDspCwav(frame: ByteArray): ByteArray = assembleCwav(2, 32_000, frame)

    private fun assembleCwav(encoding: Int, sampleRate: Int, sampleBytes: ByteArray): ByteArray {
        val infoOffset = 0x40
        val channelTable = infoOffset + 0x1C
        val channelEntry = channelTable + 0x24
        val adpcmInfo = channelEntry + 0x20
        val dataOffset = (adpcmInfo + 0x40 + 0x1F) and 0x1F.inv()
        val dataField = dataOffset + 8
        val total = dataField + sampleBytes.size + 8
        val bytes = ByteArray(total)

        putAscii(bytes, 0, "CWAV")
        putU16(bytes, 0x04, 0xFEFF)
        putU16(bytes, 0x06, 0x40)
        putU32(bytes, 0x08, 0x02010000)
        putU32(bytes, 0x0C, total)
        putU16(bytes, 0x10, 2)
        putReference(bytes, 0x14, 0x7000, infoOffset)
        putReference(bytes, 0x20, 0x7001, dataOffset)

        putAscii(bytes, infoOffset, "INFO")
        putU32(bytes, infoOffset + 0x04, 0x40)
        bytes[infoOffset + 0x08] = encoding.toByte()
        bytes[infoOffset + 0x09] = 0
        putU32(bytes, infoOffset + 0x0C, sampleRate)
        putU32(bytes, infoOffset + 0x10, 0)
        putU32(bytes, infoOffset + 0x14, if (encoding == 2) sampleBytes.size / 8 * 14 else sampleBytes.size / 2)
        putU32(bytes, infoOffset + 0x18, 0)

        putU32(bytes, channelTable, 1)
        putReference(bytes, channelTable + 0x04, 0x7100, channelEntry - channelTable)

        putReference(bytes, channelEntry, 0x1F00, 0)
        putReference(bytes, channelEntry + 0x08, if (encoding == 2) 0x0300 else 0x0300, adpcmInfo - channelEntry)

        putAscii(bytes, dataOffset, "DATA")
        putU32(bytes, dataOffset + 0x04, 8 + sampleBytes.size)
        sampleBytes.copyInto(bytes, dataField)
        return bytes
    }

    private fun putReference(bytes: ByteArray, offset: Int, type: Int, target: Int) {
        putU16(bytes, offset, type)
        putU16(bytes, offset + 2, 0)
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
