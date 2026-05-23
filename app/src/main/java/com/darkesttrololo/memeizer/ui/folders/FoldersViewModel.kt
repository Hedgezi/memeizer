package com.darkesttrololo.memeizer.ui.folders

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.darkesttrololo.memeizer.data.AppContainer
import com.darkesttrololo.memeizer.data.db.IndexedFolderEntity
import com.darkesttrololo.memeizer.data.indexing.IndexWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FoldersViewModel(
    private val context: Context,
    private val container: AppContainer,
) : ViewModel() {
    val uiState: StateFlow<FoldersUiState> = container.folderRepository.observeFolders()
        .map { folders -> FoldersUiState(folders = folders) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoldersUiState())

    fun addFolder(uri: Uri) {
        viewModelScope.launch {
            container.folderRepository.addFolder(uri, uri.lastPathSegment ?: uri.toString())
            startIndexing(replace = true)
        }
    }

    fun removeFolder(folderId: Long) {
        viewModelScope.launch {
            container.folderRepository.removeFolder(folderId)
        }
    }

    fun startIndexing(replace: Boolean) {
        val request = OneTimeWorkRequestBuilder<IndexWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            IndexWorker.UNIQUE_WORK_NAME,
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        fun factory(context: Context, container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = FoldersViewModel(context, container) as T
        }
    }
}

data class FoldersUiState(
    val folders: List<IndexedFolderEntity> = emptyList(),
)
