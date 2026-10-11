package com.darkesttrololo.memeizer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(embedding: ClipEmbeddingEntity)

    @Query("""
        UPDATE clip_embeddings SET status = 'PENDING', vector = NULL, error = NULL
        WHERE model_version = :model AND status = 'FAILED' AND EXISTS (
            SELECT 1 FROM indexed_images i
            JOIN indexed_folders f ON f.id = i.folder_id AND f.enabled = 1
            WHERE i.id = clip_embeddings.image_id AND i.active = 1
                AND i.content_key = clip_embeddings.content_key
        )
    """)
    suspend fun resetFailures(model: String): Int

    @Query("""
        SELECT i.*, f.selection_version AS selectionVersion FROM indexed_images i
        JOIN indexed_folders f ON f.id = i.folder_id AND f.enabled = 1
        LEFT JOIN clip_embeddings e ON e.image_id = i.id AND e.model_version = :model
        WHERE i.active = 1 AND (e.image_id IS NULL OR e.content_key != i.content_key
            OR e.status = 'PENDING' OR e.status = 'INDEXING')
        ORDER BY i.id LIMIT :limit
    """)
    suspend fun pending(model: String, limit: Int = 64): List<ClipPendingRow>

    @Query("""
        SELECT COUNT(*) AS total,
            COALESCE(SUM(CASE WHEN e.status = 'READY' AND e.content_key = i.content_key THEN 1 ELSE 0 END), 0) AS ready,
            COALESCE(SUM(CASE WHEN e.status = 'FAILED' AND e.content_key = i.content_key THEN 1 ELSE 0 END), 0) AS failed
        FROM indexed_images i JOIN indexed_folders f ON f.id = i.folder_id AND f.enabled = 1
        LEFT JOIN clip_embeddings e ON e.image_id = i.id AND e.model_version = :model
        WHERE i.active = 1
    """)
    fun observeProgress(model: String): Flow<ClipProgress>

    @Query("""
        SELECT i.id, i.uri, i.display_name AS displayName, i.mime_type AS mimeType,
            i.size, i.last_modified AS lastModified, COALESCE(o.text, '') AS text,
            e.dimension, e.vector FROM clip_embeddings e
        JOIN indexed_images i ON i.id = e.image_id AND i.content_key = e.content_key AND i.active = 1
        JOIN indexed_folders f ON f.id = i.folder_id AND f.enabled = 1
        LEFT JOIN ocr_results o ON o.image_id = i.id
        WHERE e.model_version = :model AND e.status = 'READY' AND i.id > :afterId
        ORDER BY i.id LIMIT :limit
    """)
    suspend fun vectors(model: String, afterId: Long, limit: Int = 256): List<ClipSearchRow>
}

data class ClipPendingRow(
    @androidx.room.Embedded val image: IndexedImageEntity,
    val selectionVersion: Long,
)

data class ClipProgress(val total: Int = 0, val ready: Int = 0, val failed: Int = 0)

data class ClipSearchRow(
    @androidx.room.Embedded val image: SearchResultRow,
    val dimension: Int,
    val vector: ByteArray?,
)
