package com.vocis.speech.llm

import kotlinx.serialization.Serializable

@Serializable
enum class ScamCategory {
    NONE,
    DIGITAL_ARREST,
    OTP_THEFT,
    BANKING_IMPERSONATION,
    REMOTE_ACCESS,
    COERCIVE_AUTHORITY,
    COURIER_FRAUD
}

@Serializable
enum class UrgencyLevel {
    LOW,
    MEDIUM,
    HIGH,
    EXTREME
}

@Serializable
data class SemanticAnalysisResult(
    val isScam: Boolean,
    val scamCategory: ScamCategory,
    val urgencyLevel: UrgencyLevel,
    val coercionTactics: List<String> = emptyList(),
    val confidence: Float,
    val rawExplanation: String? = null,
    val isFromFallback: Boolean = false,
    /** BCP-47 language code detected by Whisper (e.g. "en", "hi", "ta"). Null when ASR is offline/Vosk. */
    val detectedLanguage: String? = null,
    /**
     * False when AASIST output should be treated as unreliable.
     * AASIST was trained on English ASVspoof-2019 data only; non-English speech,
     * heavily codec-compressed audio (Opus/OGG at low bitrates), or non-speech
     * audio can produce extreme false-positive spoof scores.
     * When false, BiometricFusionEngine ignores the synthetic score and falls
     * back to the LLM-only verdict.
     */
    val isAasistReliable: Boolean = true
)
