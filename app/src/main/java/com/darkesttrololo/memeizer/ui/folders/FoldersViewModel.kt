package com.darkesttrololo.memeizer.ui.folders

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.darkesttrololo.memeizer.data.AppContainer
import com.darkesttrololo.memeizer.data.db.IndexedFolderEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FoldersViewModel(private val container: AppContainer) : ViewModel() {
    val uiState: StateFlow<FoldersUiState> = container.folderRepository.observeFolders()
        .map { folders -> FoldersUiState(folders = folders) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoldersUiState())

    fun addFolder(uri: Uri) {
        viewModelScope.launch {
            container.folderRepository.addFolder(uri, uri.lastPathSegment ?: uri.toString())
            container.indexScheduler.schedulePeriodicIndexing()
        }
    }

    fun removeFolder(folderId: Long) {
        viewModelScope.launch {
            container.folderRepository.removeFolder(folderId)
        }
    }

    fun startIndexing(replace: Boolean, forceReindex: Boolean) {
        container.indexScheduler.enqueueManualIndexing(forceReindex = forceReindex, replace = replace)
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = FoldersViewModel(container) as T
        }
    }
}

data class FoldersUiState(
    val folders: List<IndexedFolderEntity> = emptyList(),
)
