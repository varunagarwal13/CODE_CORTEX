package com.vocis.intelligence.policy

import com.vocis.core.data.dao.ProtectionPolicyDao
import com.vocis.core.data.dao.TrustedCallerDao
import com.vocis.core.data.entity.ProtectionPolicyEntity
import com.vocis.core.data.entity.SecurityIncidentEntity
import com.vocis.core.domain.model.ProtectionAction
import com.vocis.core.domain.model.ProtectionMode
import com.vocis.core.domain.model.RiskLevel
import com.vocis.intelligence.identity.CallerIdentity
import com.vocis.intelligence.identity.ReputationLevel
import com.vocis.intelligence.risk.RiskAssessment

data class CallScreeningDecision(
    val action: ProtectionAction,
    val disallowCall: Boolean,
    val rejectCall: Boolean,
    val silenceCall: Boolean,
    val skipCallLog: Boolean
)

class ProtectionPolicyEngine(
    private val dao: ProtectionPolicyDao? = null,
    private val trustedCallerDao: TrustedCallerDao? = null
) {
    fun defaultPolicy(): ProtectionPolicyEntity {
        return ProtectionPolicyEntity(
            policyId = "default_policy",
            mode = ProtectionMode.BALANCED,
            lowRiskBehavior = ProtectionAction.MONITOR_ONLY,
            elevatedRiskBehavior = ProtectionAction.SHOW_COMPACT_WARNING,
            highRiskBehavior = ProtectionAction.SHOW_RISK_CARD,
            criticalRiskBehavior = ProtectionAction.BLOCK_CALL,
            unknownCallerBehavior = ProtectionAction.MONITOR_ONLY,
            autoBlockCritical = true
        )
    }

    suspend fun evaluateInteraction(
        phoneNumber: String,
        riskAssessment: RiskAssessment,
        activeIncident: SecurityIncidentEntity? = null,
        callerIdentity: CallerIdentity? = null,
        policyOverride: ProtectionPolicyEntity? = null
    ): ProtectionAction {
        // ponytail: known-contact bypass removed — spoofed caller ID, compromised phones,
        // and voice clone attacks all target saved contacts. The identity base weight is
        // already 0 for known contacts, so normal calls still score LOW; but OTP/context/
        // linguistic/VCD factors must still accumulate.

        // 0. Explicit Trusted Callers whitelist takes highest precedence
        if (trustedCallerDao?.isTrusted(phoneNumber) == true) {
            return ProtectionAction.MONITOR_ONLY
        }

        // 1. Active incident check: if there is an active high/critical incident, escalate to BLOCK_CALL
        if (activeIncident != null && (activeIncident.severity == RiskLevel.HIGH || activeIncident.severity == RiskLevel.CRITICAL)) {
            return ProtectionAction.BLOCK_CALL
        }

        val resolvedIdentity = callerIdentity ?: CallerIdentity(
            phoneNumber = phoneNumber,
            displayName = null,
            reputationLevel = if (phoneNumber.isBlank() || phoneNumber == "UNKNOWN") ReputationLevel.UNKNOWN else ReputationLevel.NEUTRAL,
            isKnownContact = false,
            source = "INTERACTION"
        )

        return decide(
            riskScore = riskAssessment.score,
            riskLevel = riskAssessment.level,
            callerIdentity = resolvedIdentity,
            policyOverride = policyOverride
        )
    }

    suspend fun decide(
        riskScore: Int,
        riskLevel: RiskLevel,
        callerIdentity: CallerIdentity,
        policyOverride: ProtectionPolicyEntity? = null
    ): ProtectionAction {
        // Whitelist verified safe contacts
        if (callerIdentity.isKnownContact && callerIdentity.reputationLevel == ReputationLevel.SAFE) {
            return ProtectionAction.MONITOR_ONLY
        }

        val policy = policyOverride ?: dao?.getPolicy() ?: defaultPolicy()

        // 2. Critical override rule: autoBlockCritical forces BLOCK_CALL regardless of mode
        if (policy.autoBlockCritical && riskLevel == RiskLevel.CRITICAL) {
            return ProtectionAction.BLOCK_CALL
        }

        // 3. Mode and Risk-level specific behavior
        return when (riskLevel) {
            RiskLevel.LOW -> policy.lowRiskBehavior
            RiskLevel.ELEVATED -> policy.elevatedRiskBehavior
            RiskLevel.HIGH -> policy.highRiskBehavior
            RiskLevel.CRITICAL -> policy.criticalRiskBehavior
        }
    }

    fun toScreeningDecision(action: ProtectionAction): CallScreeningDecision {
        return when (action) {
            ProtectionAction.BLOCK_CALL -> CallScreeningDecision(
                action = action,
                disallowCall = true,
                rejectCall = true,
                silenceCall = false,
                skipCallLog = true
            )
            ProtectionAction.SHOW_SECURITY_INTERVENTION,
            ProtectionAction.SHOW_RISK_CARD -> CallScreeningDecision(
                action = action,
                disallowCall = false,
                rejectCall = false,
                silenceCall = true,
                skipCallLog = false
            )
            ProtectionAction.SHOW_COMPACT_WARNING,
            ProtectionAction.MONITOR_ONLY -> CallScreeningDecision(
                action = action,
                disallowCall = false,
                rejectCall = false,
                silenceCall = false,
                skipCallLog = false
            )
        }
    }
}
