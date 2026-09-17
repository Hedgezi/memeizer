package com.darkesttrololo.memeizer

import android.app.Application
import androidx.work.Configuration
import com.darkesttrololo.memeizer.data.AppContainer

class MemeizerApp : Application(), Configuration.Provider {
    // Providers may receive a cold-start call before Application.onCreate finishes.
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        container.indexScheduler.schedulePeriodicIndexing()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(container.workerFactory)
            .build()
}
