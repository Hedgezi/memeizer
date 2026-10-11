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

/** Uses the actual ViewModel, button, WorkManager and bundled model; cleans only its own fixtures. */
@RunWith(AndroidJUnit4::class)
class SemanticRetryDeviceTest {
    @Test fun emptyQueryButtonRetriesThroughWorkerContinuationAndKeepsPermanentFailures() = runBlocking {
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
                    val imageKey = "$key-$index"
                    val id = db.imageDao().insert(IndexedImageEntity(
                        folderId = folderId, uri = Uri.fromFile(file).toString(), documentKey = imageKey,
                        displayName = "synthetic-$index.png", mimeType = "image/png", size = file.length(),
                        lastModified = file.lastModified(), contentKey = imageKey, indexStatus = "FAILED",
                        createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
                    db.clipDao().save(ClipEmbeddingEntity(id, container.clipEngine.modelVersion, imageKey,
                        container.clipEngine.dimension, null, "FAILED", "synthetic failure", 1))
                }
            }
            instrumentation.runOnMainSync { viewModel.onModeChanged(SearchMode.SEMANTIC) }
            withTimeout(10_000) { viewModel.uiState.first { it.clipProgress.failed == 66 && it.semanticRetryEnabled } }
            activity = instrumentation.startActivitySync(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            val homeActivity = activity
            instrumentation.runOnMainSync {
                homeActivity.setContent {
                    MaterialTheme { HomeScreen(viewModel, PaddingValues(), rememberLazyGridState()) }
                }
            }
            val label = homeActivity.getString(R.string.semantic_retry_failed)
            val button = awaitButton(label)
            assertTrue(button.isEnabled)
            assertEquals("", viewModel.uiState.value.query)
            val manager = WorkManager.getInstance(context)
            val before = manager.getWorkInfosForUniqueWork(ClipIndexWorker.WORK_NAME).get().map { it.id }.toSet()
            assertTrue(button.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            withTimeout(5_000) { viewModel.uiState.first { !it.semanticRetryEnabled } }
            instrumentation.runOnMainSync { repeat(3) { viewModel.retryFailedImages() } }
            withTimeout(120_000) {
                viewModel.uiState.first { it.clipProgress.ready == initial.ready + 65 && it.clipProgress.failed == 1 }
                container.indexScheduler.clipWorkState.first { !it.isLoading }
            }
            val submitted = manager.getWorkInfosForUniqueWork(ClipIndexWorker.WORK_NAME).get().filter { it.id !in before }
            assertEquals(2, submitted.size) // One submitted run and its >64 continuation, despite duplicate taps.
            assertTrue(submitted.all { it.state == androidx.work.WorkInfo.State.SUCCEEDED })
            val after = db.clipDao().vectors(container.clipEngine.modelVersion, 0)
            originalVectors.forEach { original ->
                assertArrayEquals(original.vector, after.first { it.image.id == original.image.id }.vector)
            }
            withTimeout(5_000) { viewModel.uiState.first { it.showSemanticRetry && it.semanticRetryEnabled } }
            val retry = awaitButton(label)
            assertTrue(retry.isEnabled)
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            try {
                File(context.filesDir, "semantic-retry-synthetic.png").outputStream().use {
                    screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            } finally { screenshot.recycle() }
            assertTrue(retry.performAction(AccessibilityNodeInfo.ACTION_CLICK))
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
            files.forEach { it.delete() }
        }
    }

    private fun awaitButton(label: String): AccessibilityNodeInfo {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            find(automation.rootInActiveWindow, label)?.let { return it }
            SystemClock.sleep(100)
        }
        val screenshot = automation.takeScreenshot()
        if (screenshot != null) {
            try {
                File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "semantic-retry-failure.png")
                    .outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            } finally { screenshot.recycle() }
        }
        error("Synthetic retry button did not appear: $label")
    }

    private fun find(node: AccessibilityNodeInfo?, label: String): AccessibilityNodeInfo? {
        if (node == null || !node.refresh()) return null
        if (node.text?.toString() == label) {
            var target: AccessibilityNodeInfo = node
            while (!target.isClickable) target = target.parent ?: return null
            return target
        }
        for (index in 0 until node.childCount) find(node.getChild(index), label)?.let { return it }
        return null
    }
}
