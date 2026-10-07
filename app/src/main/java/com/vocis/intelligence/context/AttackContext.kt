package com.vocis.intelligence.context

/**
 * Composite attack context holding multi-modal cross-channel threat signals.
 * Preserves full backward-compatibility with VOCIS database and Room entities while
 * mapping the complete VOCIS 5-minute temporal attack correlation specification.
 */
data class AttackContext(
    val hasActiveOtp: Boolean = false,
    val otpWithinWindow: Boolean = false,
    val remoteDesktopActive: Boolean = false,
    val phishingLinkDetected: Boolean = false,
    val activeCallPhoneNumber: String? = null,
    val windowStartMs: Long = 0L,

    // VOCIS Context Correlation Suite
    val contextType: ContextType = ContextType.UNKNOWN,
    val inferredIntent: InferredIntent = InferredIntent.UNKNOWN,
    val detectedPatterns: List<String> = emptyList(),
    val explanation: String = "",
    val hasCallbackMismatch: Boolean = false,
    val callbackNumber: String? = null,
    val hasAuthorityThreat: Boolean = false,
    val hasDeliveryThreat: Boolean = false,
    val hasUtilityThreat: Boolean = false,
    val hasTelecomThreat: Boolean = false,
    val hasFinancialThreat: Boolean = false,
    val contributingSignals: List<RiskSignal> = emptyList(),
    val compositeRiskWeight: Int = 0
)
