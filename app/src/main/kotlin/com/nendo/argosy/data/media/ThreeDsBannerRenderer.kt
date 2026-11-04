package com.nendo.argosy.data.media

private const val BANNER_IMAGE_WIDTH = 256
private const val BANNER_IMAGE_HEIGHT = 128
private const val BANNER_IMAGE_BYTE_SIZE = BANNER_IMAGE_WIDTH * BANNER_IMAGE_HEIGHT * 2

/** 70 ms a frame: a 24-frame turn lasts about 1.7 s, the Home Menu's own resting speed. */
private const val GIF_FRAME_DELAY_CENTISECONDS = 7

sealed interface ThreeDsBannerRenderResult {
    data class Success(val width: Int, val height: Int, val argb: IntArray) : ThreeDsBannerRenderResult
    data class Failure(val reason: String) : ThreeDsBannerRenderResult
}

/**
 * Renders a 3DS banner out of a decompressed CGFX container.
 *
 * Scene banners are composited by [CgfxBannerScene] through the banner's own camera. Only when a
 * container holds no renderable scene does this fall back to a single texture: the one
 * [CgfxTextureWalker] proved by arithmetic, where the container's own DATA/DICT table names it and
 * its declared byte count matches what its format and dimensions require. The fixed-offset scan
 * below only runs for a container the walker cannot read at all, which is the bannertool
 * image-banner template; a container that does have texture objects never reaches it, so unrelated
 * bytes can no longer be rendered as a banner.
 */
object ThreeDsBannerRenderer {

    fun render(cgfx: ByteArray): ThreeDsBannerRenderResult {
        if (cgfx.size < 64 || !hasCgfxMagic(cgfx)) {
            return ThreeDsBannerRenderResult.Failure("CGFX magic missing or truncated")
        }

        // A real banner is a 3D scene; compositing it is what produces the Home Menu look, and it
        // must run before any single texture is considered.
        CgfxBannerScene.render(cgfx)?.let { return it }

        val textures = (CgfxTextureWalker.walk(cgfx) as? CgfxWalkResult.Success)?.textures.orEmpty()
        if (textures.isEmpty()) {
            return renderImageBannerTemplate(cgfx)
                ?: ThreeDsBannerRenderResult.Failure(
                    "CGFX does not match the supported 3DS image-banner template"
                )
        }

        val candidates = textures
            .filter { CgfxTextureDecoder.isDecodable(it.format) }
            .sortedByDescending { it.width.toLong() * it.height.toLong() }
        for (texture in candidates) {
            val argb = CgfxTextureDecoder.decode(cgfx, texture) ?: continue
            if (hasContent(argb)) {
                return ThreeDsBannerRenderResult.Success(
                    width = texture.width,
                    height = texture.height,
                    argb = argb,
                )
            }
        }

        val formats = textures.joinToString { CgfxTextureDecoder.formatName(it.format) }
        return ThreeDsBannerRenderResult.Failure(
            "CGFX holds ${textures.size} banner textures, none renderable: $formats"
        )
    }

    /**
     * The banner's scene as one full turn, encoded as an animated GIF. The launcher's image loader
     * already decodes animated GIFs, so the hero turns without any playback code of its own.
     */
    fun renderRotationGif(cgfx: ByteArray): ByteArray? {
        val rotation = CgfxBannerScene.renderRotation(cgfx) ?: return null
        return GifEncoder.encode(
            frames = rotation.frames,
            width = rotation.width,
            height = rotation.height,
            delayCentiseconds = GIF_FRAME_DELAY_CENTISECONDS,
        )
    }

    /** The bannertool image-banner layout: a 256x128 block at a fixed offset. */
    private fun renderImageBannerTemplate(cgfx: ByteArray): ThreeDsBannerRenderResult.Success? {
        val candidateOffsets = mutableListOf<Int>()
        if (cgfx.size >= 0x1580 + BANNER_IMAGE_BYTE_SIZE) candidateOffsets.add(0x1580)
        if (cgfx.size >= BANNER_IMAGE_BYTE_SIZE) candidateOffsets.add(cgfx.size - BANNER_IMAGE_BYTE_SIZE)

        for (offset in candidateOffsets.distinct()) {
            if (offset < 0 || offset + BANNER_IMAGE_BYTE_SIZE > cgfx.size) continue
            val texture = cgfx.copyOfRange(offset, offset + BANNER_IMAGE_BYTE_SIZE)

            val asRgba4 = TiledPixelDecoder.decodeRgba4444(texture, BANNER_IMAGE_WIDTH, BANNER_IMAGE_HEIGHT)
            if (asRgba4 != null && hasContent(asRgba4)) {
                return ThreeDsBannerRenderResult.Success(BANNER_IMAGE_WIDTH, BANNER_IMAGE_HEIGHT, asRgba4)
            }
            val asRgb565 = TiledPixelDecoder.decodeRgb565(texture, BANNER_IMAGE_WIDTH, BANNER_IMAGE_HEIGHT)
            if (asRgb565 != null && hasContent(asRgb565)) {
                return ThreeDsBannerRenderResult.Success(BANNER_IMAGE_WIDTH, BANNER_IMAGE_HEIGHT, asRgb565)
            }
        }
        return null
    }

    /**
     * A flat run of identical pixels is not an image. This is a content check, not the acceptance
     * test: whether the bytes are a texture at all is decided by the walker's size arithmetic.
     */
    private fun hasContent(argb: IntArray): Boolean {
        if (argb.size < 2) return false
        val first = argb[0]
        var differing = 0
        for (pixel in argb) {
            if (pixel != first) differing++
        }
        return differing >= argb.size / 20
    }

    private fun hasCgfxMagic(cgfx: ByteArray): Boolean {
        if (cgfx.size < 4) return false
        val magic = "CGFX".toByteArray(Charsets.US_ASCII)
        for (index in magic.indices) {
            if (cgfx[index] != magic[index]) return false
        }
        return true
    }
}
