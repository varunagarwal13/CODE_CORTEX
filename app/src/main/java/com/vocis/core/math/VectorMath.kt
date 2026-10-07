package com.vocis.core.math

import kotlin.math.sqrt

object VectorMath {

    private const val EPSILON: Float = 1e-12f

    fun dotProduct(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must have identical dimensions (${a.size} != ${b.size})" }
        var sum = 0f
        for (i in a.indices) {
            sum += a[i] * b[i]
        }
        return sum
    }

    fun l2Norm(vector: FloatArray): Float {
        var sum = 0f
        for (v in vector) {
            sum += v * v
        }
        return sqrt(sum)
    }

    fun l2Normalize(vector: FloatArray): FloatArray {
        val norm = l2Norm(vector)
        if (norm < EPSILON) {
            return FloatArray(vector.size)
        }
        val result = FloatArray(vector.size)
        for (i in vector.indices) {
            result[i] = vector[i] / norm
        }
        return result
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must have identical dimensions (${a.size} != ${b.size})" }
        val normA = l2Norm(a)
        val normB = l2Norm(b)
        if (normA < EPSILON || normB < EPSILON) {
            return 0f
        }
        val similarity = dotProduct(a, b) / (normA * normB)
        return similarity.coerceIn(-1f, 1f)
    }
}
