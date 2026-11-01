package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.PlatformLibretroSettingsDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.libretro.GraphicsApi
import com.nendo.argosy.libretro.GraphicsBackend
import com.nendo.argosy.libretro.GraphicsBackendRegistry
import com.nendo.argosy.libretro.coreoptions.CoreOptionResolver
import com.swordfish.libretrodroid.Variable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The renderer and Vulkan driver a built-in launch runs with. [coreVariables] carry the game's
 * backend; [driver] is null whenever the system driver runs.
 */
data class BuiltinGraphics(
    val coreId: String?,
    val backend: GraphicsBackend?,
    val coreVariables: List<Variable>,
    val gameBackend: String?,
    val inheritedBackend: String?,
    val backendValue: String?,
    val preferVulkan: Boolean,
    val gameDriver: String?,
    val inheritedDriver: String,
    val choice: GpuDriverChoice,
    val driver: InstalledVulkanDriver?,
    val installedDrivers: List<InstalledVulkanDriver>,
    val customDriversSupported: Boolean,
    val coreAcceptsCustomDriver: Boolean
)

@Singleton
class BuiltinGraphicsResolver @Inject constructor(
    private val coreOptionResolver: CoreOptionResolver,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val platformLibretroSettingsDao: PlatformLibretroSettingsDao,
    private val vulkanDriverRepository: VulkanDriverRepository
) {
    suspend fun resolveForGame(game: GameEntity, coreId: String?, globalDriver: String): BuiltinGraphics {
        val variables = coreId
            ?.let { coreOptionResolver.resolveVariables(it, game.id.takeIf { game.perGameSettingsEnabled }) }
            ?.toList()
            .orEmpty()
        return resolve(game.id, game.platformId, coreId, variables, globalDriver)
    }

    suspend fun resolve(
        gameId: Long,
        platformId: Long,
        coreId: String?,
        resolvedVariables: List<Variable>,
        globalDriver: String
    ): BuiltinGraphics {
        val platformSettings = platformLibretroSettingsDao.getByPlatformId(platformId)
        val gameConfig = emulatorConfigDao.getByGameId(gameId)
        val backend = GraphicsBackendRegistry.forCore(coreId)
        val vulkanSupported = vulkanDriverRepository.vulkanSupported()
        val gameBackend = backend?.let { GraphicsBackendRegistry.gameValue(it, gameConfig?.graphicsBackend) }
        val requestedVariables = GraphicsBackendRegistry.applyGameBackend(coreId, resolvedVariables, gameConfig?.graphicsBackend)
        val coreVariables = if (vulkanSupported) {
            requestedVariables
        } else {
            GraphicsBackendRegistry.withoutVulkan(coreId, requestedVariables)
        }
        val inheritedFrontendApi = platformSettings?.graphicsApi
            ?: GraphicsApi.OPENGL.token.takeUnless { vulkanSupported }
        val requestedFrontendApi = gameBackend?.takeIf { backend is GraphicsBackend.FrontendApi } ?: inheritedFrontendApi
        val frontendApi = if (vulkanSupported) requestedFrontendApi else GraphicsApi.OPENGL.token
        val effectiveVariables = coreVariables.toValueMap()
        val gameDriver = gameConfig?.gpuDriver?.takeIf { it.isNotBlank() }
        val inheritedDriver = GpuDriverPrecedence.select(null, platformSettings?.gpuDriver, globalDriver)
        val installed = vulkanDriverRepository.listInstalled()
        val customSupported = vulkanDriverRepository.customDriversSupported()
        val coreAcceptsCustom = GraphicsBackendRegistry.acceptsCustomVulkanDriver(coreId)
        val choice = GpuDriverPrecedence.resolve(
            requested = GpuDriverPrecedence.select(gameDriver, inheritedDriver, globalDriver),
            installedIds = installed.map { it.id }.toSet(),
            customDriversSupported = customSupported && coreAcceptsCustom
        )
        return BuiltinGraphics(
            coreId = coreId,
            backend = backend,
            coreVariables = coreVariables,
            gameBackend = gameBackend,
            inheritedBackend = backend?.let {
                val inheritedVariables = if (vulkanSupported) {
                    resolvedVariables
                } else {
                    GraphicsBackendRegistry.withoutVulkan(coreId, resolvedVariables)
                }
                GraphicsBackendRegistry.effectiveValue(it, inheritedVariables.toValueMap(), inheritedFrontendApi)
            },
            backendValue = backend?.let { GraphicsBackendRegistry.effectiveValue(it, effectiveVariables, frontendApi) },
            preferVulkan = GraphicsBackendRegistry.prefersVulkan(coreId, effectiveVariables, frontendApi),
            gameDriver = gameDriver,
            inheritedDriver = inheritedDriver,
            choice = choice,
            driver = installed.firstOrNull { it.id == choice.customDriverId },
            installedDrivers = installed,
            customDriversSupported = customSupported,
            coreAcceptsCustomDriver = coreAcceptsCustom
        )
    }

    private fun List<Variable>.toValueMap(): Map<String, String> =
        mapNotNull { v -> v.key?.let { k -> v.value?.let { k to it } } }.toMap()
}
