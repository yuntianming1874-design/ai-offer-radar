package com.aioffer.radar.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aioffer.radar.AiOfferRadarApplication
import com.aioffer.radar.data.SyncResult
import java.util.concurrent.TimeUnit

class OfferSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as AiOfferRadarApplication).container
        return when (container.syncService.sync()) {
            is SyncResult.Success -> Result.success()
            is SyncResult.Failure -> Result.retry()
        }
    }
}

object OfferSyncScheduler {
    const val UNIQUE_WORK_NAME = "offer_periodic_sync"

    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            createRequest()
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        if (enabled) schedule(context) else cancel(context)
    }

    internal fun createRequest() = PeriodicWorkRequestBuilder<OfferSyncWorker>(
        repeatInterval = 15,
        repeatIntervalTimeUnit = TimeUnit.MINUTES
    ).setConstraints(
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    ).setBackoffCriteria(
        BackoffPolicy.EXPONENTIAL,
        30,
        TimeUnit.SECONDS
    ).build()
}
