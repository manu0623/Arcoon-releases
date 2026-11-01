package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.LibretroSavePathResolver
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.repository.EmulatorSaveConfigRepository
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "VariantSaveCarryOver"

/**
 * Copies the built-in core saves a version file collected under `saves/variants/<fileId>` into
 * the base save directory of the game that owns the file, resolving both directories the way the
 * built-in launch does. Copies only, and only onto names absent at the destination.
 */
@Singleton
class VariantSaveCarryOver @Inject constructor(
    private val emulatorConfigDao: EmulatorConfigDao,
    private val emulatorSaveConfigRepository: EmulatorSaveConfigRepository,
    private val libretroSavePathResolver: LibretroSavePathResolver
) {
    /**
     * [formerGameIds] are games that launched [file] as a variant, whose per-game save path may
     * hold the variant directory. Returns the number of files copied.
     */
    suspend fun carryOver(
        file: GameFileEntity,
        owner: GameEntity,
        formerGameIds: Set<Long>
    ): Int = withContext(Dispatchers.IO) {
        val besideRom = emulatorSaveConfigRepository
            .getByEmulator(EmulatorRegistry.BUILTIN_ID)?.savesBesideRom == true
        val romDir = file.localPath?.let { File(it).parent }
        val platformBase = libretroSavePathResolver.liveSaveBaseDir(
            platformId = owner.platformId,
            besideRomDir = if (besideRom) romDir else null
        )
        val destination = perGameSaveDir(owner.id) ?: platformBase
        val sourceBases = (listOf(platformBase, destination) + formerGameIds.mapNotNull { perGameSaveDir(it) })
            .distinctBy { it.absolutePath }

        var copied = 0
        for (base in sourceBases) {
            val variantDir = File(base, "variants/${file.id}")
            if (!variantDir.isDirectory) continue
            variantDir.walkTopDown().filter { it.isFile }.forEach { source ->
                val target = File(destination, source.relativeTo(variantDir).path)
                if (target.exists()) return@forEach
                try {
                    target.parentFile?.mkdirs()
                    source.copyTo(target, overwrite = false)
                    copied++
                    Logger.info(
                        TAG,
                        "carryOver: copied ${source.absolutePath} -> ${target.absolutePath} " +
                            "(${source.length()} bytes) for game ${owner.id}; original left in place"
                    )
                } catch (e: Exception) {
                    Logger.warn(TAG, "carryOver: copy failed ${source.absolutePath} -> ${target.absolutePath}: ${e.message}")
                }
            }
        }
        copied
    }

    private suspend fun perGameSaveDir(gameId: Long): File? =
        emulatorConfigDao.getSavePathForGame(gameId)?.takeIf { it.isNotBlank() }?.let(::File)
}
