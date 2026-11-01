package com.nendo.argosy.ui.screens.common

import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.download.DownloadState
import com.nendo.argosy.ui.common.toIndicator
import com.nendo.argosy.ui.screens.home.GameDownloadIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What every game card draws over its cover while a transfer is in flight, keyed by game id.
 *
 * Shared rather than owned by one screen: a download is started from wherever the game is, and the
 * card that started it has to answer for it on Home, in the library and on the companion screen.
 */
@Singleton
class DownloadIndicatorSource @Inject constructor(
    private val downloadManager: DownloadManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val rommIndicators: Flow<Map<Long, GameDownloadIndicator>> =
        downloadManager.state.map { downloadState ->
            val indicators = mutableMapOf<Long, GameDownloadIndicator>()

            downloadState.activeDownloads.forEach { download ->
                indicators[download.gameId] = when (download.state) {
                    DownloadState.EXTRACTING,
                    DownloadState.MOVING -> GameDownloadIndicator(
                        isExtracting = true,
                        progress = download.extractionPercent
                    )
                    else -> GameDownloadIndicator(
                        isDownloading = true,
                        progress = download.progressPercent
                    )
                }
            }

            downloadState.queue.forEach { download ->
                if (download.gameId in indicators) return@forEach
                val indicator = download.state.toIndicator(
                    download.progressPercent, download.extractionPercent
                )
                if (indicator.isShown) {
                    indicators[download.gameId] = indicator
                }
            }

            indicators
        }

    val indicators: StateFlow<Map<Long, GameDownloadIndicator>> =
        rommIndicators
            .stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE_MS), emptyMap())
}

private const val SUBSCRIPTION_GRACE_MS = 5_000L
