package com.darkesttrololo.memeizer.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "indexed_folders", indices = [Index(value = ["tree_uri"], unique = true)])
data class IndexedFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "tree_uri") val treeUri: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    val enabled: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "indexed_images",
    indices = [
        Index(value = ["uri"], unique = true),
        Index(value = ["folder_id"]),
    ],
)
data class IndexedImageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "folder_id") val folderId: Long,
    val uri: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "mime_type") val mimeType: String?,
    val size: Long?,
    @ColumnInfo(name = "last_modified") val lastModified: Long?,
    @ColumnInfo(name = "content_key") val contentKey: String,
    @ColumnInfo(name = "index_status") val indexStatus: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "ocr_results", indices = [Index(value = ["image_id"], unique = true)])
data class OcrResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "image_id") val imageId: Long,
    val engine: String,
    val language: String,
    val text: String,
    val confidence: Int?,
    @ColumnInfo(name = "error_message") val errorMessage: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Fts4
@Entity(tableName = "meme_search_fts")
data class MemeSearchFtsEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "rowid") val rowId: Int = 0,
    @ColumnInfo(name = "image_id") val imageId: Long,
    val text: String,
)

enum class IndexStatus {
    PENDING,
    INDEXING,
    INDEXED,
    FAILED,
    SKIPPED,
}
