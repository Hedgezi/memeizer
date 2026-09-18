package com.darkesttrololo.memeizer.ui.home

import com.darkesttrololo.memeizer.data.indexing.IndexWorkState
import com.darkesttrololo.memeizer.data.search.SearchResult
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeContentStateTest {
    private val image = SearchResult(1, "content://image", "image.png", "", null, null, null)

    @Test
    fun initialDataMustBeLoadedBeforeFolderPrompt() {
        assertEquals(
            HomeContentState.InitialLoading,
            homeContentState("", null, null, null, null),
        )
        assertEquals(
            HomeContentState.NoFolders,
            state(folderCount = 0),
        )
    }

    @Test
    fun imagesAreShownWhileIndexingContinues() {
        assertEquals(
            HomeContentState.Images,
            state(results = listOf(image), imageCount = 1, loading = true),
        )
    }

    @Test
    fun emptyGalleryDistinguishesLoadingEmptyAndFailure() {
        assertEquals(HomeContentState.Loading, state(loading = true))
        assertEquals(HomeContentState.EmptyFolders, state())
        assertEquals(HomeContentState.IndexingError, state(failed = true))
    }

    @Test
    fun completedSearchWithoutMatchesShowsNoResults() {
        assertEquals(
            HomeContentState.NoResults,
            state(query = "cats", imageCount = 12),
        )
    }

    private fun state(
        query: String = "",
        results: List<SearchResult> = emptyList(),
        folderCount: Int = 1,
        imageCount: Int = 0,
        loading: Boolean = false,
        failed: Boolean = false,
    ) = homeContentState(
        query,
        results,
        folderCount,
        imageCount,
        IndexWorkState(isLoading = loading, hasFailed = failed),
    )
}
