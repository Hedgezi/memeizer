package com.darkesttrololo.memeizer.data.ocr

import android.content.Context
import java.io.File

class TesseractDataInstaller(private val context: Context) {
    fun ensureInstalled(): File {
        val dataRoot = File(context.filesDir, "tesseract")
        val tessDataDir = File(dataRoot, "tessdata")
        tessDataDir.mkdirs()

        languages.forEach { language ->
            val fileName = "$language.traineddata"
            val target = File(tessDataDir, fileName)
            if (!target.exists()) {
                context.assets.open("tessdata/$fileName").use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }

        return dataRoot
    }

    private companion object {
        val languages = listOf("eng", "rus")
    }
}
