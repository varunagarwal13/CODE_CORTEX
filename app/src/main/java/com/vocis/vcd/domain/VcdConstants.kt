package com.vocis.vcd.domain

/**
 * Constants governing the Voice Clone Defence (VCD) subsystem.
 * Strictly aligned with VOCIS_MASTER_PROJECT_SPEC and ROLE C requirements.
 */
object VcdConstants {
    /** Target audio sample rate in Hz */
    const val SAMPLE_RATE = 16000

    /** Slicing window size: 64,600 samples (~4.0375 seconds at 16kHz) */
    const val WINDOW_SAMPLES = 64600

    /** Slicing hop size: 48,000 samples (3.0 seconds at 16kHz) */
    const val HOP_SAMPLES = 48000

    /** Speaker embedding dimension produced by Resemblyzer GE2E */
    const val EMBEDDING_DIM = 256

    /** Minimum RMS energy below which audio window is considered silence */
    const val RMS_SILENCE_THRESHOLD = 0.005f

    /** Pairwise similarity threshold for voice enrollment validation */
    const val PAIRWISE_SIMILARITY_THRESHOLD = 0.75f

    /** Number of initial windows to calibrate carrier line noise baseline */
    const val BASELINE_WINDOWS_COUNT = 3

    /** Number of historical window scores used in sliding median filter */
    const val MEDIAN_FILTER_WINDOW_SIZE = 5

    /** Dynamic threshold offset: threshold = max(0.50, baseline + 0.15) */
    const val BASELINE_OFFSET = 0.15f

    /** Minimum decision threshold for synthetic detection */
    const val MIN_DECISION_THRESHOLD = 0.50f

    /** Maximum decision threshold before line is declared saturated/unreliable */
    const val SATURATION_THRESHOLD_LIMIT = 1.00f

    /** Speaker similarity threshold for identifying enrolled contact */
    const val SPEAKER_MATCH_THRESHOLD = 0.75f

    /** Speaker similarity lower bound below which voice is considered an impostor */
    const val SPEAKER_MISMATCH_THRESHOLD = 0.60f
}
