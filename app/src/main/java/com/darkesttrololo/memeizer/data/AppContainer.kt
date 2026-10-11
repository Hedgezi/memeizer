package com.darkesttrololo.memeizer.data

import android.content.Context
import androidx.room.Room
import androidx.work.WorkerFactory
import com.darkesttrololo.memeizer.data.db.MemeizerDatabase
import com.darkesttrololo.memeizer.data.folder.FolderOverlapChecker
import com.darkesttrololo.memeizer.data.folder.FolderRepository
import com.darkesttrololo.memeizer.data.folder.FolderScanner
import com.darkesttrololo.memeizer.data.indexing.IndexRepository
import com.darkesttrololo.memeizer.data.indexing.IndexScheduler
import com.darkesttrololo.memeizer.data.indexing.MemeizerWorkerFactory
import com.darkesttrololo.memeizer.data.ocr.MlKitLatinOcrEngine
import com.darkesttrololo.memeizer.data.ocr.NcnnPaddleOcrEngine
import com.darkesttrololo.memeizer.data.search.SearchRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: MemeizerDatabase = Room.databaseBuilder(
        appContext,
        MemeizerDatabase::class.java,
        "memeizer.db",
    ).addMigrations(MemeizerDatabase.MIGRATION_1_2).build()

    private val folderScanner = FolderScanner(appContext)
    private val ocrEngines = listOf(
        NcnnPaddleOcrEngine(appContext),
        MlKitLatinOcrEngine(appContext),
    )

    val folderRepository = FolderRepository(database, FolderOverlapChecker(appContext.contentResolver)::overlaps)
    val indexScheduler = IndexScheduler(appContext)
    val clipEngine = com.darkesttrololo.memeizer.data.clip.OnnxClipEmbeddingEngine(appContext)
    val clipRepository = com.darkesttrololo.memeizer.data.clip.ClipRepository(database, clipEngine)
    val indexRepository = IndexRepository(
        database = database,
        scanner = folderScanner::scan,
        ocrEngines = ocrEngines,
        onFilesUpdated = indexScheduler::enqueueClipIndexing,
    )
    val searchRepository = SearchRepository(database.searchDao(), clipRepository)

    val workerFactory: WorkerFactory = MemeizerWorkerFactory(indexRepository, clipRepository)
}
