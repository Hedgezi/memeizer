package com.darkesttrololo.memeizer.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darkesttrololo.memeizer.data.db.*
import com.darkesttrololo.memeizer.data.folder.*
import com.darkesttrololo.memeizer.data.indexing.IndexRepository
import com.darkesttrololo.memeizer.data.ocr.OcrEngine
import com.darkesttrololo.memeizer.data.ocr.OcrResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class IndexRegressionTest {
    private lateinit var db: MemeizerDatabase
    private lateinit var folders: FolderRepository
    private val parent = Uri.parse("content://test/tree/root")
    private val child = Uri.parse("content://test/tree/root%2Fchild")
    private var snapshots = mutableMapOf<String, List<ScannedImage>>()
    private var recognize: suspend (Uri) -> String = { "cat" }
    private var scan: suspend (Uri) -> List<ScannedImage> = { snapshots[it.toString()].orEmpty() }
    private val engine = object : OcrEngine {
        override val engineName = "mlkit"
        override val language = "latin"
        override suspend fun recognize(imageUri: Uri) = OcrResult(this@IndexRegressionTest.recognize(imageUri), null, engineName, language)
    }

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MemeizerDatabase::class.java).build()
        folders = FolderRepository(db)
    }

    @After fun close() = db.close()

    private fun repository() = IndexRepository(db, { scan(it) }, listOf(engine))
    private fun image(tree: Uri = parent, name: String = "a", modified: Long = 1) = ScannedImage(
        Uri.parse("$tree/document/root%2Fchild%2F$name"), name, "image/png", 10, modified,
    )
    private suspend fun add(tree: Uri = parent) {
        folders.addFolder(tree, "Folder")
        snapshots[tree.toString()] = listOf(image(tree))
    }
    private suspend fun count() = db.imageDao().observeImageCount().first()
    private suspend fun search(text: String) = db.searchDao().search(text, 100).first()
    private fun sqlCount(table: String): Int = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use {
        it.moveToFirst(); it.getInt(0)
    }

    @Test fun removingFolderClearsGalleryOcrFtsAndAllowsReadding() = runBlocking {
        add()
        repository().indexSelectedFolders(false)
        assertEquals(1, search("cat*").size)
        folders.removeFolder(db.folderDao().getEnabledFolders().single().id)
        assertEquals(0, count())
        assertTrue(db.searchDao().observeGallery(100).first().isEmpty())
        assertTrue(search("cat*").isEmpty())
        assertEquals(0, sqlCount("ocr_results"))
        assertEquals(0, sqlCount("meme_search_fts"))
        add()
        repository().indexSelectedFolders(false)
        assertEquals(1, count())
        assertEquals(1, search("cat*").size)
    }

    @Test fun overlappingTreesShareImageAndRetainUsableGrant() = runBlocking {
        add(parent)
        add(child)
        repository().indexSelectedFolders(false)
        assertEquals(1, count())
        val parentId = db.folderDao().getEnabledFolders().single { it.treeUri == parent.toString() }.id
        folders.removeFolder(parentId)
        assertEquals(1, count())
        assertEquals(image(child).uri.toString(), search("cat*").single().uri)
        repository().indexSelectedFolders(false)
        assertEquals(1, sqlCount("meme_search_fts"))
        folders.removeFolder(db.folderDao().getEnabledFolders().single().id)
        assertEquals(0, count())
    }

    @Test fun removalDuringOcrCannotResurrectEvenWhenSameTreeIsReadded() = runBlocking {
        add()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        recognize = { entered.complete(Unit); release.await(); "cat" }
        val job = launch { repository().indexSelectedFolders(false) }
        withTimeout(10_000) { entered.await() }
        folders.removeFolder(db.folderDao().getEnabledFolders().single().id)
        add()
        release.complete(Unit)
        job.join()
        assertEquals(0, count())
        assertEquals(0, sqlCount("ocr_results"))
        repository().indexSelectedFolders(false)
        assertEquals(1, search("cat*").size)
    }

    @Test fun removalDuringScanDiscardsSnapshot() = runBlocking {
        add()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        scan = { entered.complete(Unit); release.await(); listOf(image()) }
        val job = launch { repository().indexSelectedFolders(false) }
        withTimeout(10_000) { entered.await() }
        folders.removeFolder(db.folderDao().getEnabledFolders().single().id)
        release.complete(Unit)
        job.join()
        assertEquals(0, count())
        assertEquals(0, sqlCount("folder_images"))
        assertEquals(0, sqlCount("ocr_results"))
    }

    @Test fun concurrentInitialIndexingDoesNotDuplicateNewImage() = runBlocking {
        add()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        recognize = { calls.incrementAndGet(); entered.complete(Unit); release.await(); "cat" }
        val first = launch { repository().indexSelectedFolders(false) }
        withTimeout(10_000) { entered.await() }
        val second = launch(start = CoroutineStart.UNDISPATCHED) { repository().indexSelectedFolders(false) }
        release.complete(Unit)
        joinAll(first, second)
        assertEquals(1, calls.get())
        assertEquals(1, count())
        assertEquals(1, sqlCount("ocr_results"))
        assertEquals(1, sqlCount("meme_search_fts"))
    }

    @Test fun deletedMovedAndEmptySnapshotsRemoveObsoleteRows() = runBlocking {
        add()
        repository().indexSelectedFolders(false)
        snapshots[parent.toString()] = listOf(image(name = "moved"))
        repository().indexSelectedFolders(true)
        assertEquals(1, count())
        assertEquals("moved", db.searchDao().observeGallery(100).first().single().displayName)
        assertEquals(1, sqlCount("ocr_results"))
        assertEquals(1, sqlCount("meme_search_fts"))
        snapshots[parent.toString()] = emptyList()
        repository().indexSelectedFolders(true)
        assertEquals(0, count())
        assertEquals(0, sqlCount("ocr_results"))
        assertEquals(0, sqlCount("meme_search_fts"))
    }

    @Test fun failedOrCancelledScanPreservesPreviousIndex() = runBlocking {
        add()
        repository().indexSelectedFolders(false)
        for (error in listOf(IOException("partial traversal"), CancellationException("cancelled"))) {
            scan = { throw error }
            try { repository().indexSelectedFolders(true); fail("Expected failure") } catch (caught: Exception) {
                assertSame(error, caught)
            }
            assertEquals(1, count())
            assertEquals(1, search("cat*").size)
        }
    }

    @Test fun concurrentRepositoriesSerializeForcedWrites() = runBlocking {
        add()
        repository().indexSelectedFolders(false)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        recognize = {
            if (calls.incrementAndGet() == 1) { entered.complete(Unit); release.await() }
            "new"
        }
        val first = launch { repository().indexSelectedFolders(true) }
        withTimeout(10_000) { entered.await() }
        val second = launch(start = CoroutineStart.UNDISPATCHED) { repository().indexSelectedFolders(true) }
        // The first OCR holds the repository lock until explicitly released.
        assertEquals(1, calls.get())
        release.complete(Unit)
        joinAll(first, second)
        assertEquals(2, calls.get())
        assertEquals(1, count())
        assertEquals(1, sqlCount("ocr_results"))
        assertEquals(1, sqlCount("meme_search_fts"))
        assertEquals(1, search("new*").size)
        assertTrue(search("cat*").isEmpty())
    }

    @Test fun cancellingOcrAllowsReplacementAndLeavesNoStaleSearch() = runBlocking {
        add()
        repository().indexSelectedFolders(false)
        val entered = CompletableDeferred<Unit>()
        recognize = { entered.complete(Unit); awaitCancellation() }
        val job = launch { repository().indexSelectedFolders(true) }
        withTimeout(10_000) { entered.await() }
        job.cancelAndJoin()
        assertTrue(search("cat*").isEmpty())
        assertEquals(0, sqlCount("meme_search_fts"))
        recognize = { "replacement" }
        repository().indexSelectedFolders(false)
        assertEquals(1, search("replacement*").size)
        assertEquals(1, sqlCount("ocr_results"))
    }

    @Test fun numericSearchOnlyMatchesRecognizedText() = runBlocking {
        add()
        repository().indexSelectedFolders(false)
        assertEquals(1L, db.searchDao().observeGallery(100).first().single().id)
        assertTrue(search("1*").isEmpty())
        recognize = { "number 1" }
        repository().indexSelectedFolders(true)
        assertEquals(1, search("1*").size)
    }

    @Test fun engineMetadataIsNotSearchableButActualWordsAre() = runBlocking {
        add()
        recognize = { "" }
        repository().indexSelectedFolders(false)
        for (word in listOf("latin", "mlkit", "cyrillic", "paddleocr")) assertTrue(search("$word*").isEmpty())
        recognize = { "latin кот" }
        repository().indexSelectedFolders(true)
        assertEquals(1, search("latin*").size)
        assertEquals(1, search("кот*").size)
    }

    @Test fun failedChangedImageDropsOldTextAndRecovers() = runBlocking {
        add()
        repository().indexSelectedFolders(false)
        snapshots[parent.toString()] = listOf(image(modified = 2))
        recognize = { throw IOException("OCR failure") }
        repository().indexSelectedFolders(false)
        assertTrue(search("cat*").isEmpty())
        assertEquals("FAILED", db.imageDao().findByUri(image().uri.toString())!!.indexStatus)
        assertEquals(0, sqlCount("meme_search_fts"))
        recognize = { "newtext" }
        repository().indexSelectedFolders(false)
        assertEquals(1, search("newtext*").size)
    }

    @Test fun failedDatabaseWriteRollsBackOcrFtsAndStatusTogether() = runBlocking {
        add()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_indexed BEFORE UPDATE OF index_status ON indexed_images WHEN NEW.index_status = 'INDEXED' BEGIN SELECT RAISE(ABORT, 'test'); END")
        try { repository().indexSelectedFolders(false); fail("Expected database failure") } catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(0, sqlCount("ocr_results"))
        assertEquals(0, sqlCount("meme_search_fts"))
        assertEquals("INDEXING", db.imageDao().findByUri(image().uri.toString())!!.indexStatus)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_indexed")
        repository().indexSelectedFolders(false)
        assertEquals(1, search("cat*").size)
    }
}
