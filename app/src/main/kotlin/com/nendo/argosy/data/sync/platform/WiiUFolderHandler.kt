package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.SaveArchiver
import java.io.File

internal class WiiUFolderHandler(
    context: Context,
    fal: FileAccessLayer,
    saveArchiver: SaveArchiver
) : CategorySplitFolderHandler(context, fal, saveArchiver, platformSlug = "wiiu", categoryDir = Regex("0005[0-9a-fA-F]{4}")) {

    companion object {
        private const val USER_DIR = "user"
        private const val COMMON_DIR = "common"
        private val PERSISTENT_ID = Regex("8[0-9a-fA-F]{7}")

        fun isUserFolderArchive(roots: Set<String>): Boolean =
            roots.isNotEmpty() && roots.all { it == COMMON_DIR || PERSISTENT_ID.matches(it) }
    }

    override fun matchArchive(tempFile: File, saveId: String): ArchiveRootMatch? {
        val roots = saveArchiver.peekRootEntryNames(tempFile)
        return when {
            roots.any { it.equals(lowId(saveId), ignoreCase = true) } -> ArchiveRootMatch.EXACT
            isUserFolderArchive(roots) -> ArchiveRootMatch.UNIDENTIFIED
            else -> super.matchArchive(tempFile, saveId)
        }
    }

    override fun unpackArchive(tempFile: File, targetFolder: File, saveId: String?): Boolean {
        val roots = saveArchiver.peekRootEntryNames(tempFile)
        if (!isUserFolderArchive(roots)) return super.unpackArchive(tempFile, targetFolder, saveId)
        val userFolder = File(targetFolder, USER_DIR)
        return saveArchiver.unzipRootsTo(tempFile, roots.associateWith { File(userFolder, it) })
    }
}
