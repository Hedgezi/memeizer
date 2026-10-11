package com.darkesttrololo.memeizer.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        IndexedFolderEntity::class,
        IndexedImageEntity::class,
        OcrResultEntity::class,
        MemeSearchFtsEntity::class,
        ClipEmbeddingEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class MemeizerDatabase : RoomDatabase() {
    // Shared by all repository instances using this database. Never held by folder removal.
    val indexingMutex = kotlinx.coroutines.sync.Mutex()
    abstract fun folderDao(): FolderDao
    abstract fun imageDao(): ImageDao
    abstract fun ocrDao(): OcrDao
    val clipIndexingMutex = kotlinx.coroutines.sync.Mutex()
    abstract fun searchDao(): SearchDao
    abstract fun clipDao(): ClipDao

    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS clip_embeddings (
                        image_id INTEGER NOT NULL, model_version TEXT NOT NULL,
                        content_key TEXT NOT NULL, dimension INTEGER NOT NULL,
                        vector BLOB, status TEXT NOT NULL, error TEXT,
                        updated_at INTEGER NOT NULL, PRIMARY KEY(image_id, model_version)
                    )
                """.trimIndent())
            }
        }
    }
}
