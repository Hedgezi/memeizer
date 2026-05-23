package com.darkesttrololo.memeizer.data.folder

import android.net.Uri
import com.darkesttrololo.memeizer.data.db.FolderDao
import com.darkesttrololo.memeizer.data.db.IndexedFolderEntity
import kotlinx.coroutines.flow.Flow

class FolderRepository(private val folderDao: FolderDao) {
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
        folderDao.delete(folderId)
    }
}
