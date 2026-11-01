package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.PlatformLibretroSettingsDao
import com.nendo.argosy.data.local.entity.EmulatorConfigEntity
import com.nendo.argosy.data.local.entity.PlatformLibretroSettingsEntity
import com.swordfish.libretrodroid.Variable
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BuiltinGraphicsResolverTest {

    private val gameId = 7L
    private val platformId = 3L
    private val emulatorConfigDao = mockk<EmulatorConfigDao>()
    private val platformDao = mockk<PlatformLibretroSettingsDao>()
    private val drivers = mockk<VulkanDriverRepository>()
    private val turnip = InstalledVulkanDriver("turnip", "Turnip", "25", null, File("/d/turnip"), "libvulkan_freedreno.so")

    private val resolver = BuiltinGraphicsResolver(mockk(relaxed = true), emulatorConfigDao, platformDao, drivers)

    private fun given(
        gameBackend: String?,
        gameDriver: String? = null,
        platformApi: String? = null,
        vulkanSupported: Boolean = true
    ) {
        every { drivers.vulkanSupported() } returns vulkanSupported
        coEvery { emulatorConfigDao.getByGameId(gameId) } returns EmulatorConfigEntity(
            platformId = null,
            gameId = gameId,
            packageName = null,
            displayName = null,
            coreName = null,
            gpuDriver = gameDriver,
            graphicsBackend = gameBackend
        )
        coEvery { platformDao.getByPlatformId(platformId) } returns
            PlatformLibretroSettingsEntity(platformId = platformId, graphicsApi = platformApi)
        coEvery { drivers.listInstalled() } returns listOf(turnip)
        coEvery { drivers.customDriversSupported() } returns true
    }

    private suspend fun resolve(coreId: String, variables: List<Variable>) =
        resolver.resolve(gameId, platformId, coreId, variables, GpuDriverToken.SYSTEM)

    @Test
    fun `the game backend outranks the core override for launch, display and the Vulkan check`() = runTest {
        given(gameBackend = "vulkan", gameDriver = GpuDriverToken.custom("turnip"))

        val graphics = resolve("ppsspp", listOf(Variable("ppsspp_backend", "opengl")))

        assertEquals("vulkan", graphics.gameBackend)
        assertEquals("opengl", graphics.inheritedBackend)
        assertEquals("vulkan", graphics.backendValue)
        assertTrue(graphics.preferVulkan)
        assertEquals(listOf("ppsspp_backend" to "vulkan"), graphics.coreVariables.map { it.key to it.value })
        assertEquals("turnip", graphics.driver?.id)
    }

    @Test
    fun `a game set to OpenGL turns Vulkan off even when the core override is Vulkan`() = runTest {
        given(gameBackend = "opengl")

        val graphics = resolve("ppsspp", listOf(Variable("ppsspp_backend", "vulkan")))

        assertEquals("vulkan", graphics.inheritedBackend)
        assertEquals("opengl", graphics.backendValue)
        assertFalse(graphics.preferVulkan)
    }

    @Test
    fun `without a game backend the core override decides`() = runTest {
        given(gameBackend = null)

        val graphics = resolve("ppsspp", listOf(Variable("ppsspp_backend", "vulkan")))

        assertNull(graphics.gameBackend)
        assertEquals("vulkan", graphics.backendValue)
        assertTrue(graphics.preferVulkan)
    }

    @Test
    fun `without any override the core default decides`() = runTest {
        given(gameBackend = null)

        val graphics = resolve("mednafen_psx_hw", emptyList())

        assertEquals("hardware", graphics.backendValue)
        assertFalse(graphics.preferVulkan)
        assertTrue(graphics.coreVariables.isEmpty())
    }

    @Test
    fun `a frontend core takes the game api over the platform api`() = runTest {
        given(gameBackend = "vulkan", platformApi = "opengl")
        val graphics = resolve("dolphin", emptyList())
        assertEquals("opengl", graphics.inheritedBackend)
        assertEquals("vulkan", graphics.backendValue)
        assertTrue(graphics.preferVulkan)
        assertTrue(graphics.coreVariables.isEmpty())
    }

    @Test
    fun `a frontend core follows the platform api when the game inherits`() = runTest {
        given(gameBackend = null, platformApi = "vulkan")
        assertTrue(resolve("flycast", emptyList()).preferVulkan)

        given(gameBackend = "opengl", platformApi = "vulkan")
        assertFalse(resolve("flycast", emptyList()).preferVulkan)
    }

    @Test
    fun `a frontend core with no platform or game api defaults to Vulkan`() = runTest {
        given(gameBackend = null, platformApi = null)

        val graphics = resolve("dolphin", emptyList())

        assertEquals("vulkan", graphics.backendValue)
        assertTrue(graphics.preferVulkan)
    }

    @Test
    fun `without Vulkan support a core option Vulkan choice falls back to the core default`() = runTest {
        given(gameBackend = "vulkan", vulkanSupported = false)

        val graphics = resolve("ppsspp", listOf(Variable("ppsspp_backend", "vulkan")))

        assertEquals(listOf("ppsspp_backend" to "auto"), graphics.coreVariables.map { it.key to it.value })
        assertEquals("auto", graphics.backendValue)
        assertEquals("auto", graphics.inheritedBackend)
        assertFalse(graphics.preferVulkan)
    }

    @Test
    fun `without Vulkan support a frontend core runs OpenGL whatever was stored`() = runTest {
        given(gameBackend = "vulkan", platformApi = "vulkan", vulkanSupported = false)

        val graphics = resolve("flycast", emptyList())

        assertEquals("opengl", graphics.backendValue)
        assertFalse(graphics.preferVulkan)
    }

    @Test
    fun `ARMSX2 always runs the system driver even when a custom driver is installed and chosen`() = runTest {
        given(gameBackend = null, gameDriver = GpuDriverToken.custom("turnip"))

        val graphics = resolve("armsx2", listOf(Variable("armsx2_renderer", "Vulkan")))

        assertTrue(graphics.preferVulkan)
        assertNull(graphics.driver)
        assertNull(graphics.choice.customDriverId)
        assertTrue(graphics.choice.fellBackToSystem)
        assertFalse(graphics.coreAcceptsCustomDriver)
    }

    @Test
    fun `a core that accepts custom drivers still loads the chosen one`() = runTest {
        given(gameBackend = null, gameDriver = GpuDriverToken.custom("turnip"))

        val graphics = resolve("ppsspp", listOf(Variable("ppsspp_backend", "vulkan")))

        assertEquals("turnip", graphics.driver?.id)
        assertTrue(graphics.coreAcceptsCustomDriver)
    }

    @Test
    fun `ARMSX2 on a device without Vulkan swaps the renderer to OpenGL`() = runTest {
        given(gameBackend = null, vulkanSupported = false)

        val graphics = resolve("armsx2", listOf(Variable("armsx2_renderer", "Vulkan")))

        assertEquals(listOf("armsx2_renderer" to "OpenGL"), graphics.coreVariables.map { it.key to it.value })
        assertFalse(graphics.preferVulkan)
    }

    @Test
    fun `a core without a backend never prefers Vulkan and ignores a stored game backend`() = runTest {
        given(gameBackend = "vulkan")

        val graphics = resolve("snes9x", listOf(Variable("snes9x_layer_1", "enabled")))

        assertNull(graphics.backend)
        assertNull(graphics.gameBackend)
        assertNull(graphics.backendValue)
        assertFalse(graphics.preferVulkan)
        assertEquals(listOf("snes9x_layer_1" to "enabled"), graphics.coreVariables.map { it.key to it.value })
    }
}
