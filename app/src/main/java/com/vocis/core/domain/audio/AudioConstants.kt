package com.vocis.core.domain.audio

object AudioConstants {
    const val SAMPLE_RATE: Int = 16000
    const val WINDOW_SIZE: Int = 64600 // ~4.0375s at 16kHz for AASIST anti-spoofing
    const val HOP_SIZE_DEFAULT: Int = 48000 // 3.0s hop
    const val HOP_SIZE_HIGH_RISK: Int = 32300 // ~2.01875s hop
    const val PARTIAL_WINDOW_SIZE: Int = 25600 // 1.6s for speaker encoder
    const val EMBEDDING_DIM: Int = 256
}
