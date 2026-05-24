package com.darkesttrololo.memeizer.data

import android.content.Context
import androidx.room.Room
import androidx.work.WorkerFactory
import com.darkesttrololo.memeizer.data.db.MemeizerDatabase
import com.darkesttrololo.memeizer.data.folder.FolderRepository
import com.darkesttrololo.memeizer.data.folder.FolderScanner
import com.darkesttrololo.memeizer.data.indexing.IndexRepository
import com.darkesttrololo.memeizer.data.indexing.MemeizerWorkerFactory
import com.darkesttrololo.memeizer.data.ocr.MlKitLatinOcrEngine
import com.darkesttrololo.memeizer.data.ocr.TesseractDataInstaller
import com.darkesttrololo.memeizer.data.ocr.TesseractOcrEngine
import com.darkesttrololo.memeizer.data.search.SearchRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: MemeizerDatabase = Room.databaseBuilder(
        appContext,
        MemeizerDatabase::class.java,
        "memeizer.db",
    ).build()

    private val folderScanner = FolderScanner(appContext)
    private val dataInstaller = TesseractDataInstaller(appContext)
    private val ocrEngines = listOf(
        TesseractOcrEngine(appContext, dataInstaller, language = "rus"),
        MlKitLatinOcrEngine(appContext),
    )

    val folderRepository = FolderRepository(database.folderDao())
    val indexRepository = IndexRepository(
        folderDao = database.folderDao(),
        imageDao = database.imageDao(),
        ocrDao = database.ocrDao(),
        searchDao = database.searchDao(),
        scanner = folderScanner,
        ocrEngines = ocrEngines,
    )
    val searchRepository = SearchRepository(database.searchDao())

    val workerFactory: WorkerFactory = MemeizerWorkerFactory(indexRepository)
}
