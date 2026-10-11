package com.darkesttrololo.memeizer.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.darkesttrololo.memeizer.data.AppContainer
import com.darkesttrololo.memeizer.data.indexing.IndexWorkState
import com.darkesttrololo.memeizer.data.search.SearchMode
import com.darkesttrololo.memeizer.data.db.ClipProgress
import com.darkesttrololo.memeizer.data.search.SearchResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(container: AppContainer) : ViewModel() {
    private val query = MutableStateFlow("")
    private val mode = MutableStateFlow(SearchMode.TEXT)
    private val request = combine(query, mode) { query, mode -> SearchRequest(query, mode) }
    private val results = homeSearchSnapshots(request, container.searchRepository::search)
    private val retry = SemanticRetry(
        workIsLoading = { container.indexScheduler.clipWorkState.first().isLoading },
        submit = { container.clipRepository.retryFailedImages(container.indexScheduler::enqueueClipIndexingAndAwait) },
    )
    private val semanticState = combine(
        container.clipRepository.progress, container.clipRepository.modelError,
        container.indexScheduler.clipWorkState.onStart { emit(IndexWorkState(isLoading = true)) }, retry.state,
    ) { progress, error, work, retry -> SemanticState(progress, error != null || work.hasFailed, work.isLoading, retry) }

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

    private val baseState = combine(
        request, loadedResults, folders, imageCount, workState,
    ) { request, currentResults, folderCount, currentImageCount, currentWorkState ->
        val snapshot = currentResults.valueOrNull()?.takeIf { it.request == request }
        HomeUiState(
            query = request.query,
            mode = request.mode,
            results = snapshot?.results.orEmpty(),
            modelFailed = snapshot?.modelFailed == true,
            content = homeContentState(
                query = request.query,
                results = snapshot?.results,
                folderCount = folderCount.valueOrNull(),
                imageCount = currentImageCount.valueOrNull(),
                workState = currentWorkState.valueOrNull(),
            ),
        )
    }
    val uiState: StateFlow<HomeUiState> = combine(baseState, semanticState) { base, semantic ->
        base.copy(
            content = semanticContentState(base, semantic.progress, semantic.modelFailed),
            clipProgress = semantic.progress,
            modelFailed = base.modelFailed || semantic.modelFailed,
            showSemanticRetry = semantic.progress.failed > 0 || semantic.retry.submitting || semantic.retry.schedulingFailed,
            semanticRetryEnabled = !semantic.workIsLoading && !semantic.retry.submitting,
            semanticRetryFailed = semantic.retry.schedulingFailed,
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

    fun retryFailedImages() {
        viewModelScope.launch { retry.retry() }
    }

    fun onModeChanged(value: SearchMode) {
        mode.value = value
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
    val mode: SearchMode = SearchMode.TEXT,
    val clipProgress: ClipProgress = ClipProgress(),
    val modelFailed: Boolean = false,
    val showSemanticRetry: Boolean = false,
    val semanticRetryEnabled: Boolean = false,
    val semanticRetryFailed: Boolean = false,
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
    data object SemanticIndexNotReady : HomeContentState
    data object SemanticIndexFailed : HomeContentState
    data object ModelError : HomeContentState
    data object NoResults : HomeContentState
}

internal fun semanticContentState(base: HomeUiState, progress: ClipProgress, modelFailed: Boolean): HomeContentState {
    if (base.mode != SearchMode.SEMANTIC || base.query.isBlank() || base.content == HomeContentState.NoFolders) {
        return base.content
    }
    if (base.modelFailed || modelFailed) {
        return if (base.results.isNotEmpty()) HomeContentState.Images else HomeContentState.ModelError
    }
    if (base.content == HomeContentState.InitialLoading) return base.content
    if (base.results.isNotEmpty()) return HomeContentState.Images
    if (progress.total > 0 && progress.ready == 0) {
        return if (progress.failed == progress.total) HomeContentState.SemanticIndexFailed
        else HomeContentState.SemanticIndexNotReady
    }
    return base.content
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
internal data class SearchRequest(val query: String, val mode: SearchMode)
internal data class SearchSnapshot(val request: SearchRequest, val results: List<SearchResult>, val modelFailed: Boolean = false)
private data class SemanticState(
    val progress: ClipProgress,
    val modelFailed: Boolean,
    val workIsLoading: Boolean,
    val retry: SemanticRetryState,
)
private fun <T> loaded(value: T): LoadState<T> = Loaded(value)
private fun <T> LoadState<T>.valueOrNull(): T? = (this as? Loaded)?.value

data class ViewerSession(val results: List<SearchResult>, val page: Int)

@OptIn(ExperimentalCoroutinesApi::class)
internal fun homeSearchSnapshots(
    requests: kotlinx.coroutines.flow.Flow<SearchRequest>,
    search: (String, SearchMode) -> kotlinx.coroutines.flow.Flow<List<SearchResult>>,
    debounceMillis: Long = 300,
): kotlinx.coroutines.flow.Flow<SearchSnapshot> = requests.flatMapLatest { request ->
    kotlinx.coroutines.flow.flow {
        if (request.query.isNotBlank()) delay(debounceMillis)
        emitAll(search(request.query, request.mode).map { SearchSnapshot(request, it) })
    }.catch { error ->
        if (error is kotlinx.coroutines.CancellationException) throw error
        if (request.mode != SearchMode.SEMANTIC) throw error
        emit(SearchSnapshot(request, emptyList(), modelFailed = true))
    }
}
