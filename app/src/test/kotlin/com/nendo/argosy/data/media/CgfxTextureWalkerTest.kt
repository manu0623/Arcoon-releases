package com.nendo.argosy.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.Deflater

class CgfxTextureWalkerTest {

    @Test
    fun `walks DATA and DICT to a validated RGBA8 texture`() {
        val cgfx = buildSyntheticCgfx()

        val result = CgfxTextureWalker.walk(cgfx)

        assertTrue(result.toString(), result is CgfxWalkResult.Success)
        val success = result as CgfxWalkResult.Success
        assertEquals(1, success.declaredTextureCount)
        assertEquals(1, success.textures.size)
        val texture = success.textures.single()
        assertEquals(8, texture.width)
        assertEquals(8, texture.height)
        assertEquals(CgfxTextureDecoder.FORMAT_RGBA8, texture.format)
        assertEquals(64 * 4, texture.dataSize)
        assertEquals(TEXTURE_DATA_OFFSET, texture.dataOffset)
        assertEquals("magic-first", texture.layout)
    }

    @Test
    fun `decodes RGBA8 in tile order`() {
        val cgfx = buildSyntheticCgfx()
        val texture = (CgfxTextureWalker.walk(cgfx) as CgfxWalkResult.Success).textures.single()

        val argb = CgfxTextureDecoder.decode(cgfx, texture)

        assertTrue("decode failed", argb != null)
        // Pixel (3, 2) lives at tile slot 13, which is where the builder wrote 0x10 0x20 0x30 0x40.
        assertEquals(0x40102030, argb!![2 * 8 + 3])
        // Pixel (0, 0) is slot 0: red = slot index, opaque.
        assertEquals(0xFF000000.toInt(), argb[0])
    }

    @Test
    fun `rejects a texture whose declared size disagrees with its format`() {
        val cgfx = buildSyntheticCgfx(dataSize = 128)

        val result = CgfxTextureWalker.walk(cgfx)

        assertTrue(result.toString(), result is CgfxWalkResult.Success)
        val success = result as CgfxWalkResult.Success
        assertEquals(0, success.textures.size)
        assertTrue(success.rejected.isNotEmpty())
    }

    @Test
    fun `renders the validated texture`() {
        val result = ThreeDsBannerRenderer.render(buildSyntheticCgfx())

        assertTrue(result.toString(), result is ThreeDsBannerRenderResult.Success)
        val image = result as ThreeDsBannerRenderResult.Success
        assertEquals(8, image.width)
        assertEquals(8, image.height)
        assertEquals(0x40102030, image.argb[2 * 8 + 3])
    }

    /**
     * Reads banner CGFX files an on-device extraction already produced, when they have been copied
     * to `build/tmp/cgfx-probe`. They are never part of the repository, so the test reports and
     * returns rather than failing when the folder is absent.
     */
    @Test
    fun `reports on pulled retail banners`() {
        val directory = File("build/tmp/cgfx-probe")
        assumeTrue("no probe fixtures in ${directory.absolutePath}", directory.isDirectory)
        val files = directory.listFiles { file -> file.extension == "cgfx" }.orEmpty().sortedBy { it.name }
        assumeTrue("no .cgfx files in ${directory.absolutePath}", files.isNotEmpty())
        val outDirectory = File(directory, "out").apply { mkdirs() }

        for (file in files) {
            val bytes = file.readBytes()
            val walk = CgfxTextureWalker.walk(bytes)
            println("CGFXPROBE ${file.name} size=${bytes.size} -> ${describe(walk)}")
            if (walk is CgfxWalkResult.Success) {
                walk.rejected.forEach { println("CGFXPROBE   rejected: $it") }
                walk.textures.forEachIndexed { index, texture ->
                    val argb = CgfxTextureDecoder.decode(bytes, texture)
                    val distinct = argb?.let { countDistinctColours(it) } ?: -1
                    println(
                        "CGFXPROBE   texture ${texture.width}x${texture.height} " +
                            "${CgfxTextureDecoder.formatName(texture.format)} " +
                            "mips=${texture.mipCount} bytes=${texture.dataSize} " +
                            "at=0x${texture.dataOffset.toString(16)} layout=${texture.layout} " +
                            "distinctColours=$distinct"
                    )
                    if (argb != null) {
                        writePng(
                            File(
                                outDirectory,
                                "${file.nameWithoutExtension}-tex$index-" +
                                    "${texture.width}x${texture.height}-" +
                                    "${CgfxTextureDecoder.formatName(texture.format)}.png"
                            ),
                            texture.width,
                            texture.height,
                            argb,
                        )
                    }
                }
            }
            val render = ThreeDsBannerRenderer.render(bytes)
            val outcome = when (render) {
                is ThreeDsBannerRenderResult.Success ->
                    "${render.width}x${render.height} distinctColours=${countDistinctColours(render.argb)}"
                is ThreeDsBannerRenderResult.Failure -> "FAILED ${render.reason}"
            }
            println("CGFXPROBE   render -> $outcome")
            if (render is ThreeDsBannerRenderResult.Success) {
                writePng(
                    File(outDirectory, "${file.nameWithoutExtension}-render.png"),
                    render.width,
                    render.height,
                    render.argb,
                )
            }
        }
    }

    private fun describe(result: CgfxWalkResult): String = when (result) {
        is CgfxWalkResult.Success ->
            "textures=${result.textures.size} declared=${result.declaredTextureCount} txob=${result.txobCount}"
        is CgfxWalkResult.Failure -> "FAILED ${result.reason}"
    }

    /** Minimal RGB PNG writer so the probe output can be looked at without an Android Bitmap. */
    private fun writePng(file: File, width: Int, height: Int, argb: IntArray) {
        val raw = ByteArray((width * 3 + 1) * height)
        var cursor = 0
        for (y in 0 until height) {
            raw[cursor++] = 0
            for (x in 0 until width) {
                val colour = argb[y * width + x]
                raw[cursor++] = ((colour shr 16) and 0xFF).toByte()
                raw[cursor++] = ((colour shr 8) and 0xFF).toByte()
                raw[cursor++] = (colour and 0xFF).toByte()
            }
        }
        val deflater = Deflater(Deflater.BEST_SPEED)
        deflater.setInput(raw)
        deflater.finish()
        val compressed = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (!deflater.finished()) {
            compressed.write(buffer, 0, deflater.deflate(buffer))
        }
        deflater.end()

        val header = ByteArrayOutputStream().apply {
            write(byteArrayOf(0, 0, 0, 0)) // width, patched below
            write(byteArrayOf(0, 0, 0, 0)) // height, patched below
            write(byteArrayOf(8, 2, 0, 0, 0)) // 8-bit RGB, deflate, no filter, no interlace
        }
        val headerBytes = header.toByteArray()
        putIntBigEndian(headerBytes, 0, width)
        putIntBigEndian(headerBytes, 4, height)

        file.outputStream().use { out ->
            out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
            writePngChunk(out, "IHDR", headerBytes)
            writePngChunk(out, "IDAT", compressed.toByteArray())
            writePngChunk(out, "IEND", ByteArray(0))
        }
    }

    private fun writePngChunk(out: java.io.OutputStream, type: String, data: ByteArray) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val length = ByteArray(4).also { putIntBigEndian(it, 0, data.size) }
        out.write(length)
        out.write(typeBytes)
        out.write(data)
        val crc = CRC32().apply {
            update(typeBytes)
            update(data)
        }
        out.write(ByteArray(4).also { putIntBigEndian(it, 0, crc.value.toInt()) })
    }

    private fun putIntBigEndian(target: ByteArray, offset: Int, value: Int) {
        target[offset] = ((value ushr 24) and 0xFF).toByte()
        target[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        target[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        target[offset + 3] = (value and 0xFF).toByte()
    }

    private fun countDistinctColours(argb: IntArray): Int {
        val seen = HashSet<Int>()
        for (pixel in argb) {
            seen.add(pixel)
            if (seen.size > 512) return seen.size
        }
        return seen.size
    }

    private fun buildSyntheticCgfx(dataSize: Int = 64 * 4): ByteArray {
        val bytes = ByteArray(TEXTURE_DATA_OFFSET + TEXTURE_DATA_SIZE)
        "CGFX".toByteArray(Charsets.US_ASCII).copyInto(bytes, 0x00)
        bytes[0x04] = 0xFF.toByte()
        bytes[0x05] = 0xFE.toByte()
        putU16(bytes, 0x06, 0x14)
        putU32(bytes, 0x0C, bytes.size)
        putU32(bytes, 0x10, 2)

        "DATA".toByteArray(Charsets.US_ASCII).copyInto(bytes, DATA_SECTION)
        putU32(bytes, DATA_SECTION + 0x04, bytes.size - DATA_SECTION)
        // DICT index 1 is the texture table: its count sits at +0x10 and its self-relative
        // pointer at +0x14, which is the field pair the walker reads.
        putU32(bytes, DATA_SECTION + 0x10, 1)
        putU32(bytes, DATA_SECTION + 0x14, DICT_OFFSET - (DATA_SECTION + 0x14))

        "DICT".toByteArray(Charsets.US_ASCII).copyInto(bytes, DICT_OFFSET)
        putU32(bytes, DICT_OFFSET + 0x04, 0x2C)
        putU32(bytes, DICT_OFFSET + 0x08, 1)

        val entry = DICT_OFFSET + 0x1C
        putU32(bytes, entry + 0x08, 0)
        putU32(bytes, entry + 0x0C, TXOB_OFFSET - (entry + 0x0C))

        "TXOB".toByteArray(Charsets.US_ASCII).copyInto(bytes, TXOB_OFFSET)
        putU32(bytes, TXOB_OFFSET + 0x14, 8)
        putU32(bytes, TXOB_OFFSET + 0x18, 8)
        putU32(bytes, TXOB_OFFSET + 0x24, 1)
        putU32(bytes, TXOB_OFFSET + 0x30, CgfxTextureDecoder.FORMAT_RGBA8)
        putU32(bytes, TXOB_OFFSET + 0x40, dataSize)
        putU32(bytes, TXOB_OFFSET + 0x44, TEXTURE_DATA_OFFSET - (TXOB_OFFSET + 0x44))

        for (slot in 0 until 64) {
            val base = TEXTURE_DATA_OFFSET + slot * 4
            bytes[base] = slot.toByte()
            bytes[base + 3] = 0xFF.toByte()
        }
        val highlighted = TEXTURE_DATA_OFFSET + 13 * 4
        bytes[highlighted] = 0x10
        bytes[highlighted + 1] = 0x20
        bytes[highlighted + 2] = 0x30
        bytes[highlighted + 3] = 0x40
        return bytes
    }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Int) {
        for (byteIndex in 0 until 4) {
            bytes[offset + byteIndex] = ((value ushr (byteIndex * 8)) and 0xFF).toByte()
        }
    }

    private companion object {
        const val DATA_SECTION = 0x14
        const val DICT_OFFSET = 0x40
        const val TXOB_OFFSET = 0x6C
        const val TEXTURE_DATA_OFFSET = 0xCC
        const val TEXTURE_DATA_SIZE = 64 * 4
    }
}
