package com.darkesttrololo.memeizer.data.db

import android.net.Uri
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.darkesttrololo.memeizer.data.folder.documentKey

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS folder_images (folder_id INTEGER NOT NULL, document_key TEXT NOT NULL, image_id INTEGER NOT NULL, uri TEXT NOT NULL, PRIMARY KEY(folder_id, document_key))")
        val canonicalIds = mutableMapOf<String, Long>()
        db.query("SELECT id, folder_id, uri FROM indexed_images WHERE folder_id IN (SELECT id FROM indexed_folders) ORDER BY id").use { cursor ->
            while (cursor.moveToNext()) {
                val uri = cursor.getString(2)
                val key = documentKey(Uri.parse(uri))
                val imageId = canonicalIds.getOrPut(key) { cursor.getLong(0) }
                db.execSQL("INSERT OR IGNORE INTO folder_images VALUES (?, ?, ?, ?)", arrayOf(cursor.getLong(1), key, imageId, uri))
            }
        }
        db.execSQL("DELETE FROM ocr_results WHERE image_id NOT IN (SELECT image_id FROM folder_images)")
        db.execSQL("DELETE FROM indexed_images WHERE id NOT IN (SELECT image_id FROM folder_images)")
        // Rebuild from OCR's original line structure, not the whitespace-flattened FTS text.
        // Only exact legacy metadata lines are removed; actual words like 'latin' survive.
        db.execSQL("DELETE FROM meme_search_fts")
        db.query("SELECT image_id, text FROM ocr_results JOIN indexed_images ON image_id = indexed_images.id WHERE index_status = 'INDEXED'").use { cursor ->
            while (cursor.moveToNext()) {
                val text = cursor.getString(1).lineSequence()
                    .filterNot { it == "[paddleocr-ncnn:cyrillic]" || it == "[mlkit:latin]" }
                    .joinToString("\n").trim()
                db.execSQL("UPDATE ocr_results SET text = ? WHERE image_id = ?", arrayOf(text, cursor.getLong(0)))
                db.execSQL("INSERT INTO meme_search_fts(image_id, text) VALUES (?, ?)", arrayOf(cursor.getLong(0), text.lowercase().replace(Regex("\\s+"), " ").trim()))
            }
        }
    }
}
