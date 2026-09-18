package com.darkesttrololo.memeizer.ui.folders

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.darkesttrololo.memeizer.data.AppContainer
import com.darkesttrololo.memeizer.data.db.IndexedFolderEntity
import com.darkesttrololo.memeizer.data.folder.OverlappingFolderException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FoldersViewModel(private val container: AppContainer) : ViewModel() {
    private val error = MutableStateFlow<FolderError?>(null)
    val uiState: StateFlow<FoldersUiState> = combine(
        container.folderRepository.observeFolders(), error,
    ) { folders, currentError -> FoldersUiState(folders = folders, error = currentError) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoldersUiState())

    fun addFolder(uri: Uri) {
        viewModelScope.launch {
            error.value = null
            try {
                container.folderRepository.addFolder(uri, uri.lastPathSegment ?: uri.toString())
                container.indexScheduler.schedulePeriodicIndexing()
                container.indexScheduler.enqueueManualIndexing(forceReindex = false, replace = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: OverlappingFolderException) {
                error.value = FolderError.OverlappingFolder
            } catch (_: Exception) {
                error.value = FolderError.CannotAddFolder
            }
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
    val error: FolderError? = null,
)

enum class FolderError {
    OverlappingFolder,
    CannotAddFolder,
}
