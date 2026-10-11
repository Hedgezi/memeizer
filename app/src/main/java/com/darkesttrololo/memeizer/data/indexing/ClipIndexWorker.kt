package com.darkesttrololo.memeizer.data.indexing

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.darkesttrololo.memeizer.MemeizerApp
import com.darkesttrololo.memeizer.data.clip.ClipRepository
import kotlinx.coroutines.CancellationException

class ClipIndexWorker(
    context: Context,
    params: WorkerParameters,
    private val repository: ClipRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        if (repository.indexBatch()) {
            (applicationContext as MemeizerApp).container.indexScheduler.enqueueClipIndexingAndAwait()
        }
        Result.success()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(workDataOf("error" to "${error.javaClass.simpleName}: ${error.message}".take(1500)))
    }

    companion object { const val WORK_NAME = "meme_clip_indexing" }
}
