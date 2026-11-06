package com.nendo.argosy.data.media

import org.junit.Test
import java.io.File
import java.util.Locale

class RealCommercialBannerPipelineTest {

    private val reader = RomContainerReader()

    @Test
    fun `real commercial banners render through the scene pipeline`() {
        val roms = listOf(
            File("C:\\Users\\manu0\\Documents\\Arcoon2\\_downloads\\metroid_samus_returns_real.3ds"),
            File("C:\\Users\\manu0\\Documents\\Arcoon2\\_downloads\\mario_luigi_bowser.3ds"),
            File("C:\\Users\\manu0\\Documents\\Arcoon2\\_downloads\\oot3d_real.3ds"),
        )
        for (rom in roms) {
            val inspection = reader.inspect(rom)
            if (!inspection.isValid || inspection.isEncrypted) continue
            val banner = reader.readThreeDsBannerCgfx(rom, inspection, Locale.US)
            val cgfx = (banner as? ThreeDsBannerResult.Success)?.cgfx ?: continue
            val scene = CgfxBannerScene.render(cgfx)
            println("SCENE_RESULT rom=${rom.name} scene=$scene")
            val render = ThreeDsBannerRenderer.render(cgfx)
            println("RENDER_RESULT rom=${rom.name} -> $render")
        }
    }
}
