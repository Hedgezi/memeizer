package com.darkesttrololo.memeizer.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.darkesttrololo.memeizer.data.AppContainer
import com.darkesttrololo.memeizer.data.search.SearchResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(container: AppContainer) : ViewModel() {
    private val query = MutableStateFlow("")
    private val results = query.flatMapLatest { container.searchRepository.search(it) }

    val uiState: StateFlow<HomeUiState> = combine(
        query,
        results,
        container.indexRepository.observeImageCount(),
    ) { currentQuery, currentResults, imageCount ->
        HomeUiState(
            query = currentQuery,
            results = currentResults,
            indexedImageCount = imageCount,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

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
    val indexedImageCount: Int = 0,
)
