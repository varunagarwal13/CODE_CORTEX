package com.vocis

import com.vocis.core.math.VectorMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class VectorMathTest {

    @Test
    fun testOrthogonalVectorsYieldZeroSimilarity() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(0f, 1f, 0f)
        val sim = VectorMath.cosineSimilarity(a, b)
        assertEquals(0f, sim, 1e-6f)
    }

    @Test
    fun testIdenticalVectorsYieldOneSimilarity() {
        val a = floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f)
        val sim = VectorMath.cosineSimilarity(a, a)
        assertEquals(1f, sim, 1e-6f)
    }

    @Test
    fun testOppositeVectorsYieldMinusOneSimilarity() {
        val a = floatArrayOf(1f, 2f, 3f)
        val b = floatArrayOf(-1f, -2f, -3f)
        val sim = VectorMath.cosineSimilarity(a, b)
        assertEquals(-1f, sim, 1e-6f)
    }

    @Test
    fun testL2NormalizationProducesUnitNorm() {
        val v = floatArrayOf(3f, 4f)
        val normalized = VectorMath.l2Normalize(v)
        val norm = VectorMath.l2Norm(normalized)
        assertEquals(1f, norm, 1e-6f)
        assertEquals(0.6f, normalized[0], 1e-6f)
        assertEquals(0.8f, normalized[1], 1e-6f)
    }

    @Test
    fun testZeroVectorHandlesGracefully() {
        val zero = floatArrayOf(0f, 0f, 0f)
        val norm = VectorMath.l2Norm(zero)
        assertEquals(0f, norm, 1e-6f)

        val normalized = VectorMath.l2Normalize(zero)
        assertTrue(normalized.all { it == 0f })

        val sim = VectorMath.cosineSimilarity(zero, floatArrayOf(1f, 1f, 1f))
        assertEquals(0f, sim, 1e-6f)
    }
}
