package com.darkesttrololo.memeizer.data.folder

import android.net.Uri
import androidx.room.withTransaction
import com.darkesttrololo.memeizer.data.db.IndexedFolderEntity
import com.darkesttrololo.memeizer.data.db.MemeizerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class OverlappingFolderException : IllegalArgumentException("This folder overlaps an already selected folder.")

class FolderRepository(
    private val database: MemeizerDatabase,
    private val overlaps: (Uri, Uri) -> Boolean,
) {
    private val folderDao = database.folderDao()
    fun observeFolders(): Flow<List<IndexedFolderEntity>> = folderDao.observeFolders()

    suspend fun addFolder(treeUri: Uri, displayName: String) = withContext(Dispatchers.IO) {
        database.withTransaction {
            // Serialize validation and activation so simultaneous selections cannot overlap.
            if (folderDao.getEnabledFolders().any { overlaps(treeUri, Uri.parse(it.treeUri)) }) {
                throw OverlappingFolderException()
            }
            val now = System.currentTimeMillis()
            val existing = folderDao.findByUri(treeUri.toString())
            if (existing != null) {
                folderDao.setEnabled(existing.id, true, now)
            } else {
                folderDao.insert(
                    IndexedFolderEntity(
                        treeUri = treeUri.toString(), displayName = displayName,
                        createdAt = now, updatedAt = now,
                    ),
                )
            }
            // Cached images stay inactive until a complete fresh scan confirms their presence.
        }
    }

    suspend fun removeFolder(folderId: Long) {
        database.withTransaction {
            folderDao.setEnabled(folderId, false, System.currentTimeMillis())
            database.imageDao().deactivateFolder(folderId)
        }
    }
}
