package com.darkesttrololo.memeizer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.darkesttrololo.memeizer.data.indexing.IndexWorker

class DebugReindexReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val forceReindex = intent.getBooleanExtra(EXTRA_FORCE_REINDEX, true)
        val request = OneTimeWorkRequestBuilder<IndexWorker>()
            .setInputData(workDataOf(IndexWorker.KEY_FORCE_REINDEX to forceReindex))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            IndexWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    companion object {
        const val ACTION_REINDEX = "com.darkesttrololo.memeizer.DEBUG_REINDEX"
        const val EXTRA_FORCE_REINDEX = "force_reindex"
    }
}
