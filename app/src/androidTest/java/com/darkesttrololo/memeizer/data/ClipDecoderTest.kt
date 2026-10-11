package com.darkesttrololo.memeizer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darkesttrololo.memeizer.data.clip.ClipImageDecoder
import com.darkesttrololo.memeizer.data.clip.ClipImageLimits
import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClipDecoderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun headerLimitsIncludeThresholdAndRejectAboveItOnEitherAxis() {
        for ((width, height, accepted) in listOf(
            Triple(2048, 2048, true), Triple(2048, 2049, false), Triple(2049, 2048, false),
            Triple(4096, 1, true), Triple(1, 4096, true), Triple(4097, 1, false), Triple(1, 4097, false),
            Triple(4096, 1024, true), Triple(1024, 4096, true),
            Triple(4096, 1025, false), Triple(1025, 4096, false),
        )) {
            val file = syntheticPng(context, width, height)
            try {
                try {
                    val bitmap = ClipImageDecoder.decode(ImageDecoder.createSource(file))
                    try {
                        assertTrue("$width x $height must be rejected in the header", accepted)
                        assertEquals(width, bitmap.width)
                        assertEquals(height, bitmap.height)
                        assertNotEquals(Bitmap.Config.HARDWARE, bitmap.config)
                        assertTrue(bitmap.colorSpace!!.isSrgb)
                    } finally { bitmap.recycle() }
                } catch (error: IOException) {
                    if (accepted) throw error
                    assertEquals("Image dimensions exceed CLIP decoding limits", error.message)
                }
            } finally { file.delete() }
        }
    }

    @Test fun dimensionProductCannotOverflowTheLimitCheck() {
        try { ClipImageLimits.check(65_536, 65_536); fail("Expected rejection") }
        catch (_: IOException) { }
    }
}

internal fun syntheticPng(context: Context, width: Int, height: Int): File {
    val file = File.createTempFile("clip-synthetic-", ".png", context.cacheDir)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    try {
        bitmap.eraseColor(android.graphics.Color.GREEN)
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    } finally { bitmap.recycle() }
    return file
}
