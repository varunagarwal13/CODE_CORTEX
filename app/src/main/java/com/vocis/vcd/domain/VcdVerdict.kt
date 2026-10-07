package com.vocis.vcd.domain

/**
 * Biometric decision classifications output by BiometricFusionEngine.
 */
enum class VcdVerdict {
    /**
     * Speaker matches enrolled contact AND speech exhibits natural acoustic properties.
     */
    SAFE_VERIFIED_AUTHENTIC,

    /**
     * Speech is authentic human voice, but similarity against enrolled contact is low (<0.50).
     */
    SUSPICIOUS_IMPOSTOR,

    /**
     * High similarity to enrolled contact, but synthetic probability exceeds dynamic threshold.
     * High confidence AI Voice Clone attack.
     */
    CRITICAL_CLONE_DETECTED,

    /**
     * Synthetic speech detected from an unknown or mismatched speaker profile.
     */
    CRITICAL_UNKNOWN_SYNTHETIC,

    /**
     * Similarity is intermediate (0.50 - 0.75) or score stability is pending.
     */
    UNCERTAIN,

    /**
     * Background carrier line noise or acoustic saturation resulted in dynamic threshold >= 1.0.
     * Anti-spoof inference is flagged UNRELIABLE to prevent false clone alarms.
     */
    UNRELIABLE_LINE_SATURATED
}

/**
 * Encapsulates the output of a single live verification cycle.
 */
data class VcdVerificationResult(
    val verdict: VcdVerdict,
    val similarity: Float,
    val syntheticProbability: Float,
    val dynamicThreshold: Float,
    val baselineSynthetic: Float,
    val isReliable: Boolean,
    val timestampMs: Long = System.currentTimeMillis()
)
