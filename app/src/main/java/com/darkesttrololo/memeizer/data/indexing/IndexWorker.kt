package com.darkesttrololo.memeizer.data.indexing

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters

class IndexWorker(
    context: Context,
    params: WorkerParameters,
    private val indexRepository: IndexRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        indexRepository.indexSelectedFolders()
    }.fold(
        onSuccess = { Result.success() },
        onFailure = { Result.failure() },
    )

    companion object {
        const val UNIQUE_WORK_NAME = "meme_indexing"
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
