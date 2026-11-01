package com.nendo.argosy.data.emulator

import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

data class InstalledVulkanDriver(
    val id: String,
    val name: String,
    val driverVersion: String?,
    val description: String?,
    val directory: File,
    val libraryName: String
)

internal object VulkanDriverPackages {

    const val META_FILE = "meta.json"
    private const val PACKAGE_SUFFIX = ".zip"
    private const val ADPKG_SUFFIX = ".adpkg"
    private const val STAGING_PREFIX = ".staging-"
    private const val PREVIOUS_PREFIX = ".previous-"
    private const val MAX_UNCOMPRESSED_BYTES = 512L * 1024 * 1024
    private const val FALLBACK_ID = "driver"
    private val UNSAFE_ID_CHARS = Regex("[^A-Za-z0-9._-]")

    data class Meta(
        val name: String,
        val libraryName: String,
        val driverVersion: String?,
        val description: String?
    )

    fun parseMeta(json: String): Meta? {
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val library = obj.text("libraryName")
        if (!isPlainFileName(library)) return null
        return Meta(
            name = obj.text("name").ifEmpty { library },
            libraryName = library,
            driverVersion = obj.text("driverVersion").ifEmpty { null },
            description = obj.text("description").ifEmpty { null }
        )
    }

    private fun JSONObject.text(key: String): String =
        if (isNull(key)) "" else optString(key).trim()

    fun isPackageFile(file: File): Boolean =
        file.isFile && file.name.endsWith(PACKAGE_SUFFIX, ignoreCase = true)

    fun idForPackage(fileName: String): String {
        val base = fileName
            .removeSuffixIgnoreCase(PACKAGE_SUFFIX)
            .removeSuffixIgnoreCase(ADPKG_SUFFIX)
        return base.replace(UNSAFE_ID_CHARS, "_").trim('.', '_').ifEmpty { FALLBACK_ID }
    }

    fun readInstalled(dir: File): InstalledVulkanDriver? {
        val metaFile = File(dir, META_FILE).takeIf { it.isFile } ?: return null
        val meta = parseMeta(metaFile.readText()) ?: return null
        if (!File(dir, meta.libraryName).isFile) return null
        return InstalledVulkanDriver(
            id = dir.name,
            name = meta.name,
            driverVersion = meta.driverVersion,
            description = meta.description,
            directory = dir,
            libraryName = meta.libraryName
        )
    }

    fun listInstalled(root: File): List<InstalledVulkanDriver> =
        root.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.mapNotNull { readInstalled(it) }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()

    fun extract(
        packageFile: File,
        root: File,
        maxUncompressedBytes: Long = MAX_UNCOMPRESSED_BYTES
    ): InstalledVulkanDriver {
        val id = idForPackage(packageFile.name)
        if (!root.isDirectory && !root.mkdirs()) throw IOException("Cannot create ${root.path}")
        val target = File(root, id)
        val previous = File(root, PREVIOUS_PREFIX + id)
        restoreInterruptedSwap(target, previous)
        val staging = File(root, STAGING_PREFIX + id)
        staging.deleteRecursively()
        if (!staging.mkdirs()) throw IOException("Cannot create ${staging.path}")
        try {
            unzipInto(packageFile, staging, maxUncompressedBytes)
            val content = locateContent(staging)
                ?: throw IOException("${packageFile.name} has no $META_FILE")
            readInstalled(content)
                ?: throw IOException("${packageFile.name} does not hold the library its $META_FILE names")
            return swapIn(content, target, previous)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun restoreInterruptedSwap(target: File, previous: File) {
        if (!previous.exists()) return
        if (target.exists()) previous.deleteRecursively() else previous.renameTo(target)
    }

    private fun swapIn(content: File, target: File, previous: File): InstalledVulkanDriver {
        if (target.exists() && !target.renameTo(previous)) {
            throw IOException("Cannot move the installed driver ${target.name} aside")
        }
        try {
            if (!content.renameTo(target)) content.copyRecursively(target, overwrite = true)
            val installed = readInstalled(target) ?: throw IOException("Installed driver ${target.name} is unreadable")
            previous.deleteRecursively()
            return installed
        } catch (e: Exception) {
            target.deleteRecursively()
            if (previous.exists() && !previous.renameTo(target)) {
                throw IOException("Driver ${target.name} failed to install and the previous copy could not be restored", e)
            }
            throw e
        }
    }

    private fun unzipInto(packageFile: File, destination: File, maxUncompressedBytes: Long) {
        val base = destination.canonicalFile
        var written = 0L
        ZipInputStream(packageFile.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val target = File(base, entry.name).canonicalFile
                if (!target.path.startsWith(base.path + File.separator)) {
                    throw IOException("Blocked entry outside the package: ${entry.name}")
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { out ->
                        written += copyWithin(zip, out, maxUncompressedBytes - written)
                    }
                }
            }
        }
    }

    private fun copyWithin(input: InputStream, output: OutputStream, remaining: Long): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return copied
            copied += read
            if (copied > remaining) throw IOException("Driver package expands past its size limit")
            output.write(buffer, 0, read)
        }
    }

    private fun locateContent(staging: File): File? {
        if (File(staging, META_FILE).isFile) return staging
        val folders = staging.listFiles()?.filter { it.isDirectory }.orEmpty()
        return folders.singleOrNull()?.takeIf { File(it, META_FILE).isFile }
    }

    private fun isPlainFileName(name: String): Boolean =
        name.isNotEmpty() && name != "." && name != ".." && '/' !in name && '\\' !in name

    private fun String.removeSuffixIgnoreCase(suffix: String): String =
        if (endsWith(suffix, ignoreCase = true)) substring(0, length - suffix.length) else this
}
