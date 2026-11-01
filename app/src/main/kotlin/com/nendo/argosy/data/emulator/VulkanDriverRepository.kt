package com.nendo.argosy.data.emulator

import android.content.Context
import android.content.pm.PackageManager
import com.nendo.argosy.libretro.LibretroBuildbot
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VulkanDriverRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val driverFetcherRepository: DriverFetcherRepository
) {
    @Volatile
    private var customDriversSupportedCache: Boolean? = null

    private val driversRoot: File
        get() = File(context.filesDir, DRIVERS_DIR)

    suspend fun customDriversSupported(): Boolean = withContext(Dispatchers.IO) {
        customDriversSupportedCache ?: (
            LibretroBuildbot.processIs64Bit && driverFetcherRepository.getGpuInfo().adrenoNumber > 0
        ).also { customDriversSupportedCache = it }
    }

    fun vulkanSupported(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, VULKAN_1_1)

    suspend fun listInstalled(): List<InstalledVulkanDriver> = withContext(Dispatchers.IO) {
        VulkanDriverPackages.listInstalled(driversRoot)
    }

    suspend fun listInstallablePackages(): List<File> = withContext(Dispatchers.IO) {
        val installedIds = VulkanDriverPackages.listInstalled(driversRoot).map { it.id }.toSet()
        driverFetcherRepository.listDownloadedFiles()
            .filter { VulkanDriverPackages.isPackageFile(it) }
            .filter { VulkanDriverPackages.idForPackage(it.name) !in installedIds }
    }

    suspend fun installFromDownloads(fileName: String): Result<InstalledVulkanDriver> =
        withContext(Dispatchers.IO) {
            val packageFile = driverFetcherRepository.listDownloadedFiles()
                .firstOrNull { it.name == fileName && VulkanDriverPackages.isPackageFile(it) }
                ?: return@withContext Result.failure(
                    IllegalArgumentException("$fileName is not in the downloaded drivers")
                )
            runCatching { VulkanDriverPackages.extract(packageFile, driversRoot) }
                .onSuccess { Logger.info(TAG, "Installed Vulkan driver ${it.id} from $fileName") }
                .onFailure { Logger.warn(TAG, "Vulkan driver install from $fileName failed: ${it.message}") }
        }

    companion object {
        private const val TAG = "VulkanDriverRepository"
        private const val DRIVERS_DIR = "vulkan_drivers"
        private const val VULKAN_1_1 = 0x401000
    }
}
