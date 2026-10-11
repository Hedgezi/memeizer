package com.darkesttrololo.memeizer.data.clip

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.PriorityQueue
import kotlin.math.sqrt

object ClipVectors {
    fun normalize(vector: FloatArray, dimension: Int): FloatArray {
        require(vector.size == dimension && vector.all { it.isFinite() }) { "Invalid CLIP vector" }
        val norm = sqrt(vector.sumOf { it.toDouble() * it })
        require(norm.isFinite() && norm > 0.0) { "Zero CLIP vector" }
        return FloatArray(dimension) { (vector[it] / norm).toFloat() }
    }

    fun encode(vector: FloatArray): ByteArray = ByteBuffer.allocate(vector.size * 4)
        .order(ByteOrder.LITTLE_ENDIAN).apply { vector.forEach { putFloat(it) } }.array()

    fun decode(bytes: ByteArray?, dimension: Int): FloatArray? {
        if (bytes == null || dimension <= 0 || bytes.size.toLong() != dimension.toLong() * 4) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val vector = FloatArray(dimension) { buffer.float }
        if (vector.any { !it.isFinite() }) return null
        val squaredNorm = vector.sumOf { it.toDouble() * it }
        return vector.takeIf { kotlin.math.abs(squaredNorm - 1.0) < 0.001 }
    }

    fun dot(left: FloatArray, right: FloatArray): Float {
        require(left.size == right.size)
        var score = 0.0
        left.indices.forEach { score += left[it].toDouble() * right[it] }
        return score.toFloat()
    }
}

/** Bounded heap: lower scores and larger IDs lose first. */
class ClipTopK<T>(private val limit: Int = 30) {
    private data class Hit<T>(val id: Long, val score: Float, val value: T)
    private val order = compareBy<Hit<T>> { it.score }.thenByDescending { it.id }
    private val heap = PriorityQueue(order)

    fun add(id: Long, score: Float, value: T) {
        if (!score.isFinite() || limit <= 0) return
        heap.add(Hit(id, score, value))
        if (heap.size > limit) heap.poll()
    }

    fun results(): List<T> = heap.sortedWith(order.reversed()).map { it.value }
}
