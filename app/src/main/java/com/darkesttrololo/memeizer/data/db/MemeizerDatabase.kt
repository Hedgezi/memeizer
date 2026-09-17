package com.darkesttrololo.memeizer.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        IndexedFolderEntity::class,
        IndexedImageEntity::class,
        OcrResultEntity::class,
        MemeSearchFtsEntity::class,
        FolderImageEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class MemeizerDatabase : RoomDatabase() {
    // Shared by all repository instances using this database. Never held by folder removal.
    val indexingMutex = kotlinx.coroutines.sync.Mutex()
    abstract fun folderImageDao(): FolderImageDao

    // Call only inside a database transaction, after changing folder membership.
    suspend fun pruneUnselectedImages() {
        searchDao().deleteOrphans()
        ocrDao().deleteOrphans()
        imageDao().deleteOrphans()
        imageDao().refreshAccessUris()
    }

    abstract fun folderDao(): FolderDao
    abstract fun imageDao(): ImageDao
    abstract fun ocrDao(): OcrDao
    abstract fun searchDao(): SearchDao
}
