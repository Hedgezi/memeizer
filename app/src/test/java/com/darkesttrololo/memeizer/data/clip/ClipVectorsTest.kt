package com.darkesttrololo.memeizer.data.clip

import org.junit.Assert.*
import org.junit.Test

class ClipVectorsTest {
    @Test fun normalizationRoundTripsLittleEndianBlob() {
        val vector = ClipVectors.normalize(floatArrayOf(3f, 4f), 2)
        assertArrayEquals(floatArrayOf(.6f, .8f), vector, .000001f)
        assertArrayEquals(vector, ClipVectors.decode(ClipVectors.encode(vector), 2), 0f)
        assertEquals(1f, ClipVectors.dot(vector, vector), .000001f)
    }

    @Test fun invalidInferenceVectorsAreRejected() {
        for (value in listOf(floatArrayOf(0f, 0f), floatArrayOf(Float.NaN, 1f), floatArrayOf(Float.POSITIVE_INFINITY, 1f), floatArrayOf(1f))) {
            try { ClipVectors.normalize(value, 2); fail("Expected invalid vector rejection") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun corruptStoredVectorsAreExcluded() {
        for (value in listOf(null, byteArrayOf(1), ClipVectors.encode(floatArrayOf(0f, 0f)), ClipVectors.encode(floatArrayOf(Float.NaN, 1f)), ClipVectors.encode(floatArrayOf(3f, 4f)))) {
            assertNull(ClipVectors.decode(value, 2))
        }
        assertNull(ClipVectors.decode(byteArrayOf(), Int.MAX_VALUE))
    }

    @Test fun topThirtyUsesScoreThenAscendingImageIdAcrossPages() {
        val heap = ClipTopK<Long>()
        for (id in 600L downTo 1L) heap.add(id, if (id <= 40) 1f else .5f, id)
        assertEquals((1L..30L).toList(), heap.results())
        heap.add(999, Float.NaN, 999)
        assertEquals(30, heap.results().size)
    }

    @Test fun negativeScoresAreStillNearestResults() {
        val heap = ClipTopK<String>(2)
        heap.add(1, -.8f, "third")
        heap.add(2, -.1f, "first")
        heap.add(3, -.4f, "second")
        assertEquals(listOf("first", "second"), heap.results())
    }

    @Test fun constantImagesKeepTheirColorThroughResizeAndCrop() {
        val values = ClipPreprocessor.pixels(IntArray(100 * 300) { 0xff804020.toInt() }, 100, 300, 224,
            floatArrayOf(0f, 0f, 0f), floatArrayOf(1f, 1f, 1f))
        assertEquals(3 * 224 * 224, values.size)
        assertTrue(values.take(224 * 224).all { it == 128 / 255f })
        assertTrue(values.drop(224 * 224).take(224 * 224).all { it == 64 / 255f })
    }
}
