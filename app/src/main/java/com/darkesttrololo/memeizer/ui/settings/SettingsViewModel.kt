package com.darkesttrololo.memeizer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.darkesttrololo.memeizer.data.AppContainer
import com.darkesttrololo.memeizer.data.indexing.IndexingMode

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val indexingMode = container.indexingSettings.mode

    fun setIndexingMode(mode: IndexingMode) {
        if (indexingMode.value == mode) return
        container.indexingSettings.setMode(mode)
        container.indexScheduler.schedulePeriodicIndexing()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(container) as T
        }
    }
}
