package com.vocis.vcd.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MathPrimitivesTest {

    @Test
    fun testL2Norm() {
        val vector = floatArrayOf(3.0f, 4.0f)
        assertEquals(5.0f, MathPrimitives.l2Norm(vector), 1e-5f)
    }

    @Test
    fun testL2Normalize() {
        val vector = floatArrayOf(3.0f, 4.0f)
        val normalized = MathPrimitives.l2Normalize(vector)
        assertEquals(0.6f, normalized[0], 1e-5f)
        assertEquals(0.8f, normalized[1], 1e-5f)
        assertEquals(1.0f, MathPrimitives.l2Norm(normalized), 1e-5f)
    }

    @Test
    fun testCosineSimilarity() {
        val a = floatArrayOf(1.0f, 0.0f, 0.0f)
        val b = floatArrayOf(1.0f, 0.0f, 0.0f)
        val c = floatArrayOf(0.0f, 1.0f, 0.0f)
        val d = floatArrayOf(-1.0f, 0.0f, 0.0f)

        // Identical vectors -> 1.0
        assertEquals(1.0f, MathPrimitives.cosineSimilarity(a, b), 1e-5f)
        // Orthogonal vectors -> 0.0
        assertEquals(0.0f, MathPrimitives.cosineSimilarity(a, c), 1e-5f)
        // Opposite vectors -> -1.0
        assertEquals(-1.0f, MathPrimitives.cosineSimilarity(a, d), 1e-5f)
    }

    @Test
    fun testCalculateNormalizedCentroid() {
        val v1 = floatArrayOf(1.0f, 0.0f)
        val v2 = floatArrayOf(0.0f, 1.0f)
        val centroid = MathPrimitives.calculateNormalizedCentroid(listOf(v1, v2))

        assertEquals(1.0f, MathPrimitives.l2Norm(centroid), 1e-5f)
        assertEquals(centroid[0], centroid[1], 1e-5f)
    }

    @Test
    fun testRmsSilence() {
        val silence = FloatArray(1000) { 0.001f }
        val active = FloatArray(1000) { 0.5f }

        assertTrue(MathPrimitives.calculateRms(silence) < 0.01f)
        assertTrue(MathPrimitives.calculateRms(active) > 0.01f)
    }

    @Test
    fun testSoftmax2() {
        val logits = floatArrayOf(0.0f, 0.0f)
        val probs = MathPrimitives.softmax2(logits)
        assertEquals(0.5f, probs[0], 1e-5f)
        assertEquals(0.5f, probs[1], 1e-5f)

        val asymmetric = floatArrayOf(-10.0f, 10.0f)
        val asymmetricProbs = MathPrimitives.softmax2(asymmetric)
        assertTrue(asymmetricProbs[1] > 0.999f)
    }
}
