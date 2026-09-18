package com.vocis.vcd.fusion

import com.vocis.vcd.domain.VcdVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FusionAndCalibrationTest {

    @Test
    fun testBaselineCalibrationAndDynamicThreshold() {
        val calibrator = BaselineCalibrator(baselineWindowsCount = 3)

        // Window 1: 0.10
        var status = calibrator.ingestScore(0.10f)
        assertFalse(status.isCalibrated)
        // Baseline = 0.10, threshold = max(0.50, 0.10 + 0.15) = 0.50
        assertEquals(0.50f, status.dynamicThreshold, 1e-5f)

        // Window 2: 0.20
        status = calibrator.ingestScore(0.20f)
        assertFalse(status.isCalibrated)

        // Window 3: 0.30
        status = calibrator.ingestScore(0.30f)
        assertTrue(status.isCalibrated)
        assertTrue(status.isReliable)
        // Average baseline = (0.10 + 0.20 + 0.30) / 3 = 0.20
        assertEquals(0.20f, status.baselineSynthetic, 1e-5f)
        // Dynamic threshold = max(0.50, 0.20 + 0.15) = 0.50
        assertEquals(0.50f, status.dynamicThreshold, 1e-5f)

        // Reset
        calibrator.reset()
        assertFalse(calibrator.isCalibrated)
    }

    @Test
    fun testBaselineSaturationTriggersUnreliable() {
        val calibrator = BaselineCalibrator(baselineWindowsCount = 3)

        // Severe line noise where baseline averages 0.90
        calibrator.ingestScore(0.90f)
        calibrator.ingestScore(0.90f)
        val status = calibrator.ingestScore(0.90f)

        // Dynamic threshold = 0.90 + 0.15 = 1.05 >= 1.0 -> Unreliable!
        assertTrue(status.isCalibrated)
        assertFalse(status.isReliable)
    }

    @Test
    fun testSessionScoresMedianFilter() {
        val sessionScores = SessionScores(windowSize = 5)

        // Push 5 values with one extreme transient acoustic spike
        sessionScores.push(similarity = 0.80f, syntheticProbability = 0.10f)
        sessionScores.push(similarity = 0.82f, syntheticProbability = 0.12f)
        sessionScores.push(similarity = 0.81f, syntheticProbability = 0.95f) // Transient glitch!
        sessionScores.push(similarity = 0.83f, syntheticProbability = 0.11f)
        sessionScores.push(similarity = 0.79f, syntheticProbability = 0.13f)

        // Sorted similarities: [0.79, 0.80, 0.81, 0.82, 0.83] -> Median = 0.81
        assertEquals(0.81f, sessionScores.medianSimilarity(), 1e-5f)

        // Sorted synthetics: [0.10, 0.11, 0.12, 0.13, 0.95] -> Median = 0.12 (Glitch eliminated!)
        assertEquals(0.12f, sessionScores.medianSynthetic(), 1e-5f)
    }

    @Test
    fun testBiometricFusion2DDecisionMatrix() {
        val engine = BiometricFusionEngine()
        val dynamicThreshold = 0.60f
        val baseline = 0.20f

        // 1. High Similarity (0.85 >= 0.75) + High Synthetic (0.75 >= 0.60) -> CRITICAL CLONE
        val cloneResult = engine.evaluate(
            similarity = 0.85f,
            syntheticProbability = 0.75f,
            dynamicThreshold = dynamicThreshold,
            baselineSynthetic = baseline,
            isReliable = true
        )
        assertEquals(VcdVerdict.CRITICAL_CLONE_DETECTED, cloneResult.verdict)

        // 2. High Similarity (0.85 >= 0.75) + Low Synthetic (0.15 < 0.60) -> SAFE AUTHENTIC
        val safeResult = engine.evaluate(
            similarity = 0.85f,
            syntheticProbability = 0.15f,
            dynamicThreshold = dynamicThreshold,
            baselineSynthetic = baseline,
            isReliable = true
        )
        assertEquals(VcdVerdict.SAFE_VERIFIED_AUTHENTIC, safeResult.verdict)

        // 3. Low Similarity (0.30 < 0.50) + Low Synthetic (0.15 < 0.60) -> SUSPICIOUS IMPOSTOR
        val impostorResult = engine.evaluate(
            similarity = 0.30f,
            syntheticProbability = 0.15f,
            dynamicThreshold = dynamicThreshold,
            baselineSynthetic = baseline,
            isReliable = true
        )
        assertEquals(VcdVerdict.SUSPICIOUS_IMPOSTOR, impostorResult.verdict)

        // 4. Low Similarity (0.30 < 0.50) + High Synthetic (0.75 >= 0.60) -> UNKNOWN SYNTHETIC
        val unknownSynthResult = engine.evaluate(
            similarity = 0.30f,
            syntheticProbability = 0.75f,
            dynamicThreshold = dynamicThreshold,
            baselineSynthetic = baseline,
            isReliable = true
        )
        assertEquals(VcdVerdict.CRITICAL_UNKNOWN_SYNTHETIC, unknownSynthResult.verdict)

        // 5. Intermediate Similarity (0.60) -> UNCERTAIN
        val uncertainResult = engine.evaluate(
            similarity = 0.60f,
            syntheticProbability = 0.15f,
            dynamicThreshold = dynamicThreshold,
            baselineSynthetic = baseline,
            isReliable = true
        )
        assertEquals(VcdVerdict.UNCERTAIN, uncertainResult.verdict)

        // 6. Unreliable carrier line noise + borderline similarity -> UNRELIABLE_LINE_SATURATED
        val unreliableResult = engine.evaluate(
            similarity = 0.60f,
            syntheticProbability = 0.75f,
            dynamicThreshold = 1.05f,
            baselineSynthetic = 0.90f,
            isReliable = false
        )
        assertEquals(VcdVerdict.UNRELIABLE_LINE_SATURATED, unreliableResult.verdict)
    }
}
