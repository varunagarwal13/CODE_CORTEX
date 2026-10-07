package com.vocis.intelligence.risk

import com.vocis.core.domain.model.RiskLevel
import com.vocis.intelligence.context.AttackContext
import com.vocis.intelligence.context.ContextType
import com.vocis.intelligence.identity.CallerIdentity
import com.vocis.intelligence.identity.ReputationLevel
import com.vocis.intelligence.identity.baseRiskWeight
import com.vocis.intelligence.linguistic.ScamClassification
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

data class EvidenceFactor(
    val source: String,
    val weight: Int,
    val description: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class RiskAssessment(
    val score: Int,
    val level: RiskLevel,
    val factors: List<EvidenceFactor>,
    val inputHash: String,
    val evaluatedAt: Long = System.currentTimeMillis()
)

class EvidenceFusionEngine {
    private val assessmentCache = ConcurrentHashMap<String, RiskAssessment>()

    fun buildFactors(
        identity: CallerIdentity,
        context: AttackContext,
        scamClassification: ScamClassification? = null,
        isVoiceCloneCritical: Boolean = false
    ): List<EvidenceFactor> {
        val factors = mutableListOf<EvidenceFactor>()

        // 1. Caller Identity reputation weight
        // 1. Caller Identity reputation weight
        val baseRepWeight = identity.baseRiskWeight()
        if (baseRepWeight > 0) {
            factors.add(
                EvidenceFactor(
                    source = "CALLER_REPUTATION",
                    weight = baseRepWeight,
                    description = "Caller reputation '${identity.reputationLevel}' for ${identity.phoneNumber}"
                )
            )
        }

        // 1b. Identity unresolved (WEIGHT_IDENTITY_UNRESOLVED = 10)
        if (!identity.isKnownContact && (identity.source == "UNKNOWN" || identity.source == "system" || identity.displayName.isNullOrBlank())) {
            factors.add(
                EvidenceFactor(
                    source = "IDENTITY_UNRESOLVED",
                    weight = 10,
                    description = "Caller identity could not be independently resolved"
                )
            )
        }

        // 2. Anonymous / private caller
        if (identity.phoneNumber == "UNKNOWN" || identity.phoneNumber.isBlank()) {
            factors.add(
                EvidenceFactor(
                    source = "ANONYMOUS_CALLER",
                    weight = 25,
                    description = "Caller identity hidden or presentation private"
                )
            )
        }

        // 3. Active OTP within 5-minute window (INTENT_OTP_VERIFICATION = 25)
        if (context.otpWithinWindow) {
            factors.add(
                EvidenceFactor(
                    source = "OTP_IN_WINDOW",
                    weight = 25,
                    description = "Valid OTP message received within active 5-minute correlation window"
                )
            )
        }

        // 4. Remote Desktop tool active (INTENT_REMOTE_ACCESS = 30)
        if (context.remoteDesktopActive) {
            factors.add(
                EvidenceFactor(
                    source = "REMOTE_DESKTOP_ACTIVE",
                    weight = 30,
                    description = "Remote desktop sharing application (AnyDesk/TeamViewer/RustDesk) is active"
                )
            )
        }

        // 5. Phishing link detected in recent SMS (MALICIOUS_LINK = 25)
        if (context.phishingLinkDetected) {
            factors.add(
                EvidenceFactor(
                    source = "PHISHING_LINK_DETECTED",
                    weight = 25,
                    description = "Suspicious URL or raw IP link identified in incoming SMS"
                )
            )
        }

        // 6. Callback number mismatch
        if (context.hasCallbackMismatch) {
            factors.add(
                EvidenceFactor(
                    source = "CALLBACK_MISMATCH",
                    weight = 20,
                    description = "Suspicious callback mismatch: SMS advertised callback ${context.callbackNumber} but incoming caller is ${context.activeCallPhoneNumber}"
                )
            )
        }

        // 7. Composite Correlated Attack Context (WEIGHT_ATTACK_CONTEXT = 20 flat)
        if (context.contextType != ContextType.UNKNOWN) {
            factors.add(
                EvidenceFactor(
                    source = "CORRELATED_ATTACK_CONTEXT",
                    weight = 20,
                    description = "Correlated 5-min multi-event threat: ${context.explanation}"
                )
            )
        }

        // 8. Linguistic scam / urgency classification
        scamClassification?.let { scam ->
            if (scam.isScam) {
                val weight = if (scam.scamScore >= 80) 30 else 15
                factors.add(
                    EvidenceFactor(
                        source = "SCAM_CLASSIFICATION_${scam.scamCategory}",
                        weight = weight,
                        description = scam.rationale
                    )
                )
            }
            if (scam.urgencyTactics.isNotEmpty()) {
                factors.add(
                    EvidenceFactor(
                        source = "URGENCY_COERCION",
                        weight = 15,
                        description = "High urgency coercion tactics detected: ${scam.urgencyTactics.joinToString()}"
                    )
                )
            }
        }

        // 9. Voice Clone verdict from VCD
        if (isVoiceCloneCritical) {
            factors.add(
                EvidenceFactor(
                    source = "VOICE_CLONE_CRITICAL",
                    weight = 60,
                    description = "Acoustic deepfake synthesis confidence threshold exceeded"
                )
            )
        }

        return factors
    }

    fun calculateInputHash(factors: List<EvidenceFactor>): String {
        val sortedKeys = factors.map { "${it.source}:${it.weight}" }.sorted().joinToString(";")
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(sortedKeys.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun fuse(factors: List<EvidenceFactor>): Int {
        return factors.sumOf { it.weight }
    }

    fun evaluate(
        identity: CallerIdentity,
        context: AttackContext,
        scamClassification: ScamClassification? = null,
        isVoiceCloneCritical: Boolean = false
    ): RiskAssessment {
        val factors = buildFactors(identity, context, scamClassification, isVoiceCloneCritical)
        val hash = calculateInputHash(factors)

        return assessmentCache.computeIfAbsent(hash) {
            val rawSum = fuse(factors)
            val score = RiskEngine.score(rawSum)
            val level = RiskEngine.band(score)
            RiskAssessment(
                score = score,
                level = level,
                factors = factors,
                inputHash = hash
            )
        }
    }

    fun invalidateCache() {
        assessmentCache.clear()
    }
}

object RiskEngine {
    fun score(fusedWeight: Int): Int = fusedWeight.coerceIn(0, 100)

    fun band(score: Int): RiskLevel = RiskLevel.fromScore(score)
}
