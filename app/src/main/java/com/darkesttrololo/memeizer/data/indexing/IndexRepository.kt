package com.darkesttrololo.memeizer.data.indexing

import android.net.Uri
import androidx.room.withTransaction
import com.darkesttrololo.memeizer.data.db.FolderImageEntity
import com.darkesttrololo.memeizer.data.db.IndexStatus
import com.darkesttrololo.memeizer.data.db.IndexedImageEntity
import com.darkesttrololo.memeizer.data.db.MemeSearchFtsEntity
import com.darkesttrololo.memeizer.data.db.MemeizerDatabase
import com.darkesttrololo.memeizer.data.db.OcrResultEntity
import com.darkesttrololo.memeizer.data.folder.ScannedImage
import com.darkesttrololo.memeizer.data.ocr.OcrEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class IndexRepository(
    private val database: MemeizerDatabase,
    private val scanner: suspend (Uri) -> List<ScannedImage>,
    private val ocrEngines: List<OcrEngine>,
) {
    private val folderDao = database.folderDao()
    private val imageDao = database.imageDao()
    private val ocrDao = database.ocrDao()
    private val searchDao = database.searchDao()
    private val links = database.folderImageDao()

    fun observeImageCount() = imageDao.observeImageCount()

    suspend fun indexSelectedFolders(forceReindex: Boolean) = withContext(Dispatchers.IO) {
        database.indexingMutex.withLock {
            folderDao.getEnabledFolders().forEach { folder ->
                // If this throws (including cancellation), do not reconcile a partial snapshot.
                val snapshot = scanner(Uri.parse(folder.treeUri))
                currentCoroutineContext().ensureActive()
                val work = database.withTransaction {
                    if (!folderDao.isEnabled(folder.id)) return@withTransaction emptyList()
                    val pending = snapshot.distinctBy { it.documentKey }.mapNotNull { image ->
                        val existing = imageDao.findByDocumentKey(image.documentKey)
                        val id = existing?.id ?: imageDao.insert(
                            IndexedImageEntity(
                                folderId = folder.id, uri = image.uri.toString(),
                                displayName = image.displayName, mimeType = image.mimeType,
                                size = image.size, lastModified = image.lastModified,
                                contentKey = image.contentKey, indexStatus = IndexStatus.PENDING.name,
                                createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis(),
                            ),
                        )
                        links.insert(FolderImageEntity(folder.id, image.documentKey, id, image.uri.toString()))
                        if (!forceReindex && existing?.contentKey == image.contentKey && existing.indexStatus == IndexStatus.INDEXED.name) {
                            null
                        } else {
                            searchDao.deleteForImage(id)
                            ocrDao.deleteForImage(id)
                            imageDao.updateContentKeyAndStatus(id, image.contentKey, IndexStatus.PENDING.name, System.currentTimeMillis())
                            id to image
                        }
                    }
                    // Replace membership only once the full scan has succeeded.
                    val keys = snapshot.mapTo(hashSetOf()) { it.documentKey }
                    val retained = links.forFolder(folder.id).filter { it.documentKey in keys }
                    links.deleteFolder(folder.id)
                    retained.forEach { links.insert(it) }
                    database.pruneUnselectedImages()
                    pending
                }
                work.forEach { (id, image) -> runOcr(folder.id, id, image.uri) }
            }
        }
    }

    private suspend fun runOcr(folderId: Long, imageId: Long, imageUri: Uri) {
        val started = database.withTransaction {
            if (!folderDao.isEnabled(folderId) || imageDao.findById(imageId) == null) return@withTransaction false
            imageDao.updateStatus(imageId, IndexStatus.INDEXING.name, System.currentTimeMillis())
            true
        }
        if (!started) return
        val result = try {
            Result.success(recognizeAllLanguages(imageUri))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
        currentCoroutineContext().ensureActive()
        database.withTransaction {
            // A folder may have been removed while OCR ran. Never resurrect its data.
            if (!folderDao.isEnabled(folderId) || imageDao.findById(imageId) == null) return@withTransaction
            val value = result.getOrNull()
            val now = System.currentTimeMillis()
            ocrDao.deleteForImage(imageId)
            searchDao.deleteForImage(imageId)
            ocrDao.insert(
                OcrResultEntity(
                    imageId = imageId,
                    engine = value?.engine ?: ocrEngines.joinToString("+") { it.engineName },
                    language = value?.language ?: ocrEngines.joinToString("+") { it.language },
                    text = value?.text.orEmpty(), confidence = value?.confidence,
                    errorMessage = result.exceptionOrNull()?.message,
                    createdAt = now, updatedAt = now,
                ),
            )
            if (value != null) searchDao.insert(MemeSearchFtsEntity(imageId = imageId, text = normalize(value.text)))
            imageDao.updateStatus(imageId, if (value != null) IndexStatus.INDEXED.name else IndexStatus.FAILED.name, now)
        }
    }

    private fun normalize(text: String): String = text
        .lowercase()
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun normalizeOcrText(result: com.darkesttrololo.memeizer.data.ocr.OcrResult): String {
        val text = result.text.trim()
        return if (result.engine == "paddleocr-ncnn" && result.language == "cyrillic") {
            text.mapLatinHomoglyphsToCyrillic()
        } else {
            text
        }
    }

    private fun String.mapLatinHomoglyphsToCyrillic(): String = map { char ->
        LATIN_TO_CYRILLIC[char] ?: char
    }.joinToString(separator = "")

    private suspend fun recognizeAllLanguages(imageUri: Uri): com.darkesttrololo.memeizer.data.ocr.OcrResult {
        val results = ocrEngines.map { engine -> engine.recognize(imageUri) }
        val combinedText = results.joinToString(separator = "\n\n") { result ->
            normalizeOcrText(result)
        }.trim()
        val confidences = results.mapNotNull { it.confidence }

        return com.darkesttrololo.memeizer.data.ocr.OcrResult(
            text = combinedText,
            confidence = confidences.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            engine = ocrEngines.joinToString(separator = "+") { it.engineName },
            language = ocrEngines.joinToString(separator = "+") { it.language },
        )
    }

    private companion object {
        val LATIN_TO_CYRILLIC = mapOf(
            'A' to 'А',
            'B' to 'В',
            'C' to 'С',
            'E' to 'Е',
            'H' to 'Н',
            'K' to 'К',
            'M' to 'М',
            'O' to 'О',
            'P' to 'Р',
            'T' to 'Т',
            'X' to 'Х',
            'Y' to 'У',
            'a' to 'а',
            'b' to 'в',
            'c' to 'с',
            'e' to 'е',
            'h' to 'н',
            'k' to 'к',
            'm' to 'м',
            'o' to 'о',
            'p' to 'р',
            't' to 'т',
            'x' to 'х',
            'y' to 'у',
        )
    }
}
