package com.darkesttrololo.memeizer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.darkesttrololo.memeizer.data.indexing.IndexScheduler

class DebugReindexReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val forceReindex = intent.getBooleanExtra(EXTRA_FORCE_REINDEX, true)
        IndexScheduler(context).enqueueManualIndexing(forceReindex = forceReindex, replace = true)
    }

    companion object {
        const val ACTION_REINDEX = "com.darkesttrololo.memeizer.DEBUG_REINDEX"
        const val EXTRA_FORCE_REINDEX = "force_reindex"
    }
}
