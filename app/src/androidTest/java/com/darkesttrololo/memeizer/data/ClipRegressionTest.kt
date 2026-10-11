package com.darkesttrololo.memeizer.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darkesttrololo.memeizer.data.clip.*
import com.darkesttrololo.memeizer.data.db.*
import com.darkesttrololo.memeizer.data.search.SearchMode
import com.darkesttrololo.memeizer.data.search.SearchRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClipRegressionTest {
    private lateinit var db: MemeizerDatabase
    private var imageEmbedding: suspend (Uri) -> FloatArray = { floatArrayOf(1f, 0f) }
    private var load: suspend () -> Unit = {}
    private var version = "test-v1"
    private val engine = object : ClipEmbeddingEngine {
        override val modelVersion get() = version
        override val dimension = 2
        override suspend fun initialize() = load()
        override suspend fun embedImage(uri: Uri) = imageEmbedding(uri)
        override suspend fun embedText(text: String) = floatArrayOf(1f, 0f)
    }

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MemeizerDatabase::class.java).build()
    }
    @After fun close() = db.close()

    private suspend fun folder(): Long = db.folderDao().insert(IndexedFolderEntity(
        treeUri = "content://test/root", displayName = "Folder", createdAt = 1, updatedAt = 1,
    ))
    private suspend fun image(folder: Long, key: String = "a"): Long = db.imageDao().insert(IndexedImageEntity(
        folderId = folder, uri = "content://test/$key", documentKey = key, displayName = key,
        mimeType = "image/png", size = 1, lastModified = 1, contentKey = "$key:1", indexStatus = "FAILED",
        createdAt = 1, updatedAt = 1,
    ))
    private suspend fun save(id: Long, vector: FloatArray, contentKey: String? = null) {
        db.clipDao().save(ClipEmbeddingEntity(id, version, contentKey ?: db.imageDao().findById(id)!!.contentKey,
            2, ClipVectors.encode(ClipVectors.normalize(vector, 2)), "READY", null, 2))
    }
    private suspend fun search() = ClipRepository(db, engine).search("cat").first()

    @Test fun batchesResumeWithoutOcrAndDoNotChangeGalleryOrder() = runBlocking {
        val folder = folder()
        repeat(65) { image(folder, "$it") }
        val before = db.searchDao().observeGallery(100).first().map { it.id }
        val repository = ClipRepository(db, engine)
        assertTrue(repository.indexBatch())
        assertEquals(64, repository.progress.first().ready)
        assertFalse(repository.indexBatch())
        assertEquals(65, repository.progress.first().ready)
        imageEmbedding = { error("Current vector must be reused") }
        assertFalse(repository.indexBatch())
        assertEquals(before, db.searchDao().observeGallery(100).first().map { it.id })
        assertTrue(db.searchDao().search("cat*", 100).first().isEmpty())
        assertEquals(30, search().size) // OCR FAILED does not exclude CLIP.
    }

    @Test fun staleDisabledInactiveAndCorruptVectorsCannotAppear() = runBlocking {
        val folder = folder()
        val valid = image(folder, "valid")
        val stale = image(folder, "stale")
        val corrupt = image(folder, "corrupt")
        val inactive = image(folder, "inactive")
        save(valid, floatArrayOf(1f, 0f))
        save(stale, floatArrayOf(1f, 0f), "old-key")
        save(inactive, floatArrayOf(1f, 0f))
        db.imageDao().update(db.imageDao().findById(inactive)!!.copy(active = false))
        db.clipDao().save(ClipEmbeddingEntity(corrupt, version, "corrupt:1", 2, byteArrayOf(1), "READY", null, 2))
        assertEquals(listOf(valid), search().map { it.imageId })
        db.folderDao().setEnabled(folder, false, 3)
        assertTrue(search().isEmpty())
    }

    @Test fun tiesAcross256RowPagesUseAscendingIdsAndReturnThirty() = runBlocking {
        val folder = folder()
        val ids = (1..270).map { image(folder, "$it") }
        ids.forEach { save(it, floatArrayOf(1f, 0f)) }
        assertEquals(ids.take(30), search().map { it.imageId })
    }

    @Test fun folderReaddedDuringInferenceDoesNotAcceptOldResult() = runBlocking {
        val folder = folder()
        image(folder)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        imageEmbedding = { entered.complete(Unit); release.await(); floatArrayOf(1f, 0f) }
        val job = launch { ClipRepository(db, engine).indexBatch() }
        withTimeout(10_000) { entered.await() }
        db.folderDao().setEnabled(folder, false, 2)
        db.folderDao().setEnabled(folder, true, 3)
        release.complete(Unit)
        job.join()
        assertEquals(0, db.clipDao().observeProgress(version).first().ready)
    }

    @Test fun newerContentCannotBeOverwrittenByOldInference() = runBlocking {
        val folder = folder()
        val id = image(folder)
        imageEmbedding = {
            db.imageDao().updateContentKeyAndStatus(id, "new-key", "PENDING", 4)
            floatArrayOf(1f, 0f)
        }
        assertTrue(ClipRepository(db, engine).indexBatch())
        assertTrue(search().isEmpty())
    }

    @Test fun fileFailureDoesNotStopOthersOrCauseInfiniteContinuation() = runBlocking {
        val folder = folder()
        image(folder, "bad")
        image(folder, "good")
        imageEmbedding = { if (it.lastPathSegment == "bad") throw java.io.IOException("bad image") else floatArrayOf(1f, 0f) }
        val repository = ClipRepository(db, engine)
        assertFalse(repository.indexBatch())
        assertEquals(ClipProgress(2, 1, 1), repository.progress.first())
    }

    @Test fun decoderOversizeFailureDoesNotStopNeighboringValidFile() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val files = listOf(syntheticPng(context, 4097, 1), syntheticPng(context, 16, 16))
        try {
            val folder = folder()
            files.forEachIndexed { index, file ->
                val id = image(folder, "decode-$index")
                db.imageDao().update(db.imageDao().findById(id)!!.copy(uri = Uri.fromFile(file).toString()))
            }
            imageEmbedding = { uri ->
                ClipImageDecoder.decode(android.graphics.ImageDecoder.createSource(context.contentResolver, uri)).recycle()
                floatArrayOf(1f, 0f)
            }
            val repository = ClipRepository(db, engine)
            assertFalse(repository.indexBatch())
            assertEquals(ClipProgress(2, 1, 1), repository.progress.first())
            assertFalse(repository.indexBatch()) // No automatic retry loop.
        } finally { files.forEach { it.delete() } }
    }

    private suspend fun failure(id: Long, model: String = version, key: String? = null) {
        db.clipDao().save(ClipEmbeddingEntity(id, model, key ?: db.imageDao().findById(id)!!.contentKey,
            2, byteArrayOf(9), "FAILED", "synthetic failure", 2))
    }

    private fun embedding(id: Long, model: String = version): Triple<String, ByteArray?, String?> =
        db.openHelper.readableDatabase.query(
            "SELECT status, vector, error FROM clip_embeddings WHERE image_id = ? AND model_version = ?",
            arrayOf<Any>(id, model),
        ).use {
            assertTrue(it.moveToFirst())
            Triple(it.getString(0), if (it.isNull(1)) null else it.getBlob(1), if (it.isNull(2)) null else it.getString(2))
        }

    @Test fun retrySelectsOnlyCurrentActiveFailuresAndPreservesSuccessfulVectorsAndOcr() = runBlocking {
        val folder = folder()
        val failed = image(folder, "failed")
        val ready = image(folder, "ready")
        val stale = image(folder, "stale")
        val inactive = image(folder, "inactive")
        val otherModel = image(folder, "other-model")
        val disabledFolder = db.folderDao().insert(IndexedFolderEntity(
            treeUri = "content://test/disabled", displayName = "disabled", enabled = false, createdAt = 1, updatedAt = 1))
        val disabled = image(disabledFolder, "disabled")
        failure(failed)
        failure(stale, key = "old-content")
        failure(inactive)
        failure(otherModel, model = "old-model")
        failure(disabled)
        save(ready, floatArrayOf(1f, 0f))
        val readyBefore = embedding(ready).second
        db.imageDao().update(db.imageDao().findById(inactive)!!.copy(active = false))
        db.ocrDao().insert(OcrResultEntity(imageId = failed, engine = "test", language = "latin",
            text = "cat кот", confidence = null, errorMessage = null, createdAt = 1, updatedAt = 1))
        db.searchDao().insert(MemeSearchFtsEntity(imageId = failed, text = "cat кот"))
        db.imageDao().update(db.imageDao().findById(failed)!!.copy(indexStatus = "INDEXED"))
        assertEquals(1, db.clipDao().resetFailures(version))
        assertEquals(Triple("PENDING", null, null), embedding(failed))
        assertEquals(0, db.clipDao().resetFailures(version))
        assertEquals("READY", embedding(ready).first)
        assertArrayEquals(readyBefore, embedding(ready).second)
        for (id in listOf(stale, inactive, disabled)) assertEquals("FAILED", embedding(id).first)
        assertEquals("FAILED", embedding(otherModel, "old-model").first)
        assertEquals("INDEXED", db.imageDao().findById(failed)!!.indexStatus)
        assertEquals(failed, db.searchDao().search("cat*", 10).first().single().id)
        db.openHelper.readableDatabase.query("SELECT text FROM ocr_results WHERE image_id = $failed").use {
            assertTrue(it.moveToFirst()); assertEquals("cat кот", it.getString(0))
        }
    }

    @Test fun retrySchedulingFailureCanResubmitAlreadyResetRowsWithoutChangingReadyVector() = runBlocking {
        val folder = folder()
        val failed = image(folder, "failed")
        val ready = image(folder, "ready")
        failure(failed)
        save(ready, floatArrayOf(1f, 0f))
        val before = embedding(ready).second
        val repository = ClipRepository(db, engine)
        try {
            repository.retryFailedImages { throw java.io.IOException("synthetic scheduler failure") }
            fail("Scheduling failure must reach the ViewModel")
        } catch (_: java.io.IOException) { }
        assertEquals("PENDING", embedding(failed).first)
        var scheduled = false
        assertEquals(0, repository.retryFailedImages { scheduled = true })
        assertTrue(scheduled)
        assertArrayEquals(before, embedding(ready).second)
        assertFalse(repository.indexBatch())
        assertEquals(ClipProgress(2, 2, 0), repository.progress.first())
    }

    @Test fun duplicateRepositorySubmissionsAreIgnoredUntilSchedulingAcceptance() = runBlocking {
        failure(image(folder()))
        val entered = CompletableDeferred<Unit>()
        val acceptance = CompletableDeferred<Unit>()
        var schedules = 0
        val repository = ClipRepository(db, engine)
        val first = async { repository.retryFailedImages { schedules++; entered.complete(Unit); acceptance.await() } }
        withTimeout(10_000) { entered.await() }
        assertEquals(0, repository.retryFailedImages { schedules++ })
        assertEquals(1, schedules)
        acceptance.complete(Unit)
        assertEquals(1, first.await())
    }

    @Test fun retryContinuesBeyond64AndRepeatedFileErrorsStayFailed() = runBlocking {
        val folder = folder()
        repeat(65) { failure(image(folder, "retry-$it")) }
        val repository = ClipRepository(db, engine)
        assertEquals(65, repository.retryFailedImages {})
        assertTrue(repository.indexBatch())
        assertEquals(ClipProgress(65, 64, 0), repository.progress.first())
        assertFalse(repository.indexBatch())
        assertEquals(ClipProgress(65, 65, 0), repository.progress.first())
        val bad = image(folder, "bad")
        failure(bad)
        imageEmbedding = { throw java.io.IOException("synthetic persistent error") }
        assertEquals(1, repository.retryFailedImages {})
        assertFalse(repository.indexBatch())
        assertEquals(ClipProgress(66, 65, 1), repository.progress.first())
        assertFalse(repository.indexBatch())
    }

    @Test fun modelFailureLeavesFilesPendingAndReportsSeparateError() = runBlocking {
        image(folder())
        load = { throw java.io.IOException("missing encoder") }
        val repository = ClipRepository(db, engine)
        try { repository.indexBatch(); fail("Expected model error") } catch (_: ClipModelException) { }
        assertNotNull(repository.modelError.value)
        assertEquals(1, db.clipDao().pending(version).size)
    }

    @Test fun cancellationCanResumeWithANewRepository() = runBlocking {
        image(folder())
        val entered = CompletableDeferred<Unit>()
        imageEmbedding = { entered.complete(Unit); awaitCancellation() }
        val job = launch { ClipRepository(db, engine).indexBatch() }
        withTimeout(10_000) { entered.await() }
        job.cancelAndJoin()
        imageEmbedding = { floatArrayOf(1f, 0f) }
        assertFalse(ClipRepository(db, engine).indexBatch())
        assertEquals(1, search().size)
    }

    @Test fun changedModelRebuildsOnlySemanticIndex() = runBlocking {
        val id = image(folder())
        save(id, floatArrayOf(1f, 0f))
        version = "test-v2"
        assertTrue(search().isEmpty())
        assertFalse(ClipRepository(db, engine).indexBatch())
        assertEquals(1, search().size)
        assertEquals("FAILED", db.imageDao().findById(id)!!.indexStatus)
    }

    @Test fun boundedRunStopsAndReturnsContinuation() = runBlocking {
        val folder = folder()
        repeat(3) { image(folder, "$it") }
        var time = 0L
        imageEmbedding = { time += ClipRepository.MAX_RUN_MILLIS; floatArrayOf(1f, 0f) }
        val repository = ClipRepository(db, engine) { time }
        assertTrue(repository.indexBatch())
        assertEquals(1, repository.progress.first().ready)
    }

    @Test fun emptySemanticQueryUsesGalleryAndNeverRunsEncoder() = runBlocking {
        val id = image(folder())
        imageEmbedding = { error("Should not encode") }
        assertEquals(listOf(id), SearchRepository(db.searchDao(), ClipRepository(db, engine))
            .search(" ", SearchMode.SEMANTIC).first().map { it.imageId })
    }

    @Test fun migrationPreservesFoldersImagesOcrAndFts() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "clip-migration-${System.nanoTime()}.db"
        var persistent = Room.databaseBuilder(context, MemeizerDatabase::class.java, name).build()
        try {
            val folder = persistent.folderDao().insert(IndexedFolderEntity(treeUri = "content://test/migration", displayName = "old", createdAt = 1, updatedAt = 1))
            val id = persistent.imageDao().insert(IndexedImageEntity(folderId = folder, uri = "content://test/old", documentKey = "old", displayName = "old", mimeType = null, size = 1, lastModified = 1, contentKey = "old", indexStatus = "INDEXED", createdAt = 1, updatedAt = 1))
            persistent.ocrDao().insert(OcrResultEntity(imageId = id, engine = "old", language = "latin", text = "cat кот", confidence = null, errorMessage = null, createdAt = 1, updatedAt = 1))
            persistent.searchDao().insert(MemeSearchFtsEntity(imageId = id, text = "cat кот"))
            // Exact v1 tables remain; remove the sole v2 table and reset Room's version marker.
            persistent.openHelper.writableDatabase.execSQL("DROP TABLE clip_embeddings")
            persistent.openHelper.writableDatabase.execSQL("DELETE FROM room_master_table")
            persistent.openHelper.writableDatabase.version = 1
            persistent.close()
            persistent = Room.databaseBuilder(context, MemeizerDatabase::class.java, name)
                .addMigrations(MemeizerDatabase.MIGRATION_1_2).build()
            assertEquals(1, persistent.folderDao().getEnabledFolders().size)
            assertEquals(id, persistent.searchDao().search("cat*", 10).first().single().id)
            assertEquals(id, persistent.searchDao().search("кот*", 10).first().single().id)
            assertEquals(1, persistent.clipDao().pending(version).size)
            persistent.openHelper.readableDatabase.query("SELECT text FROM ocr_results").use {
                assertTrue(it.moveToFirst()); assertEquals("cat кот", it.getString(0))
            }
        } finally { persistent.close(); context.deleteDatabase(name) }
    }
}
