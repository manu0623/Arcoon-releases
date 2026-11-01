package com.nendo.argosy.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nendo.argosy.data.repository.RetroAchievementsRepository
import com.nendo.argosy.util.Logger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class RAPatchBackfillWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val raRepository: RetroAchievementsRepository,
    private val backfill: RAPatchBackfill
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "RAPatchBackfillWorker"
        private const val WORK_NAME = "ra_patch_backfill"
        private const val BACKOFF_MINUTES = 5L

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<RAPatchBackfillWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        if (!raRepository.isLoggedIn()) {
            Logger.debug(TAG, "Not logged in to RA, nothing to backfill")
            return Result.success()
        }
        return when (backfill.run()) {
            RAPatchBackfill.Outcome.COMPLETE -> Result.success()
            RAPatchBackfill.Outcome.RETRY_LATER -> Result.retry()
        }
    }
}
