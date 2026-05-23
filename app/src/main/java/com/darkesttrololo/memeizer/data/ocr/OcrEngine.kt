package com.darkesttrololo.memeizer.data.ocr

import android.net.Uri

interface OcrEngine {
    suspend fun recognize(imageUri: Uri): OcrResult
}

data class OcrResult(
    val text: String,
    val confidence: Int?,
    val engine: String,
    val language: String,
)
