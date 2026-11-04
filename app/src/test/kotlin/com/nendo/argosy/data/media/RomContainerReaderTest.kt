package com.nendo.argosy.data.media

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class RomContainerReaderTest {

    private lateinit var tempDir: File
    private val reader = RomContainerReader()

    @Before
    fun setup() {
        tempDir = createTempDirectory("rom_container_reader_test").toFile()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `extracts metadata and tiled icon from clear NCSD image`() {
        val file = writeFixture()

        val inspection = reader.inspect(file)
        val result = reader.readThreeDsSmdh(file, inspection)

        assertTrue(inspection.isValid)
        assertFalse(inspection.isEncrypted)
        assertTrue(result is SmdhParseResult.Success)
        val media = (result as SmdhParseResult.Success).media
        assertEquals("Fixture Game", media.title)
        assertEquals("Fixture Studio", media.publisher)
        assertEquals(48, media.iconWidth)
        assertEquals(48, media.iconHeight)
        assertEquals(0xFFFF0000.toInt(), media.iconArgb[0])
        assertEquals(0xFF00FF00.toInt(), media.iconArgb[1])
        assertEquals(0xFF0000FF.toInt(), media.iconArgb[48])
        assertEquals(0xFFFFFFFF.toInt(), media.iconArgb[8])
    }

    @Test
    fun `extracts metadata when NCSD image has trimmed trailing padding`() {
        val file = writeFixture(declaredImageSizeUnits = 0x200000L)

        val inspection = reader.inspect(file)
        val result = reader.readThreeDsSmdh(file, inspection)

        assertTrue(inspection.isValid)
        assertTrue(result is SmdhParseResult.Success)
    }

    @Test
    fun `rejects application partition truncated from NCSD image`() {
        val file = writeFixture().also { rom ->
            val image = rom.readBytes()
            putU32(image, 0x124, (image.size / 0x200).toLong())
            rom.writeBytes(image)
        }

        val inspection = reader.inspect(file)

        assertFalse(inspection.isValid)
        assertEquals("NCSD application partition is invalid", inspection.failureReason)
    }

    @Test
    fun `classifies encrypted NCCH without attempting extraction`() {
        val file = writeFixture(encrypted = true)

        val inspection = reader.inspect(file)
        val result = reader.readThreeDsSmdh(file, inspection)

        assertTrue(inspection.isValid)
        assertTrue(inspection.isEncrypted)
        assertTrue(result is SmdhParseResult.Failure)
        assertEquals("Encrypted NCCH content is unsupported", (result as SmdhParseResult.Failure).reason)
    }

    @Test
    fun `reports missing SMDH entry`() {
        val file = writeFixture(iconName = "banner")

        val result = reader.readThreeDsSmdh(file, reader.inspect(file))

        assertEquals(
            SmdhParseResult.Failure("ExeFS does not contain an SMDH icon"),
            result
        )
    }

    @Test
    fun `rejects invalid ExeFS icon range`() {
        val file = writeFixture(iconOffset = 0xFFFFFFFFL)

        val result = reader.readThreeDsSmdh(file, reader.inspect(file))

        assertEquals(
            SmdhParseResult.Failure("SMDH icon has an invalid size or offset"),
            result
        )
    }

    @Test
    fun `reads compressed 3DS banner CGFX from ExeFS`() {
        val file = writeFixture()
        val result = reader.readThreeDsBannerCgfx(file, reader.inspect(file))

        assertTrue(result is ThreeDsBannerResult.Success)
        assertEquals(
            "CGFX",
            String((result as ThreeDsBannerResult.Success).cgfx, 0, 4, Charsets.US_ASCII)
        )
    }

    @Test
    fun `reads bannertool generated CBMD fixture through NCSD ExeFS`() {
        val banner = requireNotNull(
            javaClass.getResourceAsStream(
                "/com/nendo/argosy/data/media/argosy-banner-fixture.bnr"
            )
        ).use { it.readBytes() }
        val file = writeFixture(bannerData = banner)

        val result = reader.readThreeDsBannerCgfx(file, reader.inspect(file))

        assertTrue(result.toString(), result is ThreeDsBannerResult.Success)
        val cgfx = (result as ThreeDsBannerResult.Success).cgfx
        assertTrue(cgfx.size > 0x14)
        assertEquals("CGFX", String(cgfx, 0, 4, Charsets.US_ASCII))
    }

    @Test
    fun `rejects truncated NCSD image`() {
        val file = File(tempDir, "truncated.3ds")
        file.writeBytes(ByteArray(0x200).also {
            putAscii(it, 0x100, "NCSD")
        })

        val inspection = reader.inspect(file)

        assertEquals(RomContainerReader.RomPlatform.THREEDS, inspection.platform)
        assertFalse(inspection.isValid)
    }

    @Test
    fun `does not classify CIA as NCSD or NDS`() {
        val file = File(tempDir, "unsupported.cia")
        file.writeBytes(ByteArray(0x1000).also {
            putAscii(it, 0, "CIA")
        })

        val inspection = reader.inspect(file)

        assertEquals(RomContainerReader.RomPlatform.UNKNOWN, inspection.platform)
        assertFalse(inspection.isValid)
    }

    @Test
    fun `rejects SMDH with invalid magic`() {
        val smdh = buildSmdh().also { it[0] = 0 }

        assertNull(SmdhParser.parse(smdh))
    }

    @Test
    fun `falls back to English title when preferred language is absent`() {
        val smdh = buildSmdh().also {
            it.fill(0, 0x8 + 0x200, 0x8 + 0x200 + 0x80)
            putUtf16(it, 0x8 + 0x200 + 0x80, "English Long Title")
            putUtf16(it, 0x8 + 0x200 + 0x180, "Fixture Studio")
        }

        val parsed = SmdhParser.parse(smdh, preferredLanguage = "fr-FR")

        assertNotNull(parsed)
        assertEquals("English Long Title", parsed?.title)
        assertEquals("Fixture Studio", parsed?.publisher)
    }

    private fun writeFixture(
        encrypted: Boolean = false,
        iconName: String = "icon",
        iconOffset: Long = 0L,
        bannerData: ByteArray = buildBanner(),
        declaredImageSizeUnits: Long? = null
    ): File {
        val bannerOffset = 0x4000
        val exefsSize = alignToMediaUnit(0x200 + bannerOffset + bannerData.size)
        val ncchSize = alignToMediaUnit(0x200 + exefsSize)
        val partitionSize = ncchSize
        val image = ByteArray(maxOf(0xC200, 0x200 + partitionSize))
        putAscii(image, 0x100, "NCSD")
        putU32(image, 0x104, declaredImageSizeUnits ?: (image.size / 0x200).toLong())
        putU32(image, 0x120, 1L)
        putU32(image, 0x124, (partitionSize / 0x200).toLong())

        val ncchOffset = 0x200
        putAscii(image, ncchOffset + 0x100, "NCCH")
        putU32(image, ncchOffset + 0x104, (ncchSize / 0x200).toLong())
        if (!encrypted) image[ncchOffset + 0x18F] = 0x04
        putU32(image, ncchOffset + 0x1A0, 1L)
        putU32(image, ncchOffset + 0x1A4, (exefsSize / 0x200).toLong())

        val exefsOffset = ncchOffset + 0x200
        putAscii(image, exefsOffset, iconName)
        putU32(image, exefsOffset + 8, iconOffset)
        putU32(image, exefsOffset + 12, 0x36C0L)
        buildSmdh().copyInto(image, exefsOffset + 0x200)
        putAscii(image, exefsOffset + 16, "banner")
        putU32(image, exefsOffset + 24, bannerOffset.toLong())
        putU32(image, exefsOffset + 28, bannerData.size.toLong())
        bannerData.copyInto(image, exefsOffset + 0x200 + bannerOffset)

        return File(tempDir, "fixture.3ds").apply { writeBytes(image) }
    }

    private fun alignToMediaUnit(size: Int): Int = (size + 0x1FF) and -0x200

    private fun buildBanner(): ByteArray {
        val banner = ByteArray(0x100)
        putAscii(banner, 0, "CBMD")
        putU32(banner, 8, 0x88L)
        byteArrayOf(0x11, 0x04, 0, 0, 0, 0x43, 0x47, 0x46, 0x58)
            .copyInto(banner, 0x88)
        return banner
    }

    private fun buildSmdh(): ByteArray {
        val smdh = ByteArray(0x36C0)
        putAscii(smdh, 0, "SMDH")
        val englishTitle = 0x8 + 0x200
        putUtf16(smdh, englishTitle, "Fixture Game")
        putUtf16(smdh, englishTitle + 0x180, "Fixture Studio")
        setIconPixel(smdh, 0, 0, 0xF800)
        setIconPixel(smdh, 1, 0, 0x07E0)
        setIconPixel(smdh, 0, 1, 0x001F)
        setIconPixel(smdh, 8, 0, 0xFFFF)
        return smdh
    }

    private fun setIconPixel(smdh: ByteArray, x: Int, y: Int, color: Int) {
        val morton = (x and 1) or
            ((y and 1) shl 1) or
            ((x and 2) shl 1) or
            ((y and 2) shl 2) or
            ((x and 4) shl 2) or
            ((y and 4) shl 3)
        val tileIndex = (((y shr 3) * (48 shr 3) + (x shr 3)) shl 6) + morton
        val iconOffset = 0x2040 + 0x480
        val pixelOffset = iconOffset + tileIndex * 2
        smdh[pixelOffset] = color.toByte()
        smdh[pixelOffset + 1] = (color shr 8).toByte()
    }

    private fun putAscii(bytes: ByteArray, offset: Int, value: String) {
        value.toByteArray(Charsets.US_ASCII).copyInto(bytes, offset)
    }

    private fun putUtf16(bytes: ByteArray, offset: Int, value: String) {
        value.toByteArray(Charsets.UTF_16LE).copyInto(bytes, offset)
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Long) {
        for (byteIndex in 0 until 4) {
            bytes[offset + byteIndex] = (value ushr (byteIndex * 8)).toByte()
        }
    }
}
