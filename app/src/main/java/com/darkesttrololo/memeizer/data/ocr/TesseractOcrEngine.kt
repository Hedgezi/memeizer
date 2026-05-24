package com.darkesttrololo.memeizer.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

class TesseractOcrEngine(
    private val context: Context,
    private val dataInstaller: TesseractDataInstaller,
) : OcrEngine {
    override suspend fun recognize(imageUri: Uri, language: String): OcrResult = withContext(Dispatchers.IO) {
        val dataPath = dataInstaller.ensureInstalled().absolutePath
        val bitmap = decodeScaledBitmap(imageUri)
        val tess = TessBaseAPI()

        try {
            check(tess.init(dataPath, language)) { "Failed to initialize Tesseract" }
            tess.setImage(bitmap)
            val text = tess.utF8Text.orEmpty()
            OcrResult(
                text = text,
                confidence = tess.meanConfidence(),
                engine = ENGINE,
                language = language,
            )
        } finally {
            tess.recycle()
            bitmap.recycle()
        }
    }

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

    private companion object {
        const val ENGINE = "tesseract"
        const val MAX_SIDE = 2000
    }
}
