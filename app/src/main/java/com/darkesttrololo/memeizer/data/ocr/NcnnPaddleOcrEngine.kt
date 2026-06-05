package com.darkesttrololo.memeizer.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.equationl.ncnnandroidppocr.bean.OcrTextLineResult
import com.equationl.ncnnandroidppocr.OCR
import com.equationl.ncnnandroidppocr.bean.Device
import com.equationl.ncnnandroidppocr.bean.DrawModel
import com.equationl.ncnnandroidppocr.bean.ImageSize
import com.equationl.ncnnandroidppocr.bean.ModelType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

class NcnnPaddleOcrEngine(
    private val context: Context,
) : OcrEngine {
    override val engineName: String = "paddleocr-ncnn"
    override val language: String = "cyrillic"

    private val mutex = Mutex()
    private var ocr: OCR? = null

    override suspend fun recognize(imageUri: Uri): OcrResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            val bitmap = decodeScaledBitmap(imageUri)
            try {
                val result = requireNotNull(getOcr().detectBitmap(bitmap, DrawModel.None)) {
                    "NCNN PaddleOCR returned no result"
                }

                val textLines = sortTextLines(result.textLines)
                val text = textLines.joinToString(separator = "\n") { it.text }
                    .ifBlank { result.text }

                OcrResult(
                    text = text,
                    confidence = textLines
                        .map { (it.confidence * 100).roundToInt() }
                        .takeIf { it.isNotEmpty() }
                        ?.average()
                        ?.roundToInt(),
                    engine = engineName,
                    language = language,
                )
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun getOcr(): OCR {
        ocr?.let { return it }

        return OCR().also { newOcr ->
            check(newOcr.initModelFromAssert(context.assets, ModelType.Mobile, ImageSize.Size1080, Device.CPU)) {
                "Failed to initialize NCNN PaddleOCR model"
            }
            ocr = newOcr
        }
    }

    private fun sortTextLines(textLines: List<OcrTextLineResult>): List<OcrTextLineResult> {
        if (textLines.size < 2) return textLines

        val rowTolerance = textLines
            .mapNotNull { line ->
                val ys = line.points.map { it.y }
                (ys.maxOrNull() ?: return@mapNotNull null) - (ys.minOrNull() ?: return@mapNotNull null)
            }
            .sorted()
            .let { heights -> heights.getOrNull(heights.size / 2) ?: 32 }
            .coerceAtLeast(24) * 3 / 4

        return textLines.sortedWith { left, right ->
            val leftCenterY = left.points.map { it.y }.average().takeIf { !it.isNaN() } ?: 0.0
            val rightCenterY = right.points.map { it.y }.average().takeIf { !it.isNaN() } ?: 0.0
            val yDiff = leftCenterY - rightCenterY

            if (kotlin.math.abs(yDiff) <= rowTolerance) {
                val leftX = left.points.minOfOrNull { it.x } ?: 0
                val rightX = right.points.minOfOrNull { it.x } ?: 0
                leftX.compareTo(rightX)
            } else {
                yDiff.compareTo(0.0)
            }
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

    fun close() {
        ocr?.release()
        ocr = null
    }

    private companion object {
        const val MAX_SIDE = 2000
    }
}
