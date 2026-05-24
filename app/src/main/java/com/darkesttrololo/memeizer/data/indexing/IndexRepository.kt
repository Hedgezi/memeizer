package com.darkesttrololo.memeizer.data.indexing

import android.net.Uri
import com.darkesttrololo.memeizer.data.db.FolderDao
import com.darkesttrololo.memeizer.data.db.ImageDao
import com.darkesttrololo.memeizer.data.db.IndexStatus
import com.darkesttrololo.memeizer.data.db.IndexedImageEntity
import com.darkesttrololo.memeizer.data.db.MemeSearchFtsEntity
import com.darkesttrololo.memeizer.data.db.OcrDao
import com.darkesttrololo.memeizer.data.db.OcrResultEntity
import com.darkesttrololo.memeizer.data.db.SearchDao
import com.darkesttrololo.memeizer.data.folder.FolderScanner
import com.darkesttrololo.memeizer.data.folder.ScannedImage
import com.darkesttrololo.memeizer.data.ocr.OcrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class IndexRepository(
    private val folderDao: FolderDao,
    private val imageDao: ImageDao,
    private val ocrDao: OcrDao,
    private val searchDao: SearchDao,
    private val scanner: FolderScanner,
    private val ocrEngine: OcrEngine,
) {
    fun observeImageCount() = imageDao.observeImageCount()

    suspend fun indexSelectedFolders(forceReindex: Boolean) = withContext(Dispatchers.IO) {
        folderDao.getEnabledFolders().forEach { folder ->
            scanner.scan(Uri.parse(folder.treeUri)).forEach { scannedImage ->
                val imageId = upsertScannedImage(folder.id, scannedImage, forceReindex) ?: return@forEach
                runOcr(imageId, scannedImage.uri)
            }
        }
    }

    private suspend fun upsertScannedImage(folderId: Long, scannedImage: ScannedImage, forceReindex: Boolean): Long? {
        val now = System.currentTimeMillis()
        val existing = imageDao.findByUri(scannedImage.uri.toString())

        if (!forceReindex && existing != null && existing.contentKey == scannedImage.contentKey && existing.indexStatus == IndexStatus.INDEXED.name) {
            return null
        }

        if (existing != null) {
            imageDao.updateContentKeyAndStatus(existing.id, scannedImage.contentKey, IndexStatus.PENDING.name, now)
            return existing.id
        }

        return imageDao.insert(
            IndexedImageEntity(
                folderId = folderId,
                uri = scannedImage.uri.toString(),
                displayName = scannedImage.displayName,
                mimeType = scannedImage.mimeType,
                size = scannedImage.size,
                lastModified = scannedImage.lastModified,
                contentKey = scannedImage.contentKey,
                indexStatus = IndexStatus.PENDING.name,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private suspend fun runOcr(imageId: Long, imageUri: Uri) {
        val now = System.currentTimeMillis()
        imageDao.updateStatus(imageId, IndexStatus.INDEXING.name, now)

        runCatching { recognizeAllLanguages(imageUri) }
            .onSuccess { result ->
                ocrDao.deleteForImage(imageId)
                ocrDao.insert(
                    OcrResultEntity(
                        imageId = imageId,
                        engine = result.engine,
                        language = result.language,
                        text = result.text,
                        confidence = result.confidence,
                        errorMessage = null,
                        createdAt = now,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                searchDao.deleteForImage(imageId)
                searchDao.insert(MemeSearchFtsEntity(imageId = imageId, text = normalize(result.text)))
                imageDao.updateStatus(imageId, IndexStatus.INDEXED.name, System.currentTimeMillis())
            }
            .onFailure { error ->
                ocrDao.deleteForImage(imageId)
                ocrDao.insert(
                    OcrResultEntity(
                        imageId = imageId,
                        engine = "tesseract",
                        language = "eng+rus",
                        text = "",
                        confidence = null,
                        errorMessage = error.message,
                        createdAt = now,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                imageDao.updateStatus(imageId, IndexStatus.FAILED.name, System.currentTimeMillis())
            }
    }

    private fun normalize(text: String): String = text
        .lowercase()
        .replace(Regex("\\s+"), " ")
        .trim()

    private suspend fun recognizeAllLanguages(imageUri: Uri): com.darkesttrololo.memeizer.data.ocr.OcrResult {
        val results = OCR_LANGUAGES.map { language -> ocrEngine.recognize(imageUri, language) }
        val combinedText = results.joinToString(separator = "\n\n") { result ->
            "[${result.language}]\n${result.text.trim()}"
        }.trim()
        val confidences = results.mapNotNull { it.confidence }

        return com.darkesttrololo.memeizer.data.ocr.OcrResult(
            text = combinedText,
            confidence = confidences.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            engine = "tesseract",
            language = OCR_LANGUAGES.joinToString(separator = "+"),
        )
    }

    private companion object {
        val OCR_LANGUAGES = listOf("rus", "eng")
    }
}
