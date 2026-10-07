package com.vocis.vcd.domain

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Pure mathematical primitives for audio signal processing and biometric vector operations.
 * Stateless and highly optimized for real-time execution.
 */
object MathPrimitives {

    const val TARGET_DBFS = -30f

    /**
     * Loudness normalisation to -30 dBFS, matching Resemblyzer standard.
     * Prevents caller volume from distorting speaker embedding similarities.
     */
    fun toTargetDbfs(
        samples: FloatArray,
        targetDbfs: Float = TARGET_DBFS,
        increaseOnly: Boolean = true
    ): FloatArray {
        if (samples.isEmpty()) return samples

        var acc = 0.0
        for (s in samples) acc += s.toDouble() * s
        val rms = sqrt(acc / samples.size)
        if (rms < 1e-10) return samples

        val dbfs = 20.0 * log10(rms)
        val delta = targetDbfs - dbfs
        if (increaseOnly && delta < 0) return samples

        val gain = 10.0.pow(delta / 20.0).toFloat()
        return FloatArray(samples.size) { samples[it] * gain }
    }

    /**
     * Calculates L2 norm (Euclidean length) of a float vector.
     */
    fun l2Norm(vector: FloatArray): Float {
        var sumSquares = 0.0
        for (v in vector) {
            sumSquares += (v * v)
        }
        return sqrt(sumSquares).toFloat()
    }

    /**
     * Returns an L2-normalized copy of the float vector.
     * If the norm is 0, returns a vector of zeros.
     */
    fun l2Normalize(vector: FloatArray): FloatArray {
        val norm = l2Norm(vector)
        if (norm == 0.0f) {
            return FloatArray(vector.size)
        }
        val normalized = FloatArray(vector.size)
        for (i in vector.indices) {
            normalized[i] = vector[i] / norm
        }
        return normalized
    }

    /**
     * Computes the dot product of two float arrays of equal dimension.
     */
    fun dotProduct(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must have identical dimensions: ${a.size} != ${b.size}" }
        var sum = 0.0f
        for (i in a.indices) {
            sum += a[i] * b[i]
        }
        return sum
    }

    /**
     * Computes cosine similarity between two float vectors: (a · b) / (||a|| * ||b||).
     * Output range clamped to [-1.0, 1.0].
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must have identical dimensions" }
        val normA = l2Norm(a)
        val normB = l2Norm(b)
        if (normA == 0.0f || normB == 0.0f) return 0.0f
        val dot = dotProduct(a, b)
        val similarity = dot / (normA * normB)
        return similarity.coerceIn(-1.0f, 1.0f)
    }

    /**
     * Calculates the element-wise centroid vector of a list of vectors,
     * then applies L2 normalization to produce a unit centroid vector.
     */
    fun calculateNormalizedCentroid(vectors: List<FloatArray>): FloatArray {
        require(vectors.isNotEmpty()) { "Vector list cannot be empty" }
        val dim = vectors[0].size
        for (v in vectors) {
            require(v.size == dim) { "All vectors must have identical dimensions" }
        }

        val sum = FloatArray(dim)
        for (v in vectors) {
            for (i in 0 until dim) {
                sum[i] += v[i]
            }
        }

        val count = vectors.size.toFloat()
        for (i in 0 until dim) {
            sum[i] /= count
        }

        return l2Normalize(sum)
    }

    /**
     * Calculates the Root Mean Square (RMS) energy of an audio frame.
     */
    fun calculateRms(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0.0f
        var sumSquares = 0.0
        for (s in samples) {
            sumSquares += (s * s)
        }
        return sqrt(sumSquares / samples.size).toFloat()
    }

    /**
     * Computes softmax probabilities over a 2-element logit array [z0, z1].
     * Returns [P(0), P(1)].
     */
    fun softmax2(logits: FloatArray): FloatArray {
        require(logits.size == 2) { "Softmax2 requires exactly 2 logits" }
        val maxLogit = maxOf(logits[0], logits[1])
        val exp0 = kotlin.math.exp(logits[0] - maxLogit)
        val exp1 = kotlin.math.exp(logits[1] - maxLogit)
        val sumExp = exp0 + exp1
        return floatArrayOf(exp0 / sumExp, exp1 / sumExp)
    }
}
