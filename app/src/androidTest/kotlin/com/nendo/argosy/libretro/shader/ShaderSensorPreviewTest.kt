package com.nendo.argosy.libretro.shader

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swordfish.libretrodroid.ShaderConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShaderSensorPreviewTest {
    @Test
    fun diagnosticShaderRendersNeutralSensorsInSingleAndScaledChains() = runBlocking {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val parsed = GlslParser.parse(assets.open("shaders/motion-test.glsl").bufferedReader().use { it.readText() })
        val pass = ShaderConfig.Custom.ShaderPass(parsed.vertex, parsed.fragment)
        val input = Bitmap.createBitmap(2, 3, Bitmap.Config.ARGB_8888)
        val renderer = ShaderPreviewRenderer()
        try {
            for ((name, passes) in listOf(
                "single pass" to listOf(pass),
                "scaled chain" to listOf(pass.copy(scale = 2f), pass)
            )) {
                val result = renderer.render(input, ShaderConfig.Custom(passes), outputWidth = 80, outputHeight = 120)
                assertNotNull(name, result)
                try {
                    assertEquals("$name neutral tilt", Color.CYAN, result!!.getPixel(40, 60))
                    val strips = listOf(result.getPixel(40, 2), result.getPixel(40, 117))
                    val calibration = strips.maxBy { Color.red(it) }
                    val gyroscope = strips.minBy { Color.red(it) }
                    assertEquals("$name uncalibrated red", 255, Color.red(calibration))
                    assertEquals("$name uncalibrated green", 128.0, Color.green(calibration).toDouble(), 1.0)
                    assertEquals("$name uncalibrated blue", 0, Color.blue(calibration))
                    for (channel in listOf(Color.red(gyroscope), Color.green(gyroscope), Color.blue(gyroscope))) {
                        assertEquals("$name stationary gyro", 8.0, channel.toDouble(), 1.0)
                    }
                } finally {
                    result?.recycle()
                }
            }
        } finally {
            input.recycle()
            renderer.destroy()
        }
    }
}
