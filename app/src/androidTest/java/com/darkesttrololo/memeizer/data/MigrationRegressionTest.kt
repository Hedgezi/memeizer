package com.darkesttrololo.memeizer.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darkesttrololo.memeizer.data.db.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationRegressionTest {
    @Test fun v1MigrationPreservesFoldersAndTextAndRebuildsCleanFts() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-regression.db"
        context.deleteDatabase(name)
        try {
            // Version 1 schema: the four original tables are unchanged in version 2.
            val fixture = Room.databaseBuilder(context, MemeizerDatabase::class.java, name).build()
            val sql = fixture.openHelper.writableDatabase
            sql.execSQL("INSERT INTO indexed_folders VALUES (1, 'content://test/tree/root', 'chosen folder', 1, 1, 1)")
            for (id in 1..3) {
                sql.execSQL("INSERT INTO indexed_images VALUES (?, 1, ?, 'pic', 'image/png', 10, 1, 'unchanged', ?, 1, 1)", arrayOf(id, "content://test/tree/root/document/$id", if (id == 3) "FAILED" else "INDEXED"))
                val text = "[paddleocr-ncnn:cyrillic]\n\n[mlkit:latin]\n" + if (id == 1) "cat latin 123 кот" else ""
                sql.execSQL("INSERT INTO ocr_results VALUES (?, ?, 'paddleocr-ncnn+mlkit', 'cyrillic+latin', ?, NULL, NULL, 1, 1)", arrayOf(id, id, text))
                sql.execSQL("INSERT INTO meme_search_fts(image_id, text) VALUES (?, ?)", arrayOf(id, text.lowercase()))
            }
            fixture.close()
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
                it.execSQL("DROP TABLE folder_images")
                it.execSQL("DELETE FROM room_master_table")
                it.version = 1
            }
            val migrated = Room.databaseBuilder(context, MemeizerDatabase::class.java, name).addMigrations(MIGRATION_1_2).build()
            try {
                assertEquals("chosen folder", migrated.folderDao().getEnabledFolders().single().displayName)
                assertEquals(3, migrated.imageDao().observeImageCount().first())
                assertEquals(3, migrated.folderImageDao().forFolder(1).size)
                for (word in listOf("cat", "latin", "123", "кот")) {
                    assertEquals(1L, migrated.searchDao().search("$word*", 10).first().single().id)
                }
                for (word in listOf("mlkit", "paddleocr", "cyrillic")) {
                    assertTrue(migrated.searchDao().search("$word*", 10).first().isEmpty())
                }
                assertTrue(migrated.searchDao().search("2*", 10).first().isEmpty())
                assertEquals("unchanged", migrated.imageDao().findById(1)!!.contentKey)
                assertEquals("INDEXED", migrated.imageDao().findById(1)!!.indexStatus)
            } finally { migrated.close() }
        } finally { context.deleteDatabase(name) }
    }
}
