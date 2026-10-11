package com.darkesttrololo.memeizer.ui.home

import com.darkesttrololo.memeizer.data.db.ClipProgress
import com.darkesttrololo.memeizer.data.search.SearchMode
import com.darkesttrololo.memeizer.data.search.SearchResult
import org.junit.Assert.*
import org.junit.Test

class SemanticContentStateTest {
    private val base = HomeUiState(mode = SearchMode.SEMANTIC, query = "cat", content = HomeContentState.NoResults)

    @Test fun completedFailuresAreDifferentFromPendingAndEmpty() {
        assertEquals(HomeContentState.SemanticIndexFailed, semanticContentState(base, ClipProgress(4, 0, 4), false))
        assertEquals(HomeContentState.SemanticIndexNotReady, semanticContentState(base, ClipProgress(4, 0, 3), false))
        assertEquals(HomeContentState.SemanticIndexNotReady, semanticContentState(base, ClipProgress(4), false))
        val empty = base.copy(content = HomeContentState.EmptyFolders)
        assertEquals(HomeContentState.EmptyFolders, semanticContentState(empty, ClipProgress(), false))
    }

    @Test fun partialFailureRetainsResultsAndModelErrorRemainsSeparate() {
        val image = SearchResult(1, "content://test", "synthetic.png", "", null, null, null)
        val images = base.copy(results = listOf(image), content = HomeContentState.Images)
        assertEquals(HomeContentState.Images, semanticContentState(images, ClipProgress(4, 1, 3), false))
        assertEquals(HomeContentState.Images, semanticContentState(images, ClipProgress(4, 1, 3), true))
        assertEquals(HomeContentState.ModelError, semanticContentState(base, ClipProgress(4), true))
        assertEquals(HomeContentState.NoResults, semanticContentState(base, ClipProgress(4, 1, 3), false))
    }

    @Test fun blankQueryKeepsGalleryAndTextModeIsUnaffected() {
        val gallery = base.copy(query = "", content = HomeContentState.Images)
        assertEquals(HomeContentState.Images, semanticContentState(gallery, ClipProgress(4, 0, 4), false))
        assertEquals(HomeContentState.NoResults, semanticContentState(base.copy(mode = SearchMode.TEXT), ClipProgress(4, 0, 4), true))
    }

}
