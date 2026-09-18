package com.darkesttrololo.memeizer.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.darkesttrololo.memeizer.data.AppContainer
import com.darkesttrololo.memeizer.data.indexing.IndexWorkState
import com.darkesttrololo.memeizer.data.search.SearchResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(container: AppContainer) : ViewModel() {
    private val query = MutableStateFlow("")
    private val results = query.flatMapLatest { currentQuery ->
        container.searchRepository.search(currentQuery).map { SearchSnapshot(currentQuery, it) }
    }

    private val folders = container.folderRepository.observeFolders()
        .map { loaded(it.size) }
        .onStart { emit(NotLoaded) }
    private val loadedResults = results
        .map { loaded(it) }
        .onStart { emit(NotLoaded) }
    private val imageCount = container.indexRepository.observeImageCount()
        .map { loaded(it) }
        .onStart { emit(NotLoaded) }
    private val workState = container.indexScheduler.workState
        .map { loaded(it) }
        .onStart { emit(NotLoaded) }

    val uiState: StateFlow<HomeUiState> = combine(
        query, loadedResults, folders, imageCount, workState,
    ) { currentQuery, currentResults, folderCount, currentImageCount, currentWorkState ->
        val resultSnapshot = currentResults.valueOrNull()?.takeIf { it.query == currentQuery }
        val resultsValue = resultSnapshot?.results.orEmpty()
        HomeUiState(
            query = currentQuery,
            results = resultsValue,
            content = homeContentState(
                query = currentQuery,
                results = resultSnapshot?.results,
                folderCount = folderCount.valueOrNull(),
                imageCount = currentImageCount.valueOrNull(),
                workState = currentWorkState.valueOrNull(),
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private val _viewerSession = MutableStateFlow<ViewerSession?>(null)
    val viewerSession: StateFlow<ViewerSession?> = _viewerSession

    fun openViewer(result: SearchResult) {
        val snapshot = uiState.value.results.toList()
        val page = snapshot.indexOfFirst { it.imageId == result.imageId }
        if (page >= 0) _viewerSession.value = ViewerSession(snapshot, page)
    }

    fun selectViewerPage(page: Int) {
        val session = _viewerSession.value ?: return
        if (page in session.results.indices) _viewerSession.value = session.copy(page = page)
    }

    fun closeViewer() {
        _viewerSession.value = null
    }

    fun onQueryChanged(value: String) {
        query.value = value
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(container) as T
        }
    }
}

data class HomeUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val content: HomeContentState = HomeContentState.InitialLoading,
)

sealed interface HomeContentState {
    data object InitialLoading : HomeContentState
    data object NoFolders : HomeContentState
    data object Loading : HomeContentState
    data object Images : HomeContentState
    data object EmptyFolders : HomeContentState
    data object IndexingError : HomeContentState
    data object NoResults : HomeContentState
}

internal fun homeContentState(
    query: String,
    results: List<SearchResult>?,
    folderCount: Int?,
    imageCount: Int?,
    workState: IndexWorkState?,
): HomeContentState {
    if (results == null || folderCount == null || imageCount == null || workState == null) {
        return HomeContentState.InitialLoading
    }
    if (folderCount == 0) return HomeContentState.NoFolders
    if (results.isNotEmpty()) return HomeContentState.Images
    if (workState.isLoading) return HomeContentState.Loading
    if (imageCount == 0 && workState.hasFailed) return HomeContentState.IndexingError
    if (query.isNotBlank() && imageCount > 0) return HomeContentState.NoResults
    return HomeContentState.EmptyFolders
}

private sealed interface LoadState<out T>
private data object NotLoaded : LoadState<Nothing>
private data class Loaded<T>(val value: T) : LoadState<T>
private data class SearchSnapshot(val query: String, val results: List<SearchResult>)
private fun <T> loaded(value: T): LoadState<T> = Loaded(value)
private fun <T> LoadState<T>.valueOrNull(): T? = (this as? Loaded)?.value

data class ViewerSession(val results: List<SearchResult>, val page: Int)
