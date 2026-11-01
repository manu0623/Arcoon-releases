package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.model.VariantCategory
import com.nendo.argosy.data.music.MusicDirectoryManager
import com.nendo.argosy.util.Logger
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RomMGameFileSync @Inject constructor(
    private val gameFileDao: GameFileDao,
    private val musicDirectoryManager: MusicDirectoryManager
) {
    companion object {
        private const val TAG = "RomMGameFileSync"
    }

    /**
     * An absent file list means the response did not carry one; it is not a statement that
     * the game has no files. Only a response that actually enumerated files may remove rows.
     *
     * A root game file is reported with no category on RomM 4.9 and as `game` from 5.0, so
     * shape alone cannot classify it. Depth does: anything below the rom's own directory
     * that the server did not categorise is content we do not model, never a launch target.
     */
    suspend fun sync(gameId: Long, rom: RomMRom, platformSlug: String, fileListIsAuthoritative: Boolean) {
        val files = rom.files?.filter { file ->
            file.isGameContent
        } ?: return
        val rootPathLength = files.minOfOrNull { it.filePath.length }

        if (files.isEmpty()) {
            if (!fileListIsAuthoritative) {
                Logger.debug(TAG, "sync: no files in this response, keeping existing rows | gameId=$gameId")
                return
            }
            gameFileDao.deleteByGameId(gameId)
            return
        }

        val validIds = files.mapNotNull { if (it.id > 0) it.id else null }
        if (validIds.isNotEmpty() && fileListIsAuthoritative) {
            gameFileDao.deleteInvalidFilesForRom(gameId, rom.id, validIds)
        }

        val existingByFileId = gameFileDao
            .getByRommFileIds(files.map { it.id })
            .associateBy { it.rommFileId }
        val classified = files.map { file ->
            val isNested = rootPathLength != null && file.filePath.length > rootPathLength
            val category = when {
                file.category != null -> VariantCategory.fromKey(file.category)
                isNested -> VariantCategory.UNKNOWN
                else -> VariantCategory.GAME
            }
            ClassifiedFile(file, category, isNested)
        }

        val recoveredMusic = recoverMusicPaths(
            rom,
            classified.filter {
                it.category == VariantCategory.SOUNDTRACK &&
                    existingByFileId[it.file.id]?.localPath == null
            }
        )

        val entities = classified.map { (file, category, isNested) ->
            val existing = existingByFileId[file.id]
            val localPath = existing?.localPath ?: recoveredMusic[file.id]
            GameFileEntity(
                id = existing?.id ?: 0,
                gameId = gameId,
                rommFileId = file.id,
                romId = file.romId,
                fileName = file.fileName,
                filePath = file.filePath,
                category = category.key,
                fileSize = file.fileSizeBytes,
                localPath = localPath,
                downloadedAt = existing?.downloadedAt ?: localPath?.let { Instant.now() },
                isLaunchTarget = category.isLaunchTarget && !(isNested && file.category == null),
                isMultiDisc = existing?.isMultiDisc ?: false,
                m3uPath = existing?.m3uPath,
                regions = existing?.regions,
                versionGroup = existing?.versionGroup,
                trackTitle = file.trackMeta?.title ?: existing?.trackTitle,
                trackNumber = file.trackMeta?.track ?: existing?.trackNumber,
                durationSeconds = file.trackMeta?.durationSeconds ?: existing?.durationSeconds,
                docTitle = file.docMeta?.title ?: existing?.docTitle,
                docSource = file.docMeta?.source ?: existing?.docSource,
                docSourceUrl = file.docMeta?.sourceUrl ?: existing?.docSourceUrl
            )
        }
        gameFileDao.insertAll(entities)
    }

    private data class ClassifiedFile(
        val file: RomMRomFile,
        val category: VariantCategory,
        val isNested: Boolean
    )

    private suspend fun recoverMusicPaths(
        rom: RomMRom,
        tracks: List<ClassifiedFile>
    ): Map<Long, String> {
        if (tracks.isEmpty()) return emptyMap()
        val musicDir = musicDirectoryManager.resolveMusicDir()
        val platformName = rom.platformName ?: rom.platformSlug
        val probe = musicDirectoryManager.targetFileIn(
            musicDir = musicDir,
            platformName = platformName,
            gameName = rom.name,
            trackNumber = null,
            title = null,
            fileName = ""
        )
        if (probe.parentFile?.isDirectory != true) return emptyMap()

        return tracks.mapNotNull { track ->
            val target = musicDirectoryManager.targetFileIn(
                musicDir = musicDir,
                platformName = platformName,
                gameName = rom.name,
                trackNumber = track.file.trackMeta?.track,
                title = track.file.trackMeta?.title,
                fileName = track.file.fileName
            )
            target.takeIf { it.exists() }?.let { track.file.id to it.absolutePath }
        }.toMap()
    }
}
