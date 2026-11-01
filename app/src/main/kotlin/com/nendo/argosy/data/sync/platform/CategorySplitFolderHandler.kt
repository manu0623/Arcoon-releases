package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.SaveArchiver
import java.io.File

internal open class CategorySplitFolderHandler(
    context: Context,
    fal: FileAccessLayer,
    saveArchiver: SaveArchiver,
    platformSlug: String,
    private val categoryDir: Regex
) : FolderSaveHandler(context, fal, saveArchiver, platformSlug = platformSlug) {

    companion object {
        private const val ID_HEX_LENGTH = 8
        private val FULL_ID = Regex("[0-9a-fA-F]{16}")

        fun lowId(saveId: String): String =
            saveId.trimEnd('/').substringAfterLast('/').takeLast(ID_HEX_LENGTH).lowercase()

        fun categoryOf(saveId: String): String? =
            saveId.replace("/", "").takeIf { FULL_ID.matches(it) }?.take(ID_HEX_LENGTH)?.lowercase()
    }

    fun categoryBase(basePath: String, saveId: String): String {
        val base = basePath.trimEnd('/')
        val category = categoryOf(saveId) ?: return base
        val lastSegment = base.substringAfterLast('/')
        if (!categoryDir.matches(lastSegment) || lastSegment.equals(category, ignoreCase = true)) return base
        return "${base.substringBeforeLast('/')}/$category"
    }

    override fun folderMatches(folderName: String, saveId: String): Boolean =
        folderName.equals(lowId(saveId), ignoreCase = true)

    override fun constructSavePath(baseDir: String, saveId: String): String? =
        "${categoryBase(baseDir, saveId)}/${lowId(saveId)}"

    override fun findSaveFolderBySaveId(basePath: String, saveId: String): String? =
        super.findSaveFolderBySaveId(categoryBase(basePath, saveId), saveId)

    override fun findAllSaveFoldersBySaveId(basePath: String, saveId: String): List<String> =
        super.findAllSaveFoldersBySaveId(categoryBase(basePath, saveId), saveId)

    override fun matchArchive(tempFile: File, saveId: String): ArchiveRootMatch? =
        if (saveArchiver.peekRootEntryNames(tempFile).any { it.equals(lowId(saveId), ignoreCase = true) }) {
            ArchiveRootMatch.EXACT
        } else {
            super.matchArchive(tempFile, saveId)
        }
}
