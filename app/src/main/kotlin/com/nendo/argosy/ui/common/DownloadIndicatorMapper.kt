package com.nendo.argosy.ui.common

import com.nendo.argosy.data.download.DownloadState
import com.nendo.argosy.ui.screens.home.GameDownloadIndicator

fun DownloadState.toIndicator(progressPercent: Float, extractionPercent: Float): GameDownloadIndicator =
    when (this) {
        DownloadState.DOWNLOADING -> GameDownloadIndicator(isDownloading = true, progress = progressPercent)
        DownloadState.EXTRACTING,
        DownloadState.MOVING -> GameDownloadIndicator(isExtracting = true, progress = extractionPercent)
        DownloadState.PAUSED -> GameDownloadIndicator(isPaused = true, progress = progressPercent)
        DownloadState.QUEUED -> GameDownloadIndicator(isQueued = true)
        else -> GameDownloadIndicator.NONE
    }
