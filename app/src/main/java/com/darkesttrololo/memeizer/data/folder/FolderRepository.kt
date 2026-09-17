package com.darkesttrololo.memeizer.data.folder

import android.net.Uri
import androidx.room.withTransaction
import com.darkesttrololo.memeizer.data.db.MemeizerDatabase
import com.darkesttrololo.memeizer.data.db.IndexedFolderEntity
import kotlinx.coroutines.flow.Flow

class FolderRepository(private val database: MemeizerDatabase) {
    private val folderDao = database.folderDao()
    fun observeFolders(): Flow<List<IndexedFolderEntity>> = folderDao.observeFolders()

    suspend fun addFolder(treeUri: Uri, displayName: String) {
        val now = System.currentTimeMillis()
        folderDao.insert(
            IndexedFolderEntity(
                treeUri = treeUri.toString(),
                displayName = displayName,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun removeFolder(folderId: Long) {
        database.withTransaction {
            database.folderImageDao().deleteFolder(folderId)
            folderDao.delete(folderId)
            database.pruneUnselectedImages()
        }
    }
}
