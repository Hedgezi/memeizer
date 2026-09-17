package com.darkesttrololo.memeizer.data.folder

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract

class FolderOverlapChecker(
    private val isDescendant: (Uri, Uri) -> Boolean,
) {
    constructor(resolver: ContentResolver) : this({ parent, child ->
        DocumentsContract.isChildDocument(resolver, parent, child)
    })

    fun overlaps(first: Uri, second: Uri): Boolean {
        if (first.authority != second.authority) return false
        val firstId = DocumentsContract.getTreeDocumentId(first)
        val secondId = DocumentsContract.getTreeDocumentId(second)
        if (firstId == secondId) return true
        // Document IDs are opaque: asking the provider also supports non-path IDs.
        val firstDocument = DocumentsContract.buildDocumentUriUsingTree(first, firstId)
        val secondDocument = DocumentsContract.buildDocumentUriUsingTree(second, secondId)
        return isDescendant(firstDocument, secondDocument) || isDescendant(secondDocument, firstDocument)
    }
}
