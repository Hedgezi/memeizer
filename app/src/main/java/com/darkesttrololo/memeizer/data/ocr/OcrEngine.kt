package com.darkesttrololo.memeizer.data.ocr

import android.net.Uri

interface OcrEngine {
    val engineName: String
    val language: String

    suspend fun recognize(imageUri: Uri): OcrResult
}

data class OcrResult(
    val text: String,
    val confidence: Int?,
    val engine: String,
    val language: String,
)
