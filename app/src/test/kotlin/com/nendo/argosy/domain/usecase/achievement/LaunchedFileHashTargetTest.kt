package com.nendo.argosy.domain.usecase.achievement

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LaunchedFileHashTargetTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `launching the library copy is the library copy`() {
        val rom = temp.newFile("Game.sfc")

        assertTrue(VerifyRAGameIdUseCase.isLibraryCopy(rom.absolutePath, rom.absolutePath))
    }

    @Test
    fun `launching the first disc of the library playlist is the library copy`() {
        val disc = temp.newFile("Game (Disc 1).chd")
        val playlist = File(temp.root, "Game.m3u").apply { writeText(disc.name) }

        assertTrue(VerifyRAGameIdUseCase.isLibraryCopy(disc.absolutePath, playlist.absolutePath))
    }

    @Test
    fun `launching a variant file is not the library copy`() {
        val base = temp.newFile("Game.sfc")
        val hack = temp.newFile("Game (Hack).sfc")

        assertFalse(VerifyRAGameIdUseCase.isLibraryCopy(hack.absolutePath, base.absolutePath))
    }

    @Test
    fun `a game with no library copy has nothing to match`() {
        val rom = temp.newFile("Game.sfc")

        assertFalse(VerifyRAGameIdUseCase.isLibraryCopy(rom.absolutePath, null))
    }
}
