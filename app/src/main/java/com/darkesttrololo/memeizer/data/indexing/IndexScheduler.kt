package com.darkesttrololo.memeizer.data.indexing

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkInfo
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

class IndexScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val workManager by lazy { WorkManager.getInstance(appContext) }

    val workState: Flow<IndexWorkState> = flow {
        val manager = workManager
        emitAll(
            combine(
                manager.getWorkInfosForUniqueWorkFlow(IndexWorker.MANUAL_WORK_NAME),
                manager.getWorkInfosForUniqueWorkFlow(IndexWorker.PERIODIC_WORK_NAME),
            ) { manual, periodic ->
                indexWorkState(manual, periodic)
            },
        )
    }

    fun schedulePeriodicIndexing() {
        val request = PeriodicWorkRequestBuilder<IndexWorker>(24, TimeUnit.HOURS)
            .setConstraints(backgroundIndexingConstraints())
            .setInputData(workDataOf(IndexWorker.KEY_FORCE_REINDEX to false))
            .build()

        workManager.enqueueUniquePeriodicWork(
            IndexWorker.PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun enqueueManualIndexing(forceReindex: Boolean, replace: Boolean) {
        val sequence = nextSequence()
        val request = OneTimeWorkRequestBuilder<IndexWorker>()
            .setInputData(workDataOf(IndexWorker.KEY_FORCE_REINDEX to forceReindex))
            .addTag("$MANUAL_SEQUENCE_TAG_PREFIX$sequence")
            .build()

        workManager.enqueueUniqueWork(
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

    private fun nextSequence(): Long = sequence.updateAndGet { previous ->
        maxOf(System.currentTimeMillis(), previous + 1)
    }

    private companion object {
        val sequence = AtomicLong(0)
    }
}

data class IndexWorkState(
    val isLoading: Boolean = false,
    val hasFailed: Boolean = false,
)

internal fun indexWorkState(
    manualWork: List<WorkInfo>,
    periodicWork: List<WorkInfo>,
): IndexWorkState {
    val currentManual = selectCurrentManualWork(manualWork)
    val manualLoading = currentManual?.state in setOf(
        WorkInfo.State.ENQUEUED,
        WorkInfo.State.RUNNING,
        WorkInfo.State.BLOCKED,
    )
    val periodicRunning = periodicWork.any { it.state == WorkInfo.State.RUNNING }
    return IndexWorkState(
        isLoading = manualLoading || periodicRunning,
        hasFailed = currentManual?.state == WorkInfo.State.FAILED ||
            periodicWork.any { it.state == WorkInfo.State.FAILED },
    )
}

private fun selectCurrentManualWork(work: List<WorkInfo>): WorkInfo? {
    val sequenced = work.mapNotNull { info ->
        info.tags.firstNotNullOfOrNull { tag ->
            tag.removePrefix(MANUAL_SEQUENCE_TAG_PREFIX).takeIf { it != tag }?.toLongOrNull()
        }?.let { it to info }
    }
    if (sequenced.isNotEmpty()) return sequenced.maxBy { it.first }.second

    // Compatibility with work enqueued by versions that did not attach a sequence tag.
    return work.firstOrNull { !it.state.isFinished }
        ?: work.firstOrNull { it.state != WorkInfo.State.CANCELLED }
        ?: work.firstOrNull()
}

private const val MANUAL_SEQUENCE_TAG_PREFIX = "meme_manual_sequence:"
