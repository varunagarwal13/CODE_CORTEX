package com.vocis.vcd.fusion

import com.vocis.vcd.domain.VcdConstants

/**
 * Manages per-contact baseline calibration for anti-spoof detection.
 *
 * CRITICAL SECURITY INVARIANT:
 * Baselines are NEVER learned from live call audio (which may be attacker-controlled).
 * Authentic baseline is loaded exclusively from enrolled voiceprints measured at enrollment.
 * Unenrolled calls use the fixed standard synthetic threshold without dynamic adjustment.
 */
class BaselineCalibrator(
    private val baselineWindowsCount: Int = VcdConstants.BASELINE_WINDOWS_COUNT,
    private val offset: Float = VcdConstants.BASELINE_OFFSET,
    private val minThreshold: Float = VcdConstants.MIN_DECISION_THRESHOLD,
    private val saturationLimit: Float = VcdConstants.SATURATION_THRESHOLD_LIMIT
) {

    var baselineSynthetic: Float = 0.0f
        private set
    var dynamicThreshold: Float = minThreshold
        private set
    var isCalibrated: Boolean = false
        private set
    var isReliable: Boolean = true
        private set

    /**
     * Initializes calibration state using the enrolled contact's authentic baseline.
     * If [enrolledBaseline] is null or <= 0.0, falls back to fixed standard threshold.
     */
    fun configureEnrolledBaseline(enrolledBaseline: Float?) {
        if (enrolledBaseline != null && enrolledBaseline > 0.0f) {
            baselineSynthetic = enrolledBaseline
            val calculated = maxOf(minThreshold, enrolledBaseline + offset)
            dynamicThreshold = calculated
            isCalibrated = true
            isReliable = (enrolledBaseline + offset < saturationLimit)
        } else {
            baselineSynthetic = 0.0f
            dynamicThreshold = minThreshold
            isCalibrated = false
            isReliable = true
        }
    }

    /**
     * Ingests a synthetic probability score from an audio window.
     * NOTE: To prevent in-call voice clone inversion attacks, live call audio
     * NEVER updates or recalculates the baseline!
     */
    fun ingestScore(syntheticProbability: Float): CalibrationStatus {
        return getCalibrationStatus()
    }

    fun getCalibrationStatus(): CalibrationStatus {
        return CalibrationStatus(
            isCalibrated = isCalibrated,
            isReliable = isReliable,
            baselineSynthetic = baselineSynthetic,
            dynamicThreshold = dynamicThreshold
        )
    }

    /**
     * Resets calibration state for a new call session.
     */
    fun reset(enrolledBaseline: Float? = null) {
        configureEnrolledBaseline(enrolledBaseline)
    }
}

data class CalibrationStatus(
    val isCalibrated: Boolean,
    val isReliable: Boolean,
    val baselineSynthetic: Float,
    val dynamicThreshold: Float
)
