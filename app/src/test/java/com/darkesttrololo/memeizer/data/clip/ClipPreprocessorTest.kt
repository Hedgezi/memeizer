package com.darkesttrololo.memeizer.data.clip

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.*
import org.junit.Test

class ClipPreprocessorTest {
    private val mean = floatArrayOf(.48145466f, .4578275f, .40821073f)
    private val std = floatArrayOf(.26862954f, .26130258f, .27577711f)

    @Test fun extremeAspectRatiosFitA256MiBHeapWithBoundedAllocations() {
        assertTrue(Runtime.getRuntime().maxMemory() <= 256L * 1024 * 1024)
        // Android compilation omits desktop JDK APIs; these tests run on the host JVM.
        val allocation = Class.forName("java.lang.management.ManagementFactory")
            .getMethod("getThreadMXBean").invoke(null)
        val allocatedBytes = Class.forName("com.sun.management.ThreadMXBean")
            .getMethod("getThreadAllocatedBytes", java.lang.Long.TYPE)
        fun allocated() = allocatedBytes.invoke(allocation, Thread.currentThread().id) as Long
        for ((width, height) in listOf(1 to 20_000, 20_000 to 1)) {
            val argb = IntArray(width * height) { -0x789abc }
            val before = allocated()
            assertTrue("Thread allocation accounting must be available", before >= 0)
            val output = ClipPreprocessor.pixels(argb, width, height, 224, mean, std)
            val allocated = allocated() - before
            println("$width x $height: $allocated allocated bytes; heap limit ${Runtime.getRuntime().maxMemory()}")
            assertEquals(3 * 224 * 224, output.size)
            assertTrue(output.all { it.isFinite() })
            assertTrue("Allocated $allocated bytes for $width x $height", allocated < 16L * 1024 * 1024)
            for (channel in 0..2) {
                val value = ((argb[0] shr (16 - channel * 8)) and 255) / 255f
                assertEquals((value - mean[channel]) / std[channel], output[channel * 224 * 224], 0f)
            }
        }
    }

    @Test fun cropIsBitExactWithPreviousFullResizeIncludingOddOffsetsAndDownsampling() {
        for ((width, height) in listOf(1 to 1, 19 to 37, 37 to 19, 225 to 451, 451 to 225,
            713 to 501, 501 to 713, 1024 to 3072, 3072 to 1024)) {
            val random = java.util.Random(42)
            val pixels = IntArray(width * height) { random.nextInt() or -0x1000000 }
            assertArrayEquals("$width x $height",
                LegacyClipPreprocessor.pixels(pixels, width, height, 224, mean, std),
                ClipPreprocessor.pixels(pixels, width, height, 224, mean, std), 0f)
        }
    }

    @Test fun existingPythonPreprocessingFixturesMatch() {
        val directory = File(requireNotNull(System.getProperty("clipReferenceDir")))
        for (name in listOf("portrait", "landscape", "square")) {
            val image = Class.forName("javax.imageio.ImageIO").getMethod("read", File::class.java)
                .invoke(null, File(directory, "$name.png"))
            val type = image.javaClass
            val width = type.getMethod("getWidth").invoke(image) as Int
            val height = type.getMethod("getHeight").invoke(image) as Int
            val intType = Integer.TYPE
            val pixels = type.getMethod("getRGB", intType, intType, intType, intType,
                IntArray::class.java, intType, intType).invoke(image, 0, 0, width, height, null, 0, width) as IntArray
            val actual = ClipPreprocessor.pixels(pixels, width, height, 224, mean, std)
            val reference = ByteBuffer.wrap(File(directory, "$name.pixels.bin").readBytes())
                .order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
            val expected = FloatArray(reference.remaining()).also { reference.get(it) }
            assertArrayEquals(name, expected, actual, .016f)
        }
    }

    @Test fun overflowingDimensionProductIsRejected() {
        try {
            ClipPreprocessor.pixels(intArrayOf(), 65_536, 65_536, 224, mean, std)
            fail("Invalid source must be rejected before any allocation")
        } catch (_: IllegalArgumentException) { }
    }
}

/** Separable antialiased bicubic resize, with PIL's a=-0.5 kernel and 8-bit intermediates. */
private object LegacyClipPreprocessor {
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
