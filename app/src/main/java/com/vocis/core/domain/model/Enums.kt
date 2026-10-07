package com.vocis.core.domain.model

enum class RiskLevel {
    LOW,
    ELEVATED,
    HIGH,
    CRITICAL;

    companion object {
        fun fromScore(score: Int): RiskLevel = when {
            score >= 75 -> CRITICAL
            score >= 50 -> HIGH
            score >= 25 -> ELEVATED
            else -> LOW
        }
    }
}

enum class EventType {
    INCOMING_CALL,
    OUTGOING_CALL,
    SMS_RECEIVED,
    NOTIFICATION_POSTED,
    PACKAGE_ADDED,
    SYSTEM_EVENT
}

enum class IncidentType {
    DIGITAL_ARREST,
    OTP_THEFT,
    FINANCIAL_FRAUD,
    REMOTE_ACCESS_SCAM,
    VOICE_CLONE,
    OTHER
}

enum class IncidentStatus {
    NEW,
    INVESTIGATING,
    CONFIRMED,
    RESOLVED,
    DISMISSED
}

enum class ProtectionAction {
    MONITOR_ONLY,
    SHOW_COMPACT_WARNING,
    SHOW_RISK_CARD,
    SHOW_SECURITY_INTERVENTION,
    BLOCK_CALL
}

enum class ProtectionMode {
    AGGRESSIVE,
    BALANCED,
    PASSIVE
}
