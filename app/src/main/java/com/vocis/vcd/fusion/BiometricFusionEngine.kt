package com.vocis.vcd.fusion

import com.vocis.vcd.domain.VcdConstants
import com.vocis.vcd.domain.VcdVerdict
import com.vocis.vcd.domain.VcdVerificationResult

/**
 * Executes the dual-score 2D decision matrix matching VOCIS Biometric Fusion.
 *
 * CRITICAL ARCHITECTURAL INVARIANT:
 * Speaker similarity and synthetic probability are NEVER arithmetically averaged.
 * High similarity + High synthetic probability = CRITICAL Clone Signature.
 *
 * CALIBRATION & SATURATION SUPPRESSION (VOCIS Fusion Engine (Adaptive Calibration)):
 * If the speaker's own authentic voice or microphone chain reads near the ceiling
 * (baselineSynthetic + margin >= 1.0), the anti-spoof model cannot distinguish
 * them from a clone. In this state (isReliable = false / UNRELIABLE), the spoof
 * check carries no information. To prevent false accusations against authentic
 * humans, the spoof score is dropped. If identity matches (similarity >= matchThreshold),
 * the verdict is SAFE_VERIFIED_AUTHENTIC.
 *
 * AASIST RELIABILITY GATE:
 * AASIST was trained exclusively on English ASVspoof-2019 clean studio data. When
 * non-English audio or out-of-distribution codec compression is detected, synthetic
 * logits are untrustworthy. When [isAasistReliable] = false, synthetic scores are
 * suppressed and decisions rely on speaker similarity and semantic intelligence.
 */
class BiometricFusionEngine(
    val speakerMatchThreshold: Float = VcdConstants.SPEAKER_MATCH_THRESHOLD,
    val speakerMismatchThreshold: Float = VcdConstants.SPEAKER_MISMATCH_THRESHOLD
) {

    /**
     * Evaluates live scores against dynamic calibration and outputs a categorical verdict.
     *
     * @param similarity           Cosine similarity to enrolled contact voiceprint [0.0, 1.0].
     * @param syntheticProbability Synthetic speech probability from AASIST [0.0, 1.0].
     * @param dynamicThreshold     Line-noise calibrated threshold from BaselineCalibrator.
     * @param baselineSynthetic    Baseline AASIST score measured on enrolled clip.
     * @param isReliable           False when baseline saturation (baseline + margin >= 1.0) occurs.
     * @param isAasistReliable     False when non-English speech or severe compression renders AASIST noisy.
     */
    fun evaluate(
        similarity: Float,
        syntheticProbability: Float,
        dynamicThreshold: Float,
        baselineSynthetic: Float,
        isReliable: Boolean,
        isAasistReliable: Boolean = true
    ): VcdVerificationResult {

        val isHighSimilarity = similarity >= speakerMatchThreshold
        val isLowSimilarity  = similarity < speakerMismatchThreshold
        val isHighSynthetic  = syntheticProbability >= dynamicThreshold

        // ── 1. Critical Clone Detection & Synthetic Spoof Checks ──────────────────────
        // High similarity + High synthetic probability -> Verified voice clone attack!
        // CRITICAL INVARIANT: High speaker similarity must NEVER override strong synthetic evidence.
        if (isHighSimilarity && isHighSynthetic) {
            return VcdVerificationResult(
                verdict = VcdVerdict.CRITICAL_CLONE_DETECTED,
                similarity = similarity,
                syntheticProbability = syntheticProbability,
                dynamicThreshold = dynamicThreshold,
                baselineSynthetic = baselineSynthetic,
                isReliable = isReliable && isAasistReliable
            )
        }

        // High synthetic probability from unknown or mismatched speaker -> AI generated voice
        if (isHighSynthetic) {
            return VcdVerificationResult(
                verdict = VcdVerdict.CRITICAL_UNKNOWN_SYNTHETIC,
                similarity = similarity,
                syntheticProbability = syntheticProbability,
                dynamicThreshold = dynamicThreshold,
                baselineSynthetic = baselineSynthetic,
                isReliable = isReliable && isAasistReliable
            )
        }

        // ── 2. Saturation / Calibration Unreliable Gate ──────────────────────────────
        // If anti-spoof model is unreliable (e.g. baseline measured near ceiling at enrollment,
        // or non-English speech) AND synthetic evidence is NOT high:
        if (!isReliable || !isAasistReliable) {
            val verdict = when {
                // Identity matches authentic enrolled voice -> SAFE
                isHighSimilarity -> VcdVerdict.SAFE_VERIFIED_AUTHENTIC
                // Mismatch on identity -> SUSPICIOUS IMPOSTOR
                isLowSimilarity  -> VcdVerdict.SUSPICIOUS_IMPOSTOR
                // Unenrolled or borderline -> UNRELIABLE_LINE_SATURATED / UNCERTAIN
                else             -> if (!isReliable) VcdVerdict.UNRELIABLE_LINE_SATURATED else VcdVerdict.UNCERTAIN
            }
            return VcdVerificationResult(
                verdict = verdict,
                similarity = similarity,
                syntheticProbability = syntheticProbability,
                dynamicThreshold = dynamicThreshold,
                baselineSynthetic = baselineSynthetic,
                isReliable = isReliable && isAasistReliable
            )
        }

        // ── 3. Normal path: AASIST score is calibrated and reliable ──────────────────
        val verdict = when {
            // High similarity + Low synthetic probability -> Genuine trusted contact
            isHighSimilarity && !isHighSynthetic -> VcdVerdict.SAFE_VERIFIED_AUTHENTIC

            // Low similarity + Low synthetic probability -> Wrong person, but natural human
            isLowSimilarity && !isHighSynthetic  -> VcdVerdict.SUSPICIOUS_IMPOSTOR

            // Intermediate similarity with natural human voice
            else                                 -> VcdVerdict.UNCERTAIN
        }

        return VcdVerificationResult(
            verdict = verdict,
            similarity = similarity,
            syntheticProbability = syntheticProbability,
            dynamicThreshold = dynamicThreshold,
            baselineSynthetic = baselineSynthetic,
            isReliable = true
        )
    }
}
