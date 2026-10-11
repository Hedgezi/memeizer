package com.darkesttrololo.memeizer.data.clip

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import java.io.IOException

internal object ClipImageDecoder {
    fun decode(source: ImageDecoder.Source): Bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        // The header callback runs before pixel allocation. Keep original pixels and EXIF orientation.
        ClipImageLimits.check(info.size.width, info.size.height)
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
    }
}

internal object ClipImageLimits {
    const val MAX_PIXELS = 4_194_304L
    const val MAX_SIDE = 4096

    fun check(width: Int, height: Int) {
        if (width <= 0 || height <= 0 || width > MAX_SIDE || height > MAX_SIDE ||
            width.toLong() * height > MAX_PIXELS
        ) throw IOException("Image dimensions exceed CLIP decoding limits")
    }
}
