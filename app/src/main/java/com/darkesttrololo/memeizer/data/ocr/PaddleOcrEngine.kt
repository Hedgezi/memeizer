package com.darkesttrololo.memeizer.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.baidu.paddle.lite.demo.ocr.Predictor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

class PaddleOcrEngine(
    private val context: Context,
) : OcrEngine {
    override val engineName: String = "paddleocr"
    override val language: String = "cyrillic"

    private val mutex = Mutex()
    private var predictor: Predictor? = null

    override suspend fun recognize(imageUri: Uri): OcrResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            val bitmap = decodeScaledBitmap(imageUri)
            try {
                val paddlePredictor = getPredictor()
                paddlePredictor.setInputImage(bitmap)
                check(paddlePredictor.runModel(RUN_DET, RUN_CLS, RUN_REC)) { "PaddleOCR inference failed" }
                OcrResult(
                    text = extractRecognizedText(paddlePredictor.outputResult()),
                    confidence = null,
                    engine = engineName,
                    language = language,
                )
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun getPredictor(): Predictor {
        predictor?.let { return it }

        ensureAssetsPresent()
        return Predictor().also { newPredictor ->
            check(
                newPredictor.init(
                    context,
                    MODEL_PATH,
                    LABEL_PATH,
                    USE_OPENCL,
                    CPU_THREAD_NUM,
                    CPU_POWER_MODE,
                    DET_LONG_SIZE,
                    SCORE_THRESHOLD,
                ),
            ) { "PaddleOCR model initialization failed" }
            predictor = newPredictor
        }
    }

    private fun ensureAssetsPresent() {
        val requiredAssets = listOf(
            "$MODEL_PATH/det.nb",
            "$MODEL_PATH/rec.nb",
            "$MODEL_PATH/cls.nb",
            LABEL_PATH,
        )
        requiredAssets.forEach { assetPath ->
            runCatching { context.assets.open(assetPath).close() }
                .getOrElse { error -> throw IllegalStateException("Missing PaddleOCR asset: $assetPath", error) }
        }
    }

    private fun extractRecognizedText(rawText: String): String = rawText
        .lineSequence()
        .mapNotNull { line ->
            val recIndex = line.indexOf("Rec:")
            if (recIndex < 0) return@mapNotNull null
            line.substring(recIndex + 4)
                .substringBeforeLast(",")
                .trim()
                .takeIf { it.isNotBlank() }
        }
        .joinToString(separator = "\n")

    private fun decodeScaledBitmap(uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }

        val maxSide = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        val sampleSize = (maxSide.toFloat() / MAX_SIDE).roundToInt().coerceAtLeast(1)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }

        return context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(BitmapFactory.decodeStream(input, null, options)) { "Cannot decode image" }
        }
    }

    fun close() {
        predictor?.releaseModel()
        predictor = null
    }

    private companion object {
        const val MODEL_PATH = "paddleocr/models/cyrillic_PP-OCRv3"
        const val LABEL_PATH = "paddleocr/labels/cyrillic_dict.txt"
        const val USE_OPENCL = 0
        const val CPU_THREAD_NUM = 4
        const val CPU_POWER_MODE = "LITE_POWER_FULL"
        const val DET_LONG_SIZE = 960
        const val SCORE_THRESHOLD = 0.1f
        const val RUN_DET = 1
        const val RUN_CLS = 1
        const val RUN_REC = 1
        const val MAX_SIDE = 2000
    }
}
