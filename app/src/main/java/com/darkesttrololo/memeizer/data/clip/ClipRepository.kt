package com.darkesttrololo.memeizer.data.clip

import android.net.Uri
import androidx.room.withTransaction
import com.darkesttrololo.memeizer.data.db.ClipEmbeddingEntity
import com.darkesttrololo.memeizer.data.db.ClipPendingRow
import com.darkesttrololo.memeizer.data.db.MemeizerDatabase
import com.darkesttrololo.memeizer.data.search.SearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ClipRepository(
    private val database: MemeizerDatabase,
    private val engine: ClipEmbeddingEngine,
    private val clock: () -> Long = { android.os.SystemClock.elapsedRealtime() },
) {
    private val retryMutex = kotlinx.coroutines.sync.Mutex()
    val modelError = MutableStateFlow<String?>(null)
    val progress = database.clipDao().observeProgress(engine.modelVersion)

    /** Await scheduling even if a previous submission reset rows but failed to enqueue them. */
    suspend fun retryFailedImages(enqueue: suspend () -> Unit): Int = withContext(Dispatchers.IO) {
        if (!retryMutex.tryLock()) return@withContext 0
        try {
            val reset = database.clipIndexingMutex.withLock {
                database.clipDao().resetFailures(engine.modelVersion)
            }
            enqueue()
            reset
        } finally {
            retryMutex.unlock()
        }
    }

    /** Returns true when another bounded run is needed. Failed files wait for an explicit retry or content change. */
    suspend fun indexBatch(): Boolean = withContext(Dispatchers.IO) {
        database.clipIndexingMutex.withLock {
            val started = clock()
            val pending = database.clipDao().pending(engine.modelVersion)
            if (pending.isEmpty()) return@withLock false
            try {
                engine.initialize()
                modelError.value = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                modelError.value = error.message
                throw ClipModelException("Cannot load local CLIP model", error)
            }
            for (row in pending) {
                currentCoroutineContext().ensureActive()
                if (clock() - started >= MAX_RUN_MILLIS) break
                try {
                    val vector = ClipVectors.normalize(engine.embedImage(Uri.parse(row.image.uri)), engine.dimension)
                    saveIfCurrent(row, ClipVectors.encode(vector), "READY", null)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: ClipModelException) {
                    modelError.value = error.message
                    throw error
                } catch (error: Exception) {
                    saveIfCurrent(row, null, "FAILED", "${error.javaClass.simpleName}: ${error.message}".take(1500))
                }
            }
            database.clipDao().pending(engine.modelVersion, 1).isNotEmpty()
        }
    }

    private suspend fun saveIfCurrent(row: ClipPendingRow, vector: ByteArray?, status: String, error: String?) {
        currentCoroutineContext().ensureActive()
        database.withTransaction {
            val image = database.imageDao().findById(row.image.id) ?: return@withTransaction
            if (!image.active || image.folderId != row.image.folderId || image.contentKey != row.image.contentKey ||
                !database.folderDao().isCurrentSelection(image.folderId, row.selectionVersion)
            ) return@withTransaction
            database.clipDao().save(ClipEmbeddingEntity(
                image.id, engine.modelVersion, image.contentKey, engine.dimension, vector, status, error,
                System.currentTimeMillis(),
            ))
        }
    }

    fun search(query: String) = kotlinx.coroutines.flow.flow {
        val vector = try {
            ClipVectors.normalize(engine.embedText(query), engine.dimension).also { modelError.value = null }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            modelError.value = error.message
            throw ClipModelException("Cannot encode CLIP query", error)
        }
        // Room invalidates this flow on image, folder, or embedding changes, including same-count updates.
        emitAllResults(vector)
    }.flowOn(Dispatchers.IO)

    private suspend fun kotlinx.coroutines.flow.FlowCollector<List<SearchResult>>.emitAllResults(vector: FloatArray) {
        emitAll(progress.mapLatest { rank(vector) })
    }

    private suspend fun rank(query: FloatArray): List<SearchResult> = database.withTransaction {
        val top = ClipTopK<SearchResult>()
        var afterId = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val rows = database.clipDao().vectors(engine.modelVersion, afterId)
            if (rows.isEmpty()) break
            rows.forEach { row ->
                if (row.dimension == engine.dimension) {
                    ClipVectors.decode(row.vector, row.dimension)?.let { vector ->
                        top.add(row.image.id, ClipVectors.dot(query, vector), row.image.toSearchResult())
                    }
                }
            }
            afterId = rows.last().image.id
        }
        top.results()
    }

    companion object { const val MAX_RUN_MILLIS = 8 * 60 * 1000L }
}

internal fun com.darkesttrololo.memeizer.data.db.SearchResultRow.toSearchResult() = SearchResult(
    id, uri, displayName, text, mimeType, size, lastModified,
)
