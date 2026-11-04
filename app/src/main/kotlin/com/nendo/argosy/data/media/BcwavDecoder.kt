package com.nendo.argosy.data.media

private const val CWAV_HEADER_SIZE = 0x40
private const val CWAV_REFERENCE_SIZE = 0x14
private const val CWAV_DATA_REFERENCE = 0x20
private const val CWAV_INFO_ENCODING = 0x08
private const val CWAV_INFO_SAMPLE_RATE = 0x0C
private const val CWAV_INFO_CHANNEL_TABLE = 0x1C
private const val DSP_COEFFICIENT_BYTES = 0x20
private const val DSP_CONTEXT = 0x20
private const val DSP_FRAME_BYTES = 8
private const val DSP_FRAME_SAMPLES = 14
private const val RIFF_HEADER_SIZE = 44
private const val MAX_DECODED_SAMPLES = 4_000_000

private const val ENCODING_PCM8 = 0
private const val ENCODING_PCM16 = 1
private const val ENCODING_DSP_ADPCM = 2
private const val ENCODING_IMA_ADPCM = 3

sealed interface BcwavResult {
    data class Success(
        val wav: ByteArray,
        val sampleRate: Int,
        val channelCount: Int,
        val frameCount: Int
    ) : BcwavResult

    data class Failure(val reason: String) : BcwavResult
}

/**
 * Decodes a BCWAV (CWAV) resource into a 16-bit PCM RIFF/WAVE file.
 *
 * Field positions are 3dbrew's BCWAV layout, not inferred: sized references at 0x14/0x20, the INFO
 * block's encoding and sample rate at 0x08/0x0C, a channel-info reference table at 0x1C whose entry
 * offsets are relative to the table's count field, and a DATA block whose data field begins 8 bytes
 * in. A channel entry's samples reference is an offset from that data field while its ADPCM info
 * reference is relative to the samples reference field itself, which is why the two are resolved
 * against different bases. DSP ADPCM uses the usual Nintendo framing: 8-byte frames of one header
 * byte plus fourteen 4-bit samples, high nibble first, with the coefficients and initial history
 * taken from the referenced 0x0300 block.
 */
object BcwavDecoder {

    fun toWav(cwav: ByteArray): BcwavResult {
        if (cwav.size < CWAV_HEADER_SIZE) return BcwavResult.Failure("CWAV header is truncated")
        if (!hasAscii(cwav, 0, "CWAV")) return BcwavResult.Failure("Resource is not a CWAV")
        if (readU16(cwav, 0x04) != 0xFEFF) return BcwavResult.Failure("CWAV is not little-endian")

        val infoOffset = referenceOffset(cwav, CWAV_REFERENCE_SIZE)
            ?: return BcwavResult.Failure("CWAV has no INFO block")
        val dataOffset = referenceOffset(cwav, CWAV_DATA_REFERENCE)
            ?: return BcwavResult.Failure("CWAV has no DATA block")
        if (!hasAscii(cwav, infoOffset, "INFO")) return BcwavResult.Failure("CWAV INFO block is missing")
        if (!hasAscii(cwav, dataOffset, "DATA")) return BcwavResult.Failure("CWAV DATA block is missing")

        val encoding = cwav[infoOffset + CWAV_INFO_ENCODING].toInt() and 0xFF
        val sampleRate = readU32(cwav, infoOffset + CWAV_INFO_SAMPLE_RATE)
        if (sampleRate !in 4_000..192_000) return BcwavResult.Failure("CWAV sample rate is invalid")

        val channelTable = infoOffset + CWAV_INFO_CHANNEL_TABLE
        if (channelTable + 4 > cwav.size) return BcwavResult.Failure("CWAV channel table is truncated")
        val channelCount = readU32(cwav, channelTable)
        if (channelCount !in 1..8) return BcwavResult.Failure("CWAV channel count is invalid")

        val dataField = dataOffset + 8
        if (dataOffset + 8 > cwav.size) return BcwavResult.Failure("CWAV DATA block is truncated")
        val dataSize = readU32(cwav, dataOffset + 4) - 8
        if (dataSize <= 0) return BcwavResult.Failure("CWAV DATA block holds no samples")

        val decoded = ArrayList<IntArray>(channelCount)
        for (channel in 0 until channelCount) {
            // The table holds a reference per channel, and the Channel Info entry it resolves to
            // holds the samples and ADPCM references this decode needs.
            val reference = channelTable + 4 + channel * 8
            if (reference + 8 > cwav.size) return BcwavResult.Failure("CWAV channel reference is truncated")
            val channelInfo = (channelTable + readU32(cwav, reference + 4)).toLong()
            if (channelInfo < 0 || channelInfo + 12 > cwav.size) {
                return BcwavResult.Failure("CWAV channel info is outside the INFO block")
            }
            val info = channelInfo.toInt()
            val samplesOffset = readU32(cwav, info + 4).toLong()
            if (samplesOffset < 0 || samplesOffset >= dataSize) {
                return BcwavResult.Failure("CWAV channel samples lie outside the DATA block")
            }
            val start = dataField + samplesOffset.toInt()
            val available = minOf(dataSize - samplesOffset.toInt(), cwav.size - start)
            if (available <= 0) return BcwavResult.Failure("CWAV channel samples are truncated")

            val adpcmOffset = (channelInfo + readU32(cwav, info + 12)).toLong()
            val channelSamples = when (encoding) {
                ENCODING_PCM8 -> decodePcm8(cwav, start, available)
                ENCODING_PCM16 -> decodePcm16(cwav, start, available)
                ENCODING_DSP_ADPCM -> decodeDspAdpcm(cwav, start, available, adpcmOffset)
                ENCODING_IMA_ADPCM -> return BcwavResult.Failure("CWAV IMA ADPCM is unsupported")
                else -> return BcwavResult.Failure("CWAV encoding $encoding is unsupported")
            } ?: return BcwavResult.Failure("CWAV DSP ADPCM data is malformed")
            if (channelSamples.isEmpty()) return BcwavResult.Failure("CWAV channel holds no samples")
            decoded.add(channelSamples)
        }

        val frameCount = decoded.minOf { it.size }.coerceAtMost(MAX_DECODED_SAMPLES)
        if (frameCount <= 0) return BcwavResult.Failure("CWAV holds no samples")
        val wav = writeWav(decoded, frameCount, sampleRate)
        return BcwavResult.Success(
            wav = wav,
            sampleRate = sampleRate,
            channelCount = decoded.size,
            frameCount = frameCount
        )
    }

    private fun decodePcm8(bytes: ByteArray, start: Int, available: Int): IntArray {
        val count = available.coerceAtMost(MAX_DECODED_SAMPLES)
        val samples = IntArray(count)
        for (index in 0 until count) {
            samples[index] = (bytes[start + index].toInt() shl 8)
        }
        return samples
    }

    private fun decodePcm16(bytes: ByteArray, start: Int, available: Int): IntArray {
        val count = (available / 2).coerceAtMost(MAX_DECODED_SAMPLES)
        val samples = IntArray(count)
        for (index in 0 until count) {
            samples[index] = readS16(bytes, start + index * 2)
        }
        return samples
    }

    private fun decodeDspAdpcm(bytes: ByteArray, start: Int, available: Int, infoOffset: Long): IntArray? {
        if (infoOffset < 0 || infoOffset + DSP_COEFFICIENT_BYTES + DSP_CONTEXT > bytes.size) return null
        val info = infoOffset.toInt()
        val coefficients = IntArray(16) { readS16(bytes, info + it * 2) }
        val context = info + DSP_COEFFICIENT_BYTES
        var history = readS16(bytes, context + 2)
        var previous = readS16(bytes, context + 4)

        val frameCount = (available / DSP_FRAME_BYTES).coerceAtMost(MAX_DECODED_SAMPLES / DSP_FRAME_SAMPLES)
        val samples = IntArray(frameCount * DSP_FRAME_SAMPLES)
        var written = 0
        for (frame in 0 until frameCount) {
            val base = start + frame * DSP_FRAME_BYTES
            val header = bytes[base].toInt() and 0xFF
            val predictor = header ushr 4
            val scale = header and 0x0F
            if (predictor >= 8) return null
            val coefficient0 = coefficients[predictor * 2]
            val coefficient1 = coefficients[predictor * 2 + 1]
            for (index in 0 until DSP_FRAME_SAMPLES) {
                val packed = bytes[base + 1 + index / 2].toInt()
                val nibble = if (index % 2 == 0) (packed ushr 4) and 0x0F else packed and 0x0F
                val signed = if (nibble >= 8) nibble - 16 else nibble
                var sample = (((signed shl scale) shl 11) + coefficient0 * history + coefficient1 * previous) shr 11
                sample = sample.coerceIn(-32_768, 32_767)
                previous = history
                history = sample
                samples[written++] = sample
            }
        }
        return samples
    }

    private fun writeWav(channels: List<IntArray>, frameCount: Int, sampleRate: Int): ByteArray {
        val channelCount = channels.size
        val dataBytes = frameCount * channelCount * 2
        val out = ByteArray(RIFF_HEADER_SIZE + dataBytes)
        putAscii(out, 0, "RIFF")
        putU32(out, 4, 36 + dataBytes)
        putAscii(out, 8, "WAVE")
        putAscii(out, 12, "fmt ")
        putU32(out, 16, 16)
        putU16(out, 20, 1)
        putU16(out, 22, channelCount)
        putU32(out, 24, sampleRate)
        putU32(out, 28, sampleRate * channelCount * 2)
        putU16(out, 32, channelCount * 2)
        putU16(out, 34, 16)
        putAscii(out, 36, "data")
        putU32(out, 40, dataBytes)
        var offset = RIFF_HEADER_SIZE
        for (frame in 0 until frameCount) {
            for (channel in channels) {
                val sample = channel[frame]
                out[offset++] = (sample and 0xFF).toByte()
                out[offset++] = ((sample shr 8) and 0xFF).toByte()
            }
        }
        return out
    }

    private fun referenceOffset(bytes: ByteArray, position: Int): Int? {
        if (position + 8 > bytes.size) return null
        val offset = readU32(bytes, position + 4)
        if (offset == -1) return null
        if (offset < 0 || offset >= bytes.size) return null
        return offset
    }

    private fun hasAscii(bytes: ByteArray, offset: Int, text: String): Boolean {
        if (offset < 0 || offset + text.length > bytes.size) return false
        for (index in text.indices) {
            if (bytes[offset + index] != text[index].code.toByte()) return false
        }
        return true
    }

    private fun readU16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun readS16(bytes: ByteArray, offset: Int): Int = readU16(bytes, offset).toShort().toInt()

    private fun readU32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

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
