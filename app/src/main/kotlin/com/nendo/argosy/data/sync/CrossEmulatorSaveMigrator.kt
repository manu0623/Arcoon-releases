package com.nendo.argosy.data.sync

import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationDuration
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.repository.ActiveSaveRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncApiClient
import com.nendo.argosy.data.repository.SaveSyncOrchestrator
import com.nendo.argosy.data.sync.platform.SigilCollect
import com.nendo.argosy.data.sync.platform.SigilProbe
import com.nendo.argosy.data.sync.platform.SigilRestore
import com.nendo.argosy.data.sync.platform.SigilRoute
import com.nendo.argosy.data.sync.platform.SigilSaveHandler
import com.nendo.argosy.util.Logger
import com.nendo.sigil.SigilException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrossEmulatorSaveMigrator @Inject constructor(
    private val gameDao: GameDao,
    private val activeSaveRepository: ActiveSaveRepository,
    private val saveCacheDao: SaveCacheDao,
    private val emulatorResolver: EmulatorResolver,
    private val sigilSaveHandler: SigilSaveHandler,
    private val saveCacheManager: SaveCacheManager,
    private val orchestrator: SaveSyncOrchestrator,
    private val syncPreferencesRepository: SyncPreferencesRepository,
    private val saveOwnershipTracker: SaveOwnershipTracker,
    private val notificationManager: NotificationManager
) {
    sealed interface Outcome {
        data object Untouched : Outcome
        data object AlreadyPlaced : Outcome
        data object Placed : Outcome
        data object Adopted : Outcome
        data class Refused(val refusal: Refusal) : Outcome
    }

    enum class Refusal {
        NO_ROUTE,
        UNREADABLE,
        BACKUP_FAILED,
        CONFLICT,
        NO_SPACE,
        NO_TARGET,
        AMBIGUOUS,
        DAMAGED,
        UNSUPPORTED_FORMAT,
        EXISTS,
        IO
    }

    private class Launch(
        val game: GameEntity,
        val active: SaveCacheEntity,
        val targetEmulatorId: String,
        val secureSaves: Boolean,
        val ownerUserId: Long?,
        val noticeShown: Boolean
    )

    private class Migration(
        val launch: Launch,
        val source: SigilRoute,
        val target: SigilRoute
    ) {
        val game: GameEntity get() = launch.game
        val active: SaveCacheEntity get() = launch.active
        val targetEmulatorId: String get() = launch.targetEmulatorId
    }

    suspend fun migrateBeforeLaunch(gameId: Long): Outcome = try {
        migrate(gameId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.error(TAG, "[SaveSync] MIGRATE gameId=$gameId | failed, launching with the saves as they are", e)
        Outcome.Untouched
    }

    private suspend fun sourceSave(gameId: Long, ownerUserId: Long?): SaveCacheEntity? {
        activeSaveRepository.getActiveRow(gameId)?.let { return it }
        val chosenChannel = activeSaveRepository.getActiveChannel(gameId)
        return if (chosenChannel != null) {
            saveCacheDao.getMostRecentInChannel(gameId, ownerUserId, chosenChannel)
        } else {
            saveCacheDao.getMostRecentNonRollback(gameId, ownerUserId)
        }
    }

    private suspend fun migrate(gameId: Long): Outcome = withContext(Dispatchers.IO) {
        val game = gameDao.getById(gameId) ?: return@withContext Outcome.Untouched
        val ownerUserId = syncPreferencesRepository.getRommUserId()
        val secureSaves = syncPreferencesRepository.isSecureSaves()
        val active = sourceSave(gameId, ownerUserId) ?: return@withContext Outcome.Untouched
        if (SaveSyncOrchestrator.isStateChannel(active.channelName ?: SaveSyncApiClient.AUTOSAVE_SLOT_NAME)) {
            return@withContext Outcome.Untouched
        }
        if (active.isHardcore || saveCacheDao.getMostRecent(gameId, ownerUserId)?.isHardcore == true) {
            Logger.debug(TAG, "[SaveSync] MIGRATE gameId=$gameId | hardcore save in play, leaving it to the hardcore gate")
            return@withContext Outcome.Untouched
        }
        val targetEmulatorId = emulatorResolver.getEmulatorPackageForGame(game.id, game.platformId, game.platformSlug)
            ?.let { emulatorResolver.resolveEmulatorId(it) }
            ?: return@withContext Outcome.Untouched
        if (active.emulatorId == targetEmulatorId) return@withContext Outcome.Untouched

        val launch = Launch(
            game, active, targetEmulatorId, secureSaves, ownerUserId,
            noticeShown = syncPreferencesRepository.isSaveMigrationNoticeShown(active.id)
        )
        val source = sigilSaveHandler.localRoute(gameId, active.emulatorId)
        val target = sigilSaveHandler.localRoute(gameId, targetEmulatorId)
        if (source == null || target == null || source.isGeneric() != target.isGeneric()) {
            Logger.info(TAG, "[SaveSync] MIGRATE gameId=$gameId | no Sigil conversion between ${active.emulatorId} (${source?.layout}) and $targetEmulatorId (${target?.layout}), placing it the way a launch always has")
            return@withContext withContext(NonCancellable) { placeLegacy(launch, expectedExtension = null, onFailure = Refusal.IO) }
        }
        if (source.layout == target.layout && source.root == target.root) return@withContext Outcome.Untouched

        val migration = Migration(launch, source, target)
        Logger.info(TAG, "[SaveSync] MIGRATE gameId=$gameId | active cache id=${active.id} from ${active.emulatorId}/${source.layout} into $targetEmulatorId/${target.layout} | root=${target.root}")
        when (val probe = sigilSaveHandler.probe(target, gameId)) {
            is SigilProbe.Unreadable -> {
                Logger.warn(TAG, "[SaveSync] MIGRATE gameId=$gameId | target save root unreadable: ${probe.reason}")
                refuse(launch, Refusal.UNREADABLE)
            }
            SigilProbe.Absent -> withContext(NonCancellable) { place(migration) }
            is SigilProbe.Found -> decide(migration, probe)
        }
    }

    private fun SigilRoute.isGeneric(): Boolean = layout == SigilSaveHandler.GENERIC_LIBRETRO_LAYOUT

    private suspend fun decide(migration: Migration, probe: SigilProbe.Found): Outcome {
        val active = migration.active
        val activeForms = setOfNotNull(active.contentHash, active.identityHash).filterTo(HashSet()) { it.isNotEmpty() }
        if (probe.unit.contentHash in activeForms || probe.unit.identityHash in activeForms) return Outcome.AlreadyPlaced
        val recordedHash = saveOwnershipTracker.recordedHash(migration.target.root, migration.targetEmulatorId)
        if (probe.anchored && !probe.changed && recordedHash != null && recordedHash == active.contentHash) {
            return Outcome.AlreadyPlaced
        }
        if (!migration.launch.secureSaves && changedOffArgosy(probe, recordedHash)) return adopt(migration)
        return withContext(NonCancellable) { protectAndPlace(migration) }
    }

    private fun changedOffArgosy(probe: SigilProbe.Found, recordedHash: String?): Boolean {
        if (probe.anchored) return probe.changed
        if (recordedHash == null) return true
        return recordedHash != probe.unit.contentHash && recordedHash != probe.unit.identityHash
    }

    private suspend fun adopt(migration: Migration): Outcome {
        val gameId = migration.game.id
        val path = when (val lookup = orchestrator.lookupSystemSave(migration.game, migration.targetEmulatorId)) {
            is SaveLookup.Found -> lookup.path
            is SaveLookup.Unreadable -> return refuse(migration.launch, Refusal.UNREADABLE)
            SaveLookup.Absent -> {
                Logger.warn(TAG, "[SaveSync] MIGRATE gameId=$gameId | target save changed outside Argosy but no save path resolves to cache it")
                return Outcome.Untouched
            }
        }
        if (saveOwnershipTracker.claim(path, migration.targetEmulatorId) is SaveClaim.Foreign) {
            Logger.info(TAG, "[SaveSync] MIGRATE gameId=$gameId | target save belongs to another account, leaving it | path=$path")
            return Outcome.Untouched
        }
        Logger.info(TAG, "[SaveSync] MIGRATE gameId=$gameId | target save changed outside Argosy, adopting it; active cache id=${migration.active.id} stays as history | path=$path")
        val cached = withContext(NonCancellable) {
            saveCacheManager.cacheCurrentSave(
                gameId = gameId,
                emulatorId = migration.targetEmulatorId,
                savePath = path,
                channelName = migration.active.channelName,
                needsRemoteSync = true
            )
        }
        return if (cached.success) Outcome.Adopted else Outcome.Untouched
    }

    private suspend fun protectAndPlace(migration: Migration): Outcome {
        val path = when (val lookup = orchestrator.lookupSystemSave(migration.game, migration.targetEmulatorId)) {
            is SaveLookup.Found -> lookup.path
            is SaveLookup.Unreadable -> return refuse(migration.launch, Refusal.UNREADABLE)
            SaveLookup.Absent -> return refuse(migration.launch, Refusal.BACKUP_FAILED)
        }
        val rollback = saveCacheManager.cacheAsRollback(migration.game.id, migration.targetEmulatorId, path)
        if (!rollback.success) {
            Logger.warn(TAG, "[SaveSync] MIGRATE gameId=${migration.game.id} | could not back up the target save, writing nothing | path=$path")
            return refuse(migration.launch, Refusal.BACKUP_FAILED)
        }
        return place(migration)
    }

    private suspend fun place(migration: Migration): Outcome {
        val gameId = migration.game.id
        val active = migration.active
        val unit = when (val prepared = unitFor(migration)) {
            is SigilCollect.Found -> prepared.data
            else -> {
                Logger.warn(TAG, "[SaveSync] MIGRATE gameId=$gameId | cache id=${active.id} (format=${active.saveFormat}) gave no unit under ${migration.source.layout}: $prepared")
                return placeSameShapeOrRefuse(migration)
            }
        }
        return when (val restored = sigilSaveHandler.restoreInto(migration.target, gameId, unit)) {
            is SigilRestore.Restored -> {
                saveCacheManager.recordPlacement(active, migration.target.root, migration.targetEmulatorId, migration.launch.secureSaves)
                Logger.info(TAG, "[SaveSync] MIGRATE gameId=$gameId | placed cache id=${active.id} for ${migration.targetEmulatorId}")
                Outcome.Placed
            }
            is SigilRestore.Refused -> {
                Logger.warn(TAG, "[SaveSync] MIGRATE gameId=$gameId | Sigil refused the restore: ${restored.reason}")
                val refusal = refusalFor(restored.code)
                if (refusal == Refusal.UNSUPPORTED_FORMAT) placeSameShapeOrRefuse(migration) else refuse(migration.launch, refusal)
            }
            SigilRestore.NotRouted -> refuse(migration.launch, Refusal.NO_ROUTE)
        }
    }

    private suspend fun placeSameShapeOrRefuse(migration: Migration): Outcome {
        val extension = singleNativeFileExtension(migration.active)
            ?: return refuse(migration.launch, Refusal.UNSUPPORTED_FORMAT)
        Logger.info(TAG, "[SaveSync] MIGRATE gameId=${migration.game.id} | Sigil cannot convert cache id=${migration.active.id}, placing the single .$extension file the way a launch always has")
        return placeLegacy(migration.launch, extension, onFailure = Refusal.UNSUPPORTED_FORMAT)
    }

    private suspend fun singleNativeFileExtension(active: SaveCacheEntity): String? {
        if (active.saveFormat == SaveCacheEntity.FORMAT_NEUTRAL) return null
        val name = saveCacheManager.nativeFiles(active)?.keys?.singleOrNull() ?: return null
        return File(name).extension.takeIf { it.isNotEmpty() }
    }

    private suspend fun placeLegacy(launch: Launch, expectedExtension: String?, onFailure: Refusal): Outcome {
        val check = orchestrator.reconcileDisk(
            launch.game, launch.targetEmulatorId, launch.active.channelName, launch.active,
            launch.secureSaves, launch.ownerUserId, expectedExtension
        )
        Logger.info(TAG, "[SaveSync] MIGRATE gameId=${launch.game.id} | placement without Sigil for ${launch.targetEmulatorId}: $check")
        return when (check) {
            SaveSyncOrchestrator.DiskCheck.Restored -> Outcome.Placed
            SaveSyncOrchestrator.DiskCheck.Matches -> Outcome.AlreadyPlaced
            SaveSyncOrchestrator.DiskCheck.Adopted -> Outcome.Adopted
            SaveSyncOrchestrator.DiskCheck.Untouched -> Outcome.Untouched
            SaveSyncOrchestrator.DiskCheck.Failed -> refuse(launch, onFailure)
            is SaveSyncOrchestrator.DiskCheck.Unreadable -> refuse(launch, Refusal.UNREADABLE)
        }
    }

    private suspend fun unitFor(migration: Migration): SigilCollect {
        val active = migration.active
        if (active.saveFormat == SaveCacheEntity.FORMAT_NEUTRAL) {
            val file = saveCacheManager.getCacheFile(active)
            return if (file.isFile) SigilCollect.Found(file.readBytes(), file.name, active.contentHash.orEmpty(), active.identityHash.orEmpty())
            else SigilCollect.Absent
        }
        val files = saveCacheManager.nativeFiles(active) ?: return SigilCollect.Absent
        return sigilSaveHandler.convertNative(migration.source, migration.game.id, files)
    }

    private suspend fun refuse(launch: Launch, refusal: Refusal): Outcome {
        if (launch.noticeShown) {
            Logger.info(TAG, "[SaveSync] MIGRATE gameId=${launch.game.id} | $refusal for cache id=${launch.active.id}, already told the user")
            return Outcome.Refused(refusal)
        }
        notificationManager.show(
            title = NotificationText.Res(R.string.sync_migration_refused_title, listOf(launch.game.title)),
            subtitle = NotificationText.Res(messageFor(refusal)),
            type = NotificationType.WARNING,
            duration = NotificationDuration.LONG,
            key = "save-migration-${launch.game.id}"
        )
        syncPreferencesRepository.markSaveMigrationNoticeShown(launch.active.id)
        return Outcome.Refused(refusal)
    }

    private fun refusalFor(code: Int?): Refusal = when (code) {
        SigilException.CONFLICT -> Refusal.CONFLICT
        SigilException.NO_SPACE -> Refusal.NO_SPACE
        SigilException.NO_TARGET -> Refusal.NO_TARGET
        SigilException.AMBIGUOUS -> Refusal.AMBIGUOUS
        SigilException.DAMAGED -> Refusal.DAMAGED
        SigilException.UNSUPPORTED_FORMAT -> Refusal.UNSUPPORTED_FORMAT
        SigilException.EXISTS -> Refusal.EXISTS
        else -> Refusal.IO
    }

    private fun messageFor(refusal: Refusal): Int = when (refusal) {
        Refusal.NO_ROUTE -> R.string.sync_migration_refused_no_route
        Refusal.UNREADABLE -> R.string.sync_migration_refused_unreadable
        Refusal.BACKUP_FAILED -> R.string.sync_migration_refused_backup_failed
        Refusal.CONFLICT -> R.string.sync_migration_refused_conflict
        Refusal.NO_SPACE -> R.string.sync_migration_refused_no_space
        Refusal.NO_TARGET -> R.string.sync_migration_refused_no_target
        Refusal.AMBIGUOUS -> R.string.sync_migration_refused_ambiguous
        Refusal.DAMAGED -> R.string.sync_migration_refused_damaged
        Refusal.UNSUPPORTED_FORMAT -> R.string.sync_migration_refused_unsupported_format
        Refusal.EXISTS -> R.string.sync_migration_refused_exists
        Refusal.IO -> R.string.sync_migration_refused_io
    }

    private companion object {
        const val TAG = "CrossEmulatorSaveMigrator"
    }
}
