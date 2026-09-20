package com.darkesttrololo.memeizer.data.indexing

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class IndexingMode(val intervalHours: Long) {
    ECONOMICAL(24),
    ACTIVE(1),
}

class IndexingSettings(context: Context) {
    private val preferences = context.getSharedPreferences("indexing_settings", Context.MODE_PRIVATE)
    private val mutableMode = MutableStateFlow(
        IndexingMode.entries.firstOrNull { it.name == preferences.getString("mode", null) }
            ?: IndexingMode.ECONOMICAL,
    )
    val mode = mutableMode.asStateFlow()

    fun setMode(mode: IndexingMode) {
        preferences.edit().putString("mode", mode.name).apply()
        mutableMode.value = mode
    }
}
