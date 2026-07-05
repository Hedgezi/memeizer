package com.darkesttrololo.memeizer.data.indexing

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

class IndexScheduler(context: Context) {
    private val appContext = context.applicationContext

    fun schedulePeriodicIndexing() {
        val request = PeriodicWorkRequestBuilder<IndexWorker>(24, TimeUnit.HOURS)
            .setConstraints(backgroundIndexingConstraints())
            .setInputData(workDataOf(IndexWorker.KEY_FORCE_REINDEX to false))
            .build()

        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            IndexWorker.PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun enqueueManualIndexing(forceReindex: Boolean, replace: Boolean) {
        val request = OneTimeWorkRequestBuilder<IndexWorker>()
            .setInputData(workDataOf(IndexWorker.KEY_FORCE_REINDEX to forceReindex))
            .build()

        WorkManager.getInstance(appContext).enqueueUniqueWork(
            IndexWorker.MANUAL_WORK_NAME,
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private fun backgroundIndexingConstraints(): Constraints = Constraints.Builder()
        .setRequiresCharging(true)
        .setRequiresBatteryNotLow(true)
        .setRequiresStorageNotLow(true)
        .setRequiresDeviceIdle(true)
        .build()
}
