package com.nendo.argosy.libretro.shader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlslParserTest {
    @Test
    fun parsedStagesAdvertiseSensorsAndPreservePrecision() {
        for (precision in listOf("", "precision mediump float;")) {
            val parsed = GlslParser.parse(
                """
                    #version 130
                    #ifdef _HAS_SENSOR_UNIFORMS
                    uniform vec3 Accelerometer;
                    #endif
                    #if defined(VERTEX)
                    void main() { gl_Position = vec4(Accelerometer, 1.0); }
                    #elif defined(FRAGMENT)
                    $precision
                    void main() { gl_FragColor = vec4(Accelerometer, 1.0); }
                    #endif
                """.trimIndent()
            )
            for (source in listOf(parsed.vertex, parsed.fragment)) {
                assertTrue(source.startsWith("#define _HAS_SENSOR_UNIFORMS\n"))
                assertFalse(source.contains("#version"))
                assertTrue(source.indexOf("#define _HAS_SENSOR_UNIFORMS") < source.indexOf("#ifdef _HAS_SENSOR_UNIFORMS"))
            }
            assertTrue(parsed.fragment.contains(precision.ifEmpty { "precision highp float;" }))
            assertEquals(precision.isEmpty(), parsed.fragment.contains("precision highp float;"))
        }
    }
}
