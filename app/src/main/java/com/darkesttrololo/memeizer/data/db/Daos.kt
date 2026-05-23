package com.darkesttrololo.memeizer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM indexed_folders ORDER BY created_at DESC")
    fun observeFolders(): Flow<List<IndexedFolderEntity>>

    @Query("SELECT * FROM indexed_folders WHERE enabled = 1 ORDER BY created_at DESC")
    suspend fun getEnabledFolders(): List<IndexedFolderEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(folder: IndexedFolderEntity): Long

    @Query("DELETE FROM indexed_folders WHERE id = :folderId")
    suspend fun delete(folderId: Long)
}

@Dao
interface ImageDao {
    @Query("SELECT COUNT(*) FROM indexed_images")
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
        SELECT indexed_images.id, indexed_images.uri, indexed_images.display_name AS displayName, meme_search_fts.text
        FROM meme_search_fts
        JOIN indexed_images ON indexed_images.id = meme_search_fts.image_id
        WHERE meme_search_fts MATCH :query
        ORDER BY indexed_images.updated_at DESC
        LIMIT :limit
        """,
    )
    fun search(query: String, limit: Int): Flow<List<SearchResultRow>>
}

data class SearchResultRow(
    val id: Long,
    val uri: String,
    val displayName: String,
    val text: String,
)
