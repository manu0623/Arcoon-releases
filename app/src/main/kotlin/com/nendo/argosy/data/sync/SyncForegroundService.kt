package com.nendo.argosy.data.sync

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.nendo.argosy.ArgosyActivity
import com.nendo.argosy.R
import com.nendo.argosy.core.service.ServiceNotificationIds
import com.nendo.argosy.core.service.startForegroundServiceSafely
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.SyncProgress
import com.nendo.argosy.data.repository.SaveSyncRepository
import dagger.hilt.android.AndroidEntryPoint
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SyncForegroundService : Service() {

    override fun attachBaseContext(newBase: Context) {
        val tag = com.nendo.argosy.data.preferences.SessionStateStore(newBase).getAppLanguage()
        super.attachBaseContext(com.nendo.argosy.core.locale.LocaleHelper.wrap(newBase, tag))
    }

    @Inject
    lateinit var saveSyncRepository: SaveSyncRepository

    @Inject
    lateinit var romMRepository: RomMRepository

    private val serviceScope = SafeCoroutineScope(Dispatchers.Main, "SyncForegroundService")
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
        startForegroundWithNotification(getString(R.string.sync_service_preparing), 0, 0)
        observeSyncState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(PowerManager::class.java)
        val lock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)
        wakeLock = lock
        serviceScope.launch {
            while (true) {
                if (!lock.isHeld) lock.acquire(WAKELOCK_LEASE_MS)
                delay(WAKELOCK_RENEW_MS)
                if (lock.isHeld) lock.release()
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    private fun observeSyncState() {
        serviceScope.launch {
            combine(
                saveSyncRepository.syncQueueState,
                romMRepository.syncProgress
            ) { saveState, libraryProgress ->
                Pair(saveState, libraryProgress)
            }.distinctUntilChanged { (oldSave, oldLib), (newSave, newLib) ->
                oldSave.completedCount == newSave.completedCount &&
                    oldSave.operations.size == newSave.operations.size &&
                    oldSave.currentOperation == newSave.currentOperation &&
                    oldSave.hasPendingWork() == newSave.hasPendingWork() &&
                    oldLib.isSyncing == newLib.isSyncing &&
                    oldLib.currentPlatform == newLib.currentPlatform &&
                    oldLib.gamesTotal == newLib.gamesTotal &&
                    oldLib.gamesDone / GAMES_NOTIFICATION_STEP ==
                    newLib.gamesDone / GAMES_NOTIFICATION_STEP
            }.collect { (saveState, libraryProgress) ->
                val hasSaveWork = saveState.hasPendingWork()

                if (!hasSaveWork && !libraryProgress.isSyncing) {
                    stopSelf()
                    return@collect
                }

                if (libraryProgress.isSyncing) {
                    renderLibraryRow(libraryProgress)
                } else if (hasSaveWork) {
                    val current = saveState.currentOperation
                    if (current != null) {
                        val title = when (current.direction) {
                            SyncDirection.UPLOAD ->
                                getString(R.string.sync_service_uploading, current.gameName)
                            SyncDirection.DOWNLOAD ->
                                getString(R.string.sync_service_downloading, current.gameName)
                        }
                        updateNotification(title, saveState.completedCount, saveState.operations.size)
                    } else {
                        updateNotification(
                            getString(R.string.sync_service_saves),
                            saveState.completedCount,
                            saveState.operations.size
                        )
                    }
                }
            }
        }
    }

    private fun renderLibraryRow(progress: SyncProgress) {
        val platform = progress.currentPlatform
        if (platform.isEmpty()) {
            updateNotification(getString(R.string.sync_service_library), 0, 0)
            return
        }
        if (progress.gamesTotal <= 0) {
            updateNotification(getString(R.string.sync_service_platform, platform), 0, 0)
            return
        }
        val label = if (progress.platformsTotal > 1) {
            getString(
                R.string.sync_service_platform_of,
                platform,
                (progress.platformsDone + 1).coerceAtMost(progress.platformsTotal),
                progress.platformsTotal
            )
        } else {
            platform
        }
        updateNotification(
            getString(R.string.sync_service_platform_games, label, progress.gamesDone, progress.gamesTotal),
            progress.gamesDone,
            progress.gamesTotal
        )
    }

    private fun startForegroundWithNotification(
        contentText: String,
        progress: Int,
        maxProgress: Int
    ) {
        val notification = buildNotification(contentText, progress, maxProgress)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(
        contentText: String,
        progress: Int,
        maxProgress: Int
    ) {
        val notification = buildNotification(contentText, progress, maxProgress)
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(
        contentText: String,
        progress: Int,
        maxProgress: Int
    ) = NotificationCompat.Builder(this, SyncNotificationChannel.CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_helm)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(contentText)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setProgress(maxProgress, progress, progress == 0 && maxProgress == 0)
        .setContentIntent(createContentIntent())
        .build()

    private fun createContentIntent(): PendingIntent {
        val intent = Intent(this, ArgosyActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    companion object {
        private const val NOTIFICATION_ID = ServiceNotificationIds.SYNC
        private const val WAKELOCK_TAG = "argosy:sync_wakelock"
        private const val WAKELOCK_LEASE_MS = 10 * 60 * 1000L
        private const val WAKELOCK_RENEW_MS = 5 * 60 * 1000L
        private const val GAMES_NOTIFICATION_STEP = 25

        fun start(context: Context) {
            context.startForegroundServiceSafely(Intent(context, SyncForegroundService::class.java))
        }
    }
}
