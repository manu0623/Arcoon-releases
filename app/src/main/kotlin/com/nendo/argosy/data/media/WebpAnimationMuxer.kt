package com.nendo.argosy.data.media

import java.io.ByteArrayOutputStream

/**
 * Builds an animated WebP out of still WebP frames (what Android's Bitmap.compress produces), so
 * a banner animation keeps full colour and smooth alpha instead of a 256-colour GIF palette.
 *
 * Follows the WebP container specification (RIFF "WEBP" with VP8X, ANIM and one ANMF chunk per
 * frame). Each still's image chunks (ALPH + VP8, or VP8L) are copied into its ANMF unchanged, so
 * no pixel is re-encoded here.
 */
internal class WebpAnimationMuxer(private val width: Int, private val height: Int) {

    private val frames = ByteArrayOutputStream()
    private var frameCount = 0
    private var hasAlpha = false

    /** Adds one full-canvas frame shown for [durationMs]. Returns false for a still it cannot read. */
    fun addFrame(stillWebp: ByteArray, durationMs: Int): Boolean {
        val image = imageChunks(stillWebp) ?: return false
        if (image.alpha) hasAlpha = true
        val payload = ByteArrayOutputStream()
        writeU24(payload, 0) // x / 2
        writeU24(payload, 0) // y / 2
        writeU24(payload, width - 1)
        writeU24(payload, height - 1)
        writeU24(payload, durationMs.coerceIn(1, 0xFFFFFF))
        // Each frame is a whole picture: replace the canvas (no blending), keep it until the next.
        payload.write(0x02)
        payload.write(image.bytes)
        writeChunk(frames, "ANMF", payload.toByteArray())
        frameCount++
        return true
    }

    fun build(loopCount: Int = 0): ByteArray? {
        if (frameCount == 0) return null
        val body = ByteArrayOutputStream()
        body.write("WEBP".toByteArray(Charsets.US_ASCII))
        val vp8x = ByteArrayOutputStream()
        vp8x.write(0x02 or (if (hasAlpha) 0x10 else 0)) // animation, alpha
        vp8x.write(0); vp8x.write(0); vp8x.write(0)
        writeU24(vp8x, width - 1)
        writeU24(vp8x, height - 1)
        writeChunk(body, "VP8X", vp8x.toByteArray())
        val anim = ByteArrayOutputStream()
        anim.write(0); anim.write(0); anim.write(0); anim.write(0) // transparent background
        anim.write(loopCount and 0xFF); anim.write((loopCount ushr 8) and 0xFF)
        writeChunk(body, "ANIM", anim.toByteArray())
        frames.writeTo(body)
        val out = ByteArrayOutputStream()
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        writeU32(out, body.size())
        body.writeTo(out)
        return out.toByteArray()
    }

    private class Image(val bytes: ByteArray, val alpha: Boolean)

    /** The ALPH / VP8 / VP8L chunks of a still WebP, exactly as stored. */
    private fun imageChunks(webp: ByteArray): Image? {
        if (webp.size < 20) return null
        if (String(webp, 0, 4, Charsets.US_ASCII) != "RIFF" || String(webp, 8, 4, Charsets.US_ASCII) != "WEBP") return null
        val out = ByteArrayOutputStream()
        var alpha = false
        var hasImage = false
        var at = 12
        while (at + 8 <= webp.size) {
            val tag = String(webp, at, 4, Charsets.US_ASCII)
            val size = readU32(webp, at + 4)
            val padded = size + (size and 1)
            if (size < 0 || at + 8 + size > webp.size) return null
            when (tag) {
                "ALPH" -> { alpha = true; out.write(webp, at, 8 + padded.coerceAtMost(webp.size - at - 8)) }
                "VP8 " -> { hasImage = true; out.write(webp, at, 8 + padded.coerceAtMost(webp.size - at - 8)) }
                "VP8L" -> {
                    hasImage = true
                    // The VP8L header carries an "alpha is used" bit.
                    if (size >= 5 && (webp[at + 8 + 4].toInt() and 0x10) != 0) alpha = true
                    out.write(webp, at, 8 + padded.coerceAtMost(webp.size - at - 8))
                }
            }
            at += 8 + padded
        }
        if (!hasImage) return null
        val bytes = out.toByteArray()
        // Chunks must stay even-sized; a still truncated without its pad byte gets one back.
        return Image(if (bytes.size % 2 == 0) bytes else bytes + 0, alpha)
    }

    private fun writeChunk(out: ByteArrayOutputStream, tag: String, payload: ByteArray) {
        out.write(tag.toByteArray(Charsets.US_ASCII))
        writeU32(out, payload.size)
        out.write(payload)
        if (payload.size % 2 == 1) out.write(0)
    }

    private fun writeU24(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF); out.write((value ushr 8) and 0xFF); out.write((value ushr 16) and 0xFF)
    }

    private fun writeU32(out: ByteArrayOutputStream, value: Int) {
        writeU24(out, value); out.write((value ushr 24) and 0xFF)
    }

    private fun readU32(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8) or
            ((b[at + 2].toInt() and 0xFF) shl 16) or ((b[at + 3].toInt() and 0xFF) shl 24)
}
