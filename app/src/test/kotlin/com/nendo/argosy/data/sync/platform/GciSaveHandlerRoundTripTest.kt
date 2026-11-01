package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.fixtures.SaveFixtures
import com.nendo.argosy.data.sync.fixtures.realFsFal
import com.nendo.argosy.data.storage.AndroidDataAccessor
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class GciSaveHandlerRoundTripTest {

    private lateinit var tempDir: File
    private lateinit var baseDir: File
    private lateinit var romFile: File
    private lateinit var handler: GciSaveHandler

    private val androidDataAccessor = mockk<AndroidDataAccessor>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    private val gameId = "GZLE"
    private val makerCode = "01"

    @Before
    fun setUp() {
        tempDir = createTempDirectory("gci_roundtrip").toFile()
        baseDir = File(tempDir, "memcards").apply { mkdirs() }
        romFile = SaveFixtures.gameCubeRom(File(tempDir, "rom.iso"), gameId = gameId, makerCode = makerCode)
        every { context.cacheDir } returns File(tempDir, "cache").apply { mkdirs() }
        val fal = realFsFal()
        val archiver = SaveArchiver(androidDataAccessor, fal)
        handler = GciSaveHandler(context, fal, archiver)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `multi-gci bundle round-trips into Region Card layout`() = runTest {
        val cardDir = File(baseDir, "USA/Card A").apply { mkdirs() }
        val saveA = SaveFixtures.gciSave(
            File(cardDir, "$makerCode-$gameId-ZELDA_DATA.gci"),
            gameId = gameId,
            makerCode = makerCode,
            internalFilename = "ZELDA_DATA",
            payload = ByteArray(1024) { it.toByte() },
        )
        val saveB = SaveFixtures.gciSave(
            File(cardDir, "$makerCode-$gameId-ZELDA_BACK.gci"),
            gameId = gameId,
            makerCode = makerCode,
            internalFilename = "ZELDA_BACK",
            payload = ByteArray(512) { (it * 3).toByte() },
        )
        val originalA = saveA.readBytes()
        val originalB = saveB.readBytes()

        val ctx = saveContext()
        val prepared = handler.prepareForUpload(saveA.absolutePath, ctx)
            ?: error("prepareForUpload returned null")
        assertTrue("Bundle file should exist", prepared.file.exists())
        assertTrue(prepared.isTemporary)

        saveA.delete()
        saveB.delete()
        assertTrue("Cleared source GCIs before extract", baseDir.walkTopDown().none { it.isFile && it.extension.equals("gci", ignoreCase = true) })

        val result = handler.extractDownload(prepared.file, ctx)
        assertTrue("Extract reported failure: ${result.error}", result.success)

        val restoredA = File(cardDir, "$makerCode-$gameId-ZELDA_DATA.gci")
        val restoredB = File(cardDir, "$makerCode-$gameId-ZELDA_BACK.gci")
        assertTrue("Save A missing after extract", restoredA.exists())
        assertTrue("Save B missing after extract", restoredB.exists())
        assertEquals(originalA.toList(), restoredA.readBytes().toList())
        assertEquals(originalB.toList(), restoredB.readBytes().toList())
    }

    @Test
    fun `a bundle download leaves this game's saves in another card folder alone`() = runTest {
        val cardA = File(baseDir, "USA/Card A").apply { mkdirs() }
        val cardB = File(baseDir, "USA/Card B").apply { mkdirs() }
        val save = SaveFixtures.gciSave(
            File(cardA, "$makerCode-$gameId-ZELDA_DATA.gci"),
            gameId = gameId,
            makerCode = makerCode,
            internalFilename = "ZELDA_DATA",
            payload = ByteArray(256) { it.toByte() },
        )
        SaveFixtures.gciSave(
            File(cardA, "$makerCode-$gameId-ZELDA_BACK.gci"),
            gameId = gameId,
            makerCode = makerCode,
            internalFilename = "ZELDA_BACK",
            payload = ByteArray(128) { (it * 3).toByte() },
        )
        val otherCard = SaveFixtures.gciSave(
            File(cardB, "$makerCode-$gameId-ZELDA_OTHER.gci"),
            gameId = gameId,
            makerCode = makerCode,
            internalFilename = "ZELDA_OTHER",
            payload = ByteArray(256) { (it * 5).toByte() },
        )
        val otherBytes = otherCard.readBytes()
        val ctx = saveContext()
        val prepared = handler.prepareForUpload(save.absolutePath, ctx) ?: error("prepareForUpload returned null")
        val bundle = File(tempDir, "bundle.zip").also { prepared.file.copyTo(it) }

        val result = handler.extractDownload(bundle, ctx)

        assertTrue("Extract reported failure: ${result.error}", result.success)
        assertTrue("A save in another card folder was deleted", otherCard.exists())
        assertEquals(otherBytes.toList(), otherCard.readBytes().toList())
    }

    @Test
    fun `a core memory dump never uploads as a GameCube save`() = runTest {
        val dump = File(baseDir, "Super Smash Bros. Melee.srm").apply { writeBytes(ByteArray(296960) { 0xFF.toByte() }) }

        assertNull(handler.prepareForUpload(dump.absolutePath, saveContext()))
    }

    @Test
    fun `a cached path naming a non-gci file is not trusted`() {
        assertTrue(handler.isValidCachedSavePath("/storage/emulated/0/GC/USA/Card A/01-GZLE-ZELDA.gci"))
        assertTrue(handler.isValidCachedSavePath("/data/user/0/com.nendo.argosy/files/saves/GC"))
        assertFalse(handler.isValidCachedSavePath("/storage/emulated/0/RetroArch/saves/Melee.srm"))
    }

    @Test
    fun `only gci files and gci bundles are GameCube server saves`() {
        assertTrue(GciSaveHandler.isGciServerFileName("01-GZLE-ZELDA.gci"))
        assertTrue(GciSaveHandler.isGciServerFileName("Zelda [2026-06-20_10-00-00].zip"))
        assertFalse(GciSaveHandler.isGciServerFileName("Super Smash Bros. Melee [2026-10-20_05-21-15].srm"))
    }

    private fun saveContext() = SaveContext(
        config = SavePathConfig(
            emulatorId = "dolphin",
            defaultPaths = listOf(baseDir.absolutePath),
            saveExtensions = listOf("gci"),
            usesGciFormat = true,
        ),
        romPath = romFile.absolutePath,
        saveId = gameId,
        emulatorPackage = "org.dolphinemu.dolphinemu",
        gameId = 1L,
        gameTitle = "Zelda",
        platformSlug = "ngc",
        emulatorId = "dolphin",
        localSavePath = null,
        coreName = null,
    )
}
