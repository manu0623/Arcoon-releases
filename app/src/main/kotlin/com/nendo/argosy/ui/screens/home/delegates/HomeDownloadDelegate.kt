package com.nendo.argosy.ui.screens.home.delegates

import android.content.Context
import com.nendo.argosy.R
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.update.ApkInstallManager
import com.nendo.argosy.domain.usecase.download.DownloadResult
import com.nendo.argosy.ui.common.toNotificationText
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.core.notification.showSuccess
import com.nendo.argosy.ui.screens.common.DownloadIndicatorSource
import com.nendo.argosy.ui.screens.common.GameActionsDelegate
import com.nendo.argosy.ui.screens.home.GameDownloadIndicator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class HomeDownloadDelegate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager,
    private val gameActions: GameActionsDelegate,
    private val apkInstallManager: ApkInstallManager,
    private val notificationManager: NotificationManager,
    private val downloadIndicatorSource: DownloadIndicatorSource
) {
    val downloadIndicators: StateFlow<Map<Long, GameDownloadIndicator>> =
        downloadIndicatorSource.indicators

    private val completedGameIds = mutableSetOf<Long>()
    private var lastDownloadQueueTime = 0L
    private val downloadQueueDebounceMs = 300L

    fun observeDownloadState(scope: CoroutineScope, onNewlyCompleted: suspend () -> Unit) {
        scope.launch {
            downloadManager.state.collect { downloadState ->
                val newlyCompleted = downloadState.completed
                    .map { it.gameId }
                    .filter { it !in completedGameIds }

                if (newlyCompleted.isNotEmpty()) {
                    completedGameIds.addAll(newlyCompleted)
                    onNewlyCompleted()
                }
            }
        }
    }

    fun queueDownload(scope: CoroutineScope, gameId: Long) {
        val now = System.currentTimeMillis()
        if (now - lastDownloadQueueTime < downloadQueueDebounceMs) return
        lastDownloadQueueTime = now

        scope.launch {
            when (val result = gameActions.queueDownload(gameId)) {
                is DownloadResult.Queued -> { }
                is DownloadResult.AlreadyDownloaded -> {
                    notificationManager.showSuccess(
                        NotificationText.Res(R.string.home_notice_already_downloaded)
                    )
                }
                is DownloadResult.MultiDiscQueued -> {
                    notificationManager.showSuccess(
                        NotificationText.Plural(
                            R.plurals.home_notice_discs_queued,
                            result.discCount,
                            listOf(result.discCount)
                        )
                    )
                }
                is DownloadResult.Error -> notificationManager.showError(result.reason.toNotificationText())
                is DownloadResult.ExtractionFailed -> {
                    notificationManager.showError(
                        NotificationText.Res(R.string.home_notice_extraction_failed)
                    )
                }
            }
        }
    }

    fun installApk(scope: CoroutineScope, gameId: Long) {
        scope.launch {
            val success = apkInstallManager.installApkForGame(gameId)
            if (!success) {
                notificationManager.showError(
                    NotificationText.Res(R.string.home_notice_apk_install_failed)
                )
            }
        }
    }

    fun resumeDownload(gameId: Long) {
        downloadManager.resumeDownload(gameId)
    }

    fun deleteLocalFile(scope: CoroutineScope, gameId: Long, withSoundtrack: Boolean, onComplete: suspend () -> Unit) {
        scope.launch {
            gameActions.deleteLocalFile(gameId, withSoundtrack)
            notificationManager.showSuccess(
                NotificationText.Res(R.string.home_notice_download_deleted)
            )
            onComplete()
        }
    }
}
