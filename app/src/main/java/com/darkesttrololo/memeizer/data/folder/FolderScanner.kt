package com.darkesttrololo.memeizer.data.folder

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class FolderScanner(private val query: (Uri, Array<String>) -> Cursor?) {
    constructor(context: Context) : this({ uri, columns ->
        context.contentResolver.query(uri, columns, null, null, null)
    })

    // DocumentFile.listFiles() swallows provider errors and can return partial results.
    // Query directly and publish a snapshot only after the entire traversal succeeds.
    suspend fun scan(treeUri: Uri): List<ScannedImage> {
        val images = mutableListOf<ScannedImage>()
        val visited = mutableSetOf<String>()
        suspend fun visit(documentId: String) {
            currentCoroutineContext().ensureActive()
            check(visited.add(documentId)) { "Cyclic document tree" }
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            val columns = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            )
            val directories = mutableListOf<String>()
            val cursor = checkNotNull(query(children, columns)) {
                "Cannot read folder $children"
            }
            cursor.use {
                check(!it.extras.getBoolean(DocumentsContract.EXTRA_LOADING, false)) { "Folder is still loading" }
                check(it.extras.getString(DocumentsContract.EXTRA_ERROR) == null) { "Provider reported an error" }
                while (it.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val id = checkNotNull(it.getString(0))
                    val mime = checkNotNull(it.getString(2))
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        directories += id
                    } else if (mime in supportedMimeTypes) {
                        images += ScannedImage(
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                            displayName = it.getString(1) ?: id,
                            mimeType = mime,
                            size = if (it.isNull(3)) null else it.getLong(3),
                            lastModified = if (it.isNull(4)) null else it.getLong(4).takeIf { value -> value > 0 },
                        )
                    }
                }
            }
            directories.forEach { visit(it) }
        }
        visit(DocumentsContract.getTreeDocumentId(treeUri))
        return images
    }

    private companion object {
        val supportedMimeTypes = setOf("image/jpeg", "image/png", "image/webp")
    }
}

fun documentKey(uri: Uri): String = if (uri.pathSegments.contains("document")) {
    DocumentsContract.buildDocumentUri(requireNotNull(uri.authority), DocumentsContract.getDocumentId(uri)).toString()
} else {
    uri.toString()
}

data class ScannedImage(
    val uri: Uri,
    val displayName: String,
    val mimeType: String?,
    val size: Long?,
    val lastModified: Long?,
) {
    val documentKey: String = documentKey(uri)
    val contentKey: String = listOf(documentKey, size, lastModified).joinToString(separator = ":")
}
