package com.darkesttrololo.memeizer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM indexed_folders WHERE enabled = 1 ORDER BY created_at DESC")
    fun observeFolders(): Flow<List<IndexedFolderEntity>>

    @Query("SELECT * FROM indexed_folders WHERE enabled = 1 ORDER BY created_at DESC")
    suspend fun getEnabledFolders(): List<IndexedFolderEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(folder: IndexedFolderEntity): Long

    @Query("SELECT EXISTS(SELECT 1 FROM indexed_folders WHERE id = :folderId AND enabled = 1 AND selection_version = :version)")
    suspend fun isCurrentSelection(folderId: Long, version: Long): Boolean

    @Query("SELECT * FROM indexed_folders WHERE tree_uri = :uri")
    suspend fun findByUri(uri: String): IndexedFolderEntity?

    @Query("UPDATE indexed_folders SET enabled = :enabled, selection_version = selection_version + 1, updated_at = :now WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean, now: Long)
}

@Dao
interface ImageDao {
    @Query("SELECT * FROM indexed_images WHERE id = :id")
    suspend fun findById(id: Long): IndexedImageEntity?

    @Query("SELECT * FROM indexed_images WHERE document_key = :key LIMIT 1")
    suspend fun findByDocumentKey(key: String): IndexedImageEntity?

    @Update
    suspend fun update(image: IndexedImageEntity)

    @Query("UPDATE indexed_images SET active = 0 WHERE folder_id = :folderId")
    suspend fun deactivateFolder(folderId: Long)

    @Query("SELECT COUNT(*) FROM indexed_images WHERE active = 1")
    fun observeImageCount(): Flow<Int>

    @Query("SELECT * FROM indexed_images WHERE uri = :uri LIMIT 1")
    suspend fun findByUri(uri: String): IndexedImageEntity?

    @Insert
    suspend fun insert(image: IndexedImageEntity): Long

    @Query("UPDATE indexed_images SET content_key = :contentKey, index_status = :status, updated_at = :updatedAt WHERE id = :imageId")
    suspend fun updateContentKeyAndStatus(imageId: Long, contentKey: String, status: String, updatedAt: Long)

    @Query("UPDATE indexed_images SET index_status = :status, updated_at = :updatedAt WHERE id = :imageId")
    suspend fun updateStatus(imageId: Long, status: String, updatedAt: Long)
}

@Dao
interface OcrDao {
    @Query("DELETE FROM ocr_results WHERE image_id = :imageId")
    suspend fun deleteForImage(imageId: Long)

    @Insert
    suspend fun insert(result: OcrResultEntity)
}

@Dao
interface SearchDao {
    @Query("DELETE FROM meme_search_fts WHERE image_id = :imageId")
    suspend fun deleteForImage(imageId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MemeSearchFtsEntity)

    @Query(
        """
        SELECT indexed_images.id, indexed_images.uri, indexed_images.display_name AS displayName,
            indexed_images.mime_type AS mimeType, indexed_images.size, indexed_images.last_modified AS lastModified, meme_search_fts.text
        FROM meme_search_fts
        JOIN indexed_images ON indexed_images.id = meme_search_fts.image_id
        WHERE meme_search_fts.text MATCH :query AND indexed_images.active = 1 AND indexed_images.index_status = 'INDEXED'
        ORDER BY indexed_images.updated_at DESC
        LIMIT :limit
        """,
    )
    fun search(query: String, limit: Int): Flow<List<SearchResultRow>>

    @Query(
        """
        SELECT indexed_images.id, indexed_images.uri, indexed_images.display_name AS displayName,
            indexed_images.mime_type AS mimeType, indexed_images.size, indexed_images.last_modified AS lastModified, COALESCE(meme_search_fts.text, '') AS text
        FROM indexed_images
        LEFT JOIN meme_search_fts ON indexed_images.id = meme_search_fts.image_id
        WHERE indexed_images.active = 1
        ORDER BY indexed_images.updated_at DESC
        LIMIT :limit
        """,
    )
    fun observeGallery(limit: Int): Flow<List<SearchResultRow>>
}

data class SearchResultRow(
    val id: Long,
    val uri: String,
    val displayName: String,
    val text: String,
    val mimeType: String?,
    val size: Long?,
    val lastModified: Long?,
)
