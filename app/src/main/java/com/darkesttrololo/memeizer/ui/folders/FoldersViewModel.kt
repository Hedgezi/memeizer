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
    private val errorMessage = MutableStateFlow<String?>(null)
    val uiState: StateFlow<FoldersUiState> = combine(
        container.folderRepository.observeFolders(), errorMessage,
    ) { folders, error -> FoldersUiState(folders = folders, errorMessage = error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoldersUiState())

    fun addFolder(uri: Uri) {
        viewModelScope.launch {
            errorMessage.value = null
            try {
                container.folderRepository.addFolder(uri, uri.lastPathSegment ?: uri.toString())
                container.indexScheduler.schedulePeriodicIndexing()
                container.indexScheduler.enqueueManualIndexing(forceReindex = false, replace = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: OverlappingFolderException) {
                errorMessage.value = "This folder is already selected, contains a selected folder, or is inside one."
            } catch (_: Exception) {
                errorMessage.value = "Could not check or add this folder. Check access and try again."
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
    val errorMessage: String? = null,
)
