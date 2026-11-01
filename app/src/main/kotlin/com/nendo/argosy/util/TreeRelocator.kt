package com.nendo.argosy.util

import java.io.File

/**
 * Moves a directory tree into a destination, preserving structure. A name already taken at the
 * destination is freed by renaming the incumbent, never by deleting it, and a destination inside
 * the source is stepped over so the walk never descends into its own output.
 */
object TreeRelocator {

    data class Result(val moved: List<String>, val failed: List<String>)

    fun relocate(source: File, destination: File): Result {
        val moved = mutableListOf<String>()
        val failed = mutableListOf<String>()
        if (!source.exists() || source.absolutePath == destination.absolutePath) return Result(moved, failed)
        moveTree(source, destination, destination.absolutePath, moved, failed)
        return Result(moved, failed)
    }

    private fun moveTree(
        source: File,
        destination: File,
        destinationRoot: String,
        moved: MutableList<String>,
        failed: MutableList<String>
    ) {
        destination.mkdirs()
        val files = source.listFiles() ?: run {
            Logger.warn(TAG, "Left ${source.absolutePath} in place; it could not be listed")
            failed.add(source.absolutePath)
            return
        }
        for (file in files) {
            if (file.absolutePath == destinationRoot) continue
            val target = File(destination, file.name)
            if (file.isDirectory) {
                moveTree(file, target, destinationRoot, moved, failed)
            } else if (moveFile(file, target)) {
                moved.add(target.absolutePath)
            } else {
                failed.add(file.absolutePath)
            }
        }
        if (source.listFiles()?.isEmpty() == true) source.delete()
    }

    private fun moveFile(file: File, target: File): Boolean = try {
        if (target.exists() && !displace(target)) {
            Logger.warn(TAG, "Left ${file.name} in place; ${target.name} is occupied")
            false
        } else if (file.renameTo(target)) {
            true
        } else {
            file.copyTo(target, overwrite = false)
            file.delete()
            true
        }
    } catch (e: Exception) {
        Logger.warn(TAG, "Failed to move ${file.name}: ${e.message}")
        false
    }

    private fun displace(target: File): Boolean {
        val extension = target.name.substringAfterLast('.', "")
        val base = target.name.substringBeforeLast('.', target.name)
        for (index in 1..MAX_DISPLACEMENT_ATTEMPTS) {
            val name = if (extension.isEmpty()) "$base ($index)" else "$base ($index).$extension"
            val candidate = File(target.parentFile, name)
            if (!candidate.exists() && target.renameTo(candidate)) return true
        }
        return false
    }

    private const val TAG = "TreeRelocator"
    private const val MAX_DISPLACEMENT_ATTEMPTS = 99
}
