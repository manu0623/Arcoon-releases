package com.nendo.argosy.libretro.shader

import android.content.Context
import com.swordfish.libretrodroid.ShaderConfig
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShaderOriginalSizeTest {

    private val registry = ShaderRegistry(mockk<Context>(relaxed = true))

    @Test
    fun `the first pass reads the original sizes as its own input sizes`() {
        val bound = registry.withOriginalSizesBound(listOf(pass(LCD3X_FRAGMENT))).single()

        assertTrue(bound.fragment.contains("#define OrigInputSize InputSize"))
        assertTrue(bound.fragment.contains("#define OrigTextureSize TextureSize"))
        assertFalse(bound.fragment.contains("uniform COMPAT_PRECISION vec2 OrigInputSize;"))
        assertTrue(bound.fragment.contains("uniform COMPAT_PRECISION vec2 InputSize;"))
    }

    @Test
    fun `a later pass keeps its declared original sizes`() {
        val later = pass(LCD3X_FRAGMENT)

        val bound = registry.withOriginalSizesBound(listOf(pass("void main() {}"), later))

        assertEquals(later, bound[1])
    }

    @Test
    fun `a shader without original sizes is unchanged`() {
        val plain = pass("uniform COMPAT_PRECISION vec2 InputSize;\nvoid main() {}")

        assertEquals(plain, registry.withOriginalSizesBound(listOf(plain)).single())
    }

    private fun pass(fragment: String) = ShaderConfig.Custom.ShaderPass(vertex = "void main() {}", fragment = fragment)

    private companion object {
        val LCD3X_FRAGMENT = """
            uniform COMPAT_PRECISION vec2 OrigInputSize;
            uniform COMPAT_PRECISION vec2 OrigTextureSize;
            uniform COMPAT_PRECISION vec2 TextureSize;
            uniform COMPAT_PRECISION vec2 InputSize;
            void main() { vec2 omega = OrigInputSize; }
        """.trimIndent()
    }
}
