package com.darkesttrololo.memeizer.data.folder

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

class FolderScanner(private val context: Context) {
    fun scan(treeUri: Uri): Sequence<ScannedImage> = sequence {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@sequence
        yieldAll(scanDocument(root))
    }

    private fun scanDocument(document: DocumentFile): Sequence<ScannedImage> = sequence {
        document.listFiles().forEach { child ->
            when {
                child.isDirectory -> yieldAll(scanDocument(child))
                child.isFile && child.type in supportedMimeTypes -> yield(
                    ScannedImage(
                        uri = child.uri,
                        displayName = child.name ?: child.uri.lastPathSegment.orEmpty(),
                        mimeType = child.type,
                        size = child.length().takeIf { it >= 0 },
                        lastModified = child.lastModified().takeIf { it > 0 },
                    ),
                )
            }
        }
    }

    private companion object {
        val supportedMimeTypes = setOf("image/jpeg", "image/png", "image/webp")
    }
}

data class ScannedImage(
    val uri: Uri,
    val displayName: String,
    val mimeType: String?,
    val size: Long?,
    val lastModified: Long?,
) {
    val contentKey: String = listOf(uri.toString(), size, lastModified).joinToString(separator = ":")
}
