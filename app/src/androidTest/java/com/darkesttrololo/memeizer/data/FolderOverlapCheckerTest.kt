package com.darkesttrololo.memeizer.data

import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darkesttrololo.memeizer.data.folder.FolderOverlapChecker
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderOverlapCheckerTest {
    private fun tree(id: String, authority: String = "test") = DocumentsContract.buildTreeDocumentUri(authority, id)

    @Test fun detectsOpaqueDescendantIdsInBothDirections() {
        val checker = FolderOverlapChecker { parent, child ->
            DocumentsContract.getDocumentId(parent) == "opaque-42" && DocumentsContract.getDocumentId(child) == "opaque-7"
        }
        assertTrue(checker.overlaps(tree("opaque-42"), tree("opaque-7")))
        assertTrue(checker.overlaps(tree("opaque-7"), tree("opaque-42")))
        assertFalse(checker.overlaps(tree("opaque-42"), tree("opaque-99")))
    }

    @Test fun sameDocumentOverlapsAndDifferentAuthoritiesDoNot() {
        val checker = FolderOverlapChecker { _, _ -> error("Provider must not be queried") }
        assertTrue(checker.overlaps(tree("same"), tree("same")))
        assertFalse(checker.overlaps(tree("same"), tree("same", "other")))
    }

    @Test fun doesNotGuessHierarchyFromDocumentIdPrefixes() {
        val checker = FolderOverlapChecker { _, _ -> false }
        assertFalse(checker.overlaps(tree("root/a"), tree("root/a/b")))
    }

    @Test(expected = SecurityException::class)
    fun accessFailureDoesNotSilentlyApproveSelection() {
        FolderOverlapChecker { _, _ -> throw SecurityException("Lost permission") }
            .overlaps(tree("parent"), tree("child"))
    }
}
