package com.darkesttrololo.memeizer.data.clip

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Separable antialiased bicubic resize, with PIL's a=-0.5 kernel and 8-bit intermediates. */
object ClipPreprocessor {
    fun pixels(argb: IntArray, width: Int, height: Int, size: Int, mean: FloatArray, std: FloatArray): FloatArray {
        require(width > 0 && height > 0 && argb.size == width * height)
        val resizedWidth = if (width <= height) size else (width.toLong() * size / height).toInt()
        val resizedHeight = if (height <= width) size else (height.toLong() * size / width).toInt()
        val horizontal = coefficients(width, resizedWidth)
        val vertical = coefficients(height, resizedHeight)
        val intermediate = IntArray(resizedWidth * height)
        for (y in 0 until height) for (x in 0 until resizedWidth) {
            intermediate[y * resizedWidth + x] = filter(argb, y * width, 1, horizontal[x])
        }
        val left = (resizedWidth - size) / 2
        val top = (resizedHeight - size) / 2
        val output = FloatArray(3 * size * size)
        for (y in 0 until size) for (x in 0 until size) {
            val pixel = filter(intermediate, x + left, resizedWidth, vertical[y + top])
            for (channel in 0..2) {
                val value = (pixel shr (16 - channel * 8)) and 255
                output[channel * size * size + y * size + x] = (value / 255f - mean[channel]) / std[channel]
            }
        }
        return output
    }

    private data class Weights(val start: Int, val values: DoubleArray)

    private fun coefficients(source: Int, target: Int): List<Weights> {
        val scale = source.toDouble() / target
        val filterScale = max(1.0, scale)
        val support = 2 * filterScale
        return List(target) { index ->
            val center = (index + 0.5) * scale
            val start = max(0, floor(center - support + 0.5).toInt())
            val end = min(source, floor(center + support + 0.5).toInt())
            val values = DoubleArray(end - start) { kernel((it + start - center + 0.5) / filterScale) }
            val sum = values.sum()
            Weights(start, DoubleArray(values.size) { values[it] / sum })
        }
    }

    private fun kernel(value: Double): Double {
        val x = kotlin.math.abs(value)
        return when {
            x < 1 -> ((1.5 * x - 2.5) * x) * x + 1
            x < 2 -> (((-0.5 * x + 2.5) * x - 4) * x) + 2
            else -> 0.0
        }
    }

    private fun filter(pixels: IntArray, base: Int, stride: Int, weights: Weights): Int {
        var output = -0x1000000
        for (channel in 0..2) {
            var value = 0.0
            for (i in weights.values.indices) {
                value += ((pixels[base + (weights.start + i) * stride] shr (16 - channel * 8)) and 255) * weights.values[i]
            }
            output = output or (floor(value + 0.5).toInt().coerceIn(0, 255) shl (16 - channel * 8))
        }
        return output
    }
}
