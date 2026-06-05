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
    private val ocrEngines: List<OcrEngine>,
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
                        engine = ocrEngines.joinToString(separator = "+") { it.engineName },
                        language = ocrEngines.joinToString(separator = "+") { it.language },
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
            "[${result.engine}:${result.language}]\n${normalizeOcrText(result)}"
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
