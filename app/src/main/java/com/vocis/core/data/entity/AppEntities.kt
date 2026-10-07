package com.vocis.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.vocis.core.domain.model.EventType
import com.vocis.core.domain.model.IncidentStatus
import com.vocis.core.domain.model.IncidentType
import com.vocis.core.domain.model.ProtectionAction
import com.vocis.core.domain.model.ProtectionMode
import com.vocis.core.domain.model.RiskLevel

@Entity(
    tableName = "interactions",
    indices = [
        Index(value = ["timestampMs"]),
        Index(value = ["callerPhoneNumber"]),
        Index(value = ["riskLevel"])
    ]
)
data class InteractionEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val timestamp: String,
    val timestampMs: Long,
    val riskLevel: RiskLevel,
    val summary: String,
    val associatedKey: String = "",
    val appName: String = "",
    val notificationTitle: String = "",
    val notificationText: String = "",
    val packageName: String = "",
    val callerPhoneNumber: String = "",
    val callerDisplayName: String = "",
    val callerIdentityType: String = "",
    val callerIdentityConfidence: String = "",
    val callerIdentitySource: String = "",
    val repCategory: String = "",
    val repLevel: String = "",
    val repSpamReports: Int = 0,
    val repFraudReports: Int = 0,
    val groqIsScam: Boolean = false,
    val groqScamScore: Int = 0,
    val groqScamCategory: String = "",
    val groqPrimaryIntent: String = "",
    val groqUrgencyTactics: String = "",
    val groqAnalysisRationale: String = "",
    val protectionDecision: ProtectionAction? = null,
    val incidentType: IncidentType? = null,
    val isBlocked: Boolean = false
)

@Entity(
    tableName = "security_events",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["type"]),
        Index(value = ["interactionId"])
    ]
)
data class SecurityEventEntity(
    @PrimaryKey
    val id: String,
    val type: EventType,
    val source: String,
    val timestamp: Long,
    val identity: String,
    val metadata: String,
    val initialRisk: String,
    val interactionId: String? = null
)

@Entity(
    tableName = "security_incidents",
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["status"]),
        Index(value = ["incidentType"])
    ]
)
data class SecurityIncidentEntity(
    @PrimaryKey
    val incidentId: String,
    val incidentType: IncidentType,
    val severity: RiskLevel,
    val status: IncidentStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val resolvedAt: Long? = null,
    val riskScore: Int,
    val explanation: String,
    val recommendedActions: String,
    val relatedInteractionIds: String = "",
    val callerPhoneNumber: String? = null,
    val callerDisplayName: String? = null,
    val callerIdentityType: String? = null,
    val callerIdentityConfidence: String? = null,
    val callerIdentitySource: String? = null,
    val repCategory: String? = null,
    val repLevel: String? = null,
    val repSpamReports: Int = 0,
    val repFraudReports: Int = 0
)

@Entity(
    tableName = "caller_identities",
    indices = [Index(value = ["category"])]
)
data class CallerIdentityEntity(
    @PrimaryKey
    val phoneNumber: String,
    val displayName: String? = null,
    val category: String = "UNKNOWN",
    val reputationLevel: String = "NEUTRAL",
    val spamReports: Int = 0,
    val fraudReports: Int = 0,
    val isVerified: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "protection_policies")
data class ProtectionPolicyEntity(
    @PrimaryKey
    val policyId: String = "default_policy",
    val mode: ProtectionMode = ProtectionMode.BALANCED,
    val lowRiskBehavior: ProtectionAction = ProtectionAction.MONITOR_ONLY,
    val elevatedRiskBehavior: ProtectionAction = ProtectionAction.SHOW_COMPACT_WARNING,
    val highRiskBehavior: ProtectionAction = ProtectionAction.SHOW_RISK_CARD,
    val criticalRiskBehavior: ProtectionAction = ProtectionAction.BLOCK_CALL,
    val unknownCallerBehavior: ProtectionAction = ProtectionAction.MONITOR_ONLY,
    val autoBlockCritical: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "attack_contexts",
    indices = [Index(value = ["isActive"]), Index(value = ["expiresAt"])]
)
data class AttackContextEntity(
    @PrimaryKey
    val contextId: String,
    val patternType: IncidentType,
    val triggeredAt: Long,
    val expiresAt: Long,
    val primaryInteractionId: String,
    val contributingEventIds: String,
    val isActive: Boolean = true,
    val description: String = ""
)

@Entity(
    tableName = "audit_logs",
    indices = [Index(value = ["timestamp"])]
)
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true)
    val logId: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val action: String,
    val target: String,
    val actor: String = "SYSTEM",
    val details: String = "",
    val success: Boolean = true
)

@Entity(tableName = "family_contacts")
data class FamilyContactEntity(
    @PrimaryKey
    val phoneNumber: String,
    val name: String,
    val relationship: String,
    val addedAt: Long = System.currentTimeMillis(),
    val isEmergencyAlertEnabled: Boolean = true
)

@Entity(tableName = "threat_rules")
data class ThreatRuleEntity(
    @PrimaryKey
    val ruleId: String,
    val name: String,
    val regexPattern: String,
    val threatCategory: String,
    val weight: Int,
    val isEnabled: Boolean = true
)

@Entity(tableName = "trusted_callers")
data class TrustedCallerEntity(
    @PrimaryKey
    val phoneNumber: String,
    val name: String,
    val addedAt: Long = System.currentTimeMillis()
)

