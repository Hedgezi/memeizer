package com.darkesttrololo.memeizer

import android.app.Application
import androidx.work.Configuration
import com.darkesttrololo.memeizer.data.AppContainer

class MemeizerApp : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(container.workerFactory)
            .build()
}
