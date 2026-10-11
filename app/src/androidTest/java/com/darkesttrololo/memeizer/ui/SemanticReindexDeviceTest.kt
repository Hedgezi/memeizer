package com.darkesttrololo.memeizer.ui

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.ViewModelStore
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.darkesttrololo.memeizer.MainActivity
import com.darkesttrololo.memeizer.MemeizerApp
import com.darkesttrololo.memeizer.R
import com.darkesttrololo.memeizer.data.db.ClipEmbeddingEntity
import com.darkesttrololo.memeizer.data.db.IndexedFolderEntity
import com.darkesttrololo.memeizer.data.db.IndexedImageEntity
import com.darkesttrololo.memeizer.data.indexing.ClipIndexWorker
import com.darkesttrololo.memeizer.data.search.SearchMode
import com.darkesttrololo.memeizer.data.syntheticPng
import com.darkesttrololo.memeizer.data.folder.ScannedImage
import com.darkesttrololo.memeizer.data.indexing.IndexRepository
import com.darkesttrololo.memeizer.ui.home.HomeScreen
import com.darkesttrololo.memeizer.ui.home.HomeViewModel
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the actual HomeViewModel, CLIP WorkManager continuation and bundled model; cleans its own fixtures. */
@RunWith(AndroidJUnit4::class)
class SemanticReindexDeviceTest {
    @Test fun simplifiedScreenAndForcedReindexContinueBeyond64WithoutRetryLoop() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val container = (context.applicationContext as MemeizerApp).container
        val db = container.database
        withTimeout(30_000) { container.indexScheduler.clipWorkState.first { !it.isLoading } }
        val initial = container.clipRepository.progress.first()
        // Do not retry unrelated failures on a user's existing gallery.
        assumeTrue(initial.failed == 0 && initial.ready == initial.total)
        val originalVectors = db.clipDao().vectors(container.clipEngine.modelVersion, 0)
        val good = syntheticPng(context, 16, 16)
        val bad = syntheticPng(context, 4097, 1)
        val files = listOf(good, bad)
        val key = "synthetic-retry-${System.nanoTime()}"
        val snapshots = mutableListOf<ScannedImage>()
        val indexDb = androidx.room.Room.inMemoryDatabaseBuilder(context, com.darkesttrololo.memeizer.data.db.MemeizerDatabase::class.java).build()
        var folderId = 0L
        var activity: MainActivity? = null
        val store = ViewModelStore()
        val viewModel = HomeViewModel(container)
        store.put(key, viewModel)
        val collection = launch(Dispatchers.Main) { viewModel.uiState.collect() }
        try {
            folderId = db.folderDao().insert(IndexedFolderEntity(
                treeUri = "content://test/$key", displayName = key, createdAt = System.currentTimeMillis(), updatedAt = 1))
            db.withTransaction {
                repeat(66) { index ->
                    val file = if (index == 65) bad else good
                    val snapshot = ScannedImage(Uri.fromFile(file), "synthetic-$index.png", "image/png", file.length(), file.lastModified())
                    // Distinct URI identities are needed even when test bytes are identical.
                    val fixtureFile = File(context.cacheDir, "$key-$index.png").apply { file.copyTo(this) }
                    val image = snapshot.copy(uri = Uri.fromFile(fixtureFile))
                    snapshots += image
                    val imageKey = image.contentKey
                    val id = db.imageDao().insert(IndexedImageEntity(
                        folderId = folderId, uri = image.uri.toString(), documentKey = image.documentKey,
                        displayName = "synthetic-$index.png", mimeType = "image/png", size = file.length(),
                        lastModified = image.lastModified, contentKey = imageKey, indexStatus = "FAILED",
                        createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
                    db.clipDao().save(ClipEmbeddingEntity(id, container.clipEngine.modelVersion, imageKey,
                        container.clipEngine.dimension, null, "FAILED", "synthetic failure", 1))
                }
            }
            instrumentation.runOnMainSync { viewModel.onModeChanged(SearchMode.SEMANTIC) }
            withTimeout(10_000) { container.clipRepository.progress.first { it.failed == 66 } }
            activity = instrumentation.startActivitySync(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            val homeActivity = activity
            instrumentation.runOnMainSync {
                homeActivity.setContent {
                    MaterialTheme { HomeScreen(viewModel, PaddingValues(), rememberLazyGridState()) }
                }
            }
            // Both modes use the same hint; the removed helper text and controls stay absent.
            awaitText(homeActivity.getString(R.string.search_hint))
            assertSimplifiedScreen()
            instrumentation.runOnMainSync { viewModel.onModeChanged(SearchMode.TEXT) }
            withTimeout(5_000) { viewModel.uiState.first { it.mode == SearchMode.TEXT } }
            awaitText(homeActivity.getString(R.string.search_hint))
            assertSimplifiedScreen()
            instrumentation.runOnMainSync { viewModel.onModeChanged(SearchMode.SEMANTIC) }
            assertEquals("", viewModel.uiState.value.query)

            // An empty indexing DB exercises the forced callback without rewriting the user's OCR.
            // The callback schedules real CLIP workers against the app DB containing our fixtures.
            val repository = IndexRepository(indexDb, { error("No folders to scan") }, emptyList(), onForcedReindex = {
                container.clipRepository.retryFailedImages(container.indexScheduler::enqueueClipIndexingAndAwait)
            })
            val manager = WorkManager.getInstance(context)
            val before = manager.getWorkInfosForUniqueWork(ClipIndexWorker.WORK_NAME).get().map { it.id }.toSet()
            repository.indexSelectedFolders(true)
            withTimeout(120_000) {
                container.clipRepository.progress.first { it.ready == initial.ready + 65 && it.failed == 1 }
                container.indexScheduler.clipWorkState.first { !it.isLoading }
            }
            val submitted = manager.getWorkInfosForUniqueWork(ClipIndexWorker.WORK_NAME).get().filter { it.id !in before }
            assertEquals(2, submitted.size)
            assertTrue(submitted.all { it.state == androidx.work.WorkInfo.State.SUCCEEDED })
            val after = db.clipDao().vectors(container.clipEngine.modelVersion, 0)
            originalVectors.forEach { original ->
                assertArrayEquals(original.vector, after.first { it.image.id == original.image.id }.vector)
            }
            repository.indexSelectedFolders(false)
            assertEquals(2, manager.getWorkInfosForUniqueWork(ClipIndexWorker.WORK_NAME).get().count { it.id !in before })
            assertEquals(1, container.clipRepository.progress.first().failed)
            repository.indexSelectedFolders(true)
            withTimeout(30_000) {
                manager.getWorkInfosForUniqueWorkFlow(ClipIndexWorker.WORK_NAME).first { work ->
                    val new = work.filter { it.id !in before }
                    new.size == 3 && new.all { it.state.isFinished }
                }
            }
            val end = container.clipRepository.progress.first()
            assertEquals(initial.ready + 65, end.ready)
            assertEquals(1, end.failed)
            assertEquals(3, manager.getWorkInfosForUniqueWork(ClipIndexWorker.WORK_NAME).get().count { it.id !in before })
        } finally {
            indexDb.close()
            collection.cancelAndJoin()
            instrumentation.runOnMainSync { activity?.finish(); store.clear() }
            if (folderId != 0L) withContext(NonCancellable + Dispatchers.IO) {
                db.clipIndexingMutex.lock()
                try {
                    db.withTransaction {
                        val sql = db.openHelper.writableDatabase
                        sql.execSQL("DELETE FROM clip_embeddings WHERE image_id IN (SELECT id FROM indexed_images WHERE folder_id = ?)", arrayOf(folderId))
                        sql.execSQL("DELETE FROM indexed_images WHERE folder_id = ?", arrayOf(folderId))
                        sql.execSQL("DELETE FROM indexed_folders WHERE id = ?", arrayOf(folderId))
                    }
                } finally { db.clipIndexingMutex.unlock() }
            }
            (files + snapshots.map { File(requireNotNull(it.uri.path)) }).forEach { it.delete() }
        }
    }

    private fun awaitText(label: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (screenTexts(automation.rootInActiveWindow).contains(label)) return
            SystemClock.sleep(100)
        }
        error("Expected screen text did not appear: $label")
    }

    private fun assertSimplifiedScreen() {
        val texts = screenTexts(InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow)
        assertFalse(texts.any { text -> listOf(
            "Semantic index:", "Смысловой индекс:", "Retry failed images", "Повторить обработку ошибок",
            "Enter an English description", "Введите описание на английском", "Describe an image in English",
            "Опишите картинку по-английски",
        ).any { text.startsWith(it) } })
    }

    private fun screenTexts(node: AccessibilityNodeInfo?): List<String> {
        if (node == null) return emptyList()
        return listOfNotNull(node.text?.toString()) + (0 until node.childCount).flatMap { screenTexts(node.getChild(it)) }
    }
}
