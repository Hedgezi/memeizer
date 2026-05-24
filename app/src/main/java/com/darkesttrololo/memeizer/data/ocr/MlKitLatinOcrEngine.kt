package com.darkesttrololo.memeizer.data.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class MlKitLatinOcrEngine(private val context: Context) : OcrEngine {
    override val engineName: String = "mlkit"
    override val language: String = "latin"

    override suspend fun recognize(imageUri: Uri): OcrResult = withContext(Dispatchers.IO) {
        val image = InputImage.fromFilePath(context, imageUri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val result = recognizer.process(image).await()

        OcrResult(
            text = result.text,
            confidence = null,
            engine = engineName,
            language = language,
        )
    }
}
