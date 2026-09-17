package com.darkesttrololo.memeizer.data.indexing

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.darkesttrololo.memeizer.MemeizerApp

class IndexWorker(
    context: Context,
    params: WorkerParameters,
    private val indexRepository: IndexRepository,
) : CoroutineWorker(context, params) {
    constructor(context: Context, params: WorkerParameters) : this(
        context,
        params,
        (context.applicationContext as MemeizerApp).container.indexRepository,
    )

    override suspend fun doWork(): Result = runCatching {
        indexRepository.indexSelectedFolders(inputData.getBoolean(KEY_FORCE_REINDEX, false))
    }.fold(
        onSuccess = { Result.success() },
        onFailure = {
            if (it is kotlinx.coroutines.CancellationException) throw it
            Result.failure(workDataOf("error" to "${it.javaClass.simpleName}: ${it.message}".take(1500)))
        },
    )

    companion object {
        const val MANUAL_WORK_NAME = "meme_manual_indexing"
        const val PERIODIC_WORK_NAME = "meme_periodic_indexing"
        const val KEY_FORCE_REINDEX = "force_reindex"
    }
}

class MemeizerWorkerFactory(
    private val indexRepository: IndexRepository,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        IndexWorker::class.java.name -> IndexWorker(appContext, workerParameters, indexRepository)
        else -> null
    }
}
