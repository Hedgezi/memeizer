package com.darkesttrololo.memeizer.data.clip

import android.net.Uri

interface ClipEmbeddingEngine {
    val modelVersion: String
    val dimension: Int
    suspend fun initialize()
    suspend fun embedImage(uri: Uri): FloatArray
    suspend fun embedText(text: String): FloatArray
}

class ClipModelException(message: String, cause: Throwable? = null) : Exception(message, cause)
