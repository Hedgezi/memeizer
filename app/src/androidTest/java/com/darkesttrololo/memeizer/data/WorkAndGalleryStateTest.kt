package com.darkesttrololo.memeizer.data

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import com.darkesttrololo.memeizer.R
import com.darkesttrololo.memeizer.data.db.IndexStatus
import com.darkesttrololo.memeizer.data.db.IndexedFolderEntity
import com.darkesttrololo.memeizer.data.db.IndexedImageEntity
import com.darkesttrololo.memeizer.data.db.MemeizerDatabase
import com.darkesttrololo.memeizer.data.indexing.indexWorkState
import com.darkesttrololo.memeizer.data.search.SearchRepository
import java.util.UUID
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkAndGalleryStateTest {
    private lateinit var database: MemeizerDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MemeizerDatabase::class.java).build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun periodicWaitingIsNotLoadingButRunningIs() {
        assertFalse(indexWorkState(emptyList(), listOf(work(WorkInfo.State.ENQUEUED))).isLoading)
        assertTrue(indexWorkState(emptyList(), listOf(work(WorkInfo.State.RUNNING))).isLoading)
    }

    @Test
    fun newestManualWorkIgnoresCancelledReplacement() {
        val old = work(WorkInfo.State.CANCELLED, 1)
        val current = work(WorkInfo.State.ENQUEUED, 2)
        assertTrue(indexWorkState(listOf(old, current), emptyList()).isLoading)
        assertFalse(indexWorkState(listOf(current, work(WorkInfo.State.CANCELLED, 3)), emptyList()).isLoading)
    }

    @Test
    fun resourcesUseRussianAndEnglishFallback() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Поиск...", localized(context, "ru", R.string.search_hint))
        assertEquals("Search...", localized(context, "en", R.string.search_hint))
        assertEquals("Search...", localized(context, "de", R.string.search_hint))
        assertEquals(
            "Добавьте папки в настройках, чтобы видеть изображения.",
            localized(context, "ru", R.string.home_add_folders),
        )
        assertEquals(
            "Add folders in Settings to see images.",
            localized(context, "de", R.string.home_add_folders),
        )
    }

    @Test
    fun blankQueriesKeepGalleryOrderAndLimitAtOneHundred() = runBlocking {
        val now = System.currentTimeMillis()
        val folderId = database.folderDao().insert(
            IndexedFolderEntity(
                treeUri = "content://folder",
                displayName = "Folder",
                createdAt = now,
                updatedAt = now,
            ),
        )
        repeat(120) { index ->
            database.imageDao().insert(
                IndexedImageEntity(
                    folderId = folderId,
                    uri = "content://image/$index",
                    documentKey = "image-$index",
                    displayName = "$index.png",
                    mimeType = "image/png",
                    size = 1,
                    lastModified = now,
                    contentKey = "$index",
                    indexStatus = IndexStatus.PENDING.name,
                    createdAt = now,
                    updatedAt = index.toLong(),
                ),
            )
        }
        val repository = SearchRepository(database.searchDao())
        val empty = repository.search("").first()
        val spaces = repository.search("   ").first()
        assertEquals(100, empty.size)
        assertEquals(empty.map { it.imageId }, spaces.map { it.imageId })
        assertEquals("119.png", empty.first().displayName)
    }

    private fun work(state: WorkInfo.State, sequence: Long? = null) = WorkInfo(
        UUID.randomUUID(),
        state,
        sequence?.let { setOf("meme_manual_sequence:$it") }.orEmpty(),
    )

    private fun localized(context: Context, language: String, resource: Int): String {
        val configuration = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(Locale.forLanguageTag(language)))
        }
        return context.createConfigurationContext(configuration).getString(resource)
    }
}
