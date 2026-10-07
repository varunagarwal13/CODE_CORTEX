package com.vocis.digitalarrest

data class RuleEvaluationResult(
    val ruleId: String,
    val ruleName: String,
    val isViolated: Boolean,
    val confidence: Float,
    val matchedEvidence: List<String>,
    val explanation: String
)

data class DigitalArrestRuleReport(
    val rules: List<RuleEvaluationResult>,
    val totalViolations: Int,
    val isDigitalArrestConfirmed: Boolean,
    val evaluatedAt: Long = System.currentTimeMillis()
)

object DigitalArrestRules {

    // RULE_01: Law Enforcement / Regulatory Agency Impersonation
    private val AUTHORITY_PATTERNS = listOf(
        "police", "cbi", "central bureau of investigation", "ed", "enforcement directorate",
        "customs", "customs department", "ncb", "narcotics control bureau", "supreme court",
        "high court", "trai", "telecom regulatory authority", "cyber crime", "cyber cell",
        "dcp", "commissioner", "inspector", "sub inspector", "ministry of home affairs",
        "mha", "interpol"
    )

    // RULE_02: Video Confinement & Coerced Isolation Demand
    private val CONFINEMENT_PATTERNS = listOf(
        "stay on video", "do not disconnect", "do not hang up", "lock the door",
        "lock your room", "close the door", "stay in room", "keep camera on",
        "do not tell anyone", "do not inform anyone", "do not contact family",
        "skype call", "skype video", "whatsapp video", "maintain confidentiality",
        "confidential investigation", "secrecy act"
    )

    // RULE_03: Financial Asset Verification / Court Escrow Demand
    private val FINANCIAL_COERCION_PATTERNS = listOf(
        "verification account", "rbi verification", "rbi account", "court escrow",
        "safe custody account", "transfer money", "transfer funds", "deposit funds",
        "deposit money", "security deposit", "bail amount", "penalty payment",
        "refundable deposit", "clearance fee", "rtgs", "imps", "national clearance"
    )

    // RULE_04: Non-Standard Legal Procedure (WhatsApp/Skype Summons)
    private val PROCEDURE_VIOLATION_PATTERNS = listOf(
        "whatsapp warrant", "warrant on whatsapp", "telegram notice", "skype statement",
        "virtual court hearing", "digital arrest warrant", "instant summons", "online arrest",
        "video confession", "digital interrogation"
    )

    // RULE_05: Coercive Urgency & Immediate Arrest Threat
    private val ARREST_THREAT_PATTERNS = listOf(
        "arrest within", "immediate arrest", "non-bailable warrant", "raiding your house",
        "send police team", "cancelling passport", "cancel passport", "blocking aadhaar",
        "block aadhaar", "freezing all accounts", "freeze bank account", "within 1 hour",
        "within 2 hours", "surrender now", "jail within 24 hours"
    )

    fun evaluate(
        callerClaims: List<String>,
        transcript: String = "",
        metadata: String = ""
    ): DigitalArrestRuleReport {
        val combinedText = buildString {
            append(transcript.lowercase())
            append(" ")
            append(metadata.lowercase())
            for (claim in callerClaims) {
                append(" ")
                append(claim.lowercase())
            }
        }

        val rule1 = evaluateRule(
            ruleId = "RULE_01",
            ruleName = "Authority / Law Enforcement Impersonation Check",
            patterns = AUTHORITY_PATTERNS,
            text = combinedText,
            explanationOnMatch = "Caller or transcript falsely claims official authority from police, court, or investigative agencies."
        )

        val rule2 = evaluateRule(
            ruleId = "RULE_02",
            ruleName = "Demand for Video Confinement & Coerced Isolation Check",
            patterns = CONFINEMENT_PATTERNS,
            text = combinedText,
            explanationOnMatch = "Caller demands continuous video surveillance, room isolation, or prohibits contacting relatives."
        )

        val rule3 = evaluateRule(
            ruleId = "RULE_03",
            ruleName = "Financial Asset Verification & Escrow Transfer Demand Check",
            patterns = FINANCIAL_COERCION_PATTERNS,
            text = combinedText,
            explanationOnMatch = "Caller demands financial transfer to 'verification accounts', RBI escrow, or purported bail fees."
        )

        val rule4 = evaluateRule(
            ruleId = "RULE_04",
            ruleName = "Non-Standard Legal Procedure Check",
            patterns = PROCEDURE_VIOLATION_PATTERNS,
            text = combinedText,
            explanationOnMatch = "Purported legal summons or warrants served via non-standard channels (WhatsApp/Skype)."
        )

        val rule5 = evaluateRule(
            ruleId = "RULE_05",
            ruleName = "Coercive Urgency & Immediate Arrest Threat Check",
            patterns = ARREST_THREAT_PATTERNS,
            text = combinedText,
            explanationOnMatch = "Caller exerts intense psychological intimidation threatening imminent arrest or asset freezing."
        )

        val allRules = listOf(rule1, rule2, rule3, rule4, rule5)
        val totalViolations = allRules.count { it.isViolated }
        // Confirmed if at least 2 rules are violated, or authority impersonation + (confinement or financial coercion)
        val isConfirmed = totalViolations >= 2 || (rule1.isViolated && (rule2.isViolated || rule3.isViolated))

        return DigitalArrestRuleReport(
            rules = allRules,
            totalViolations = totalViolations,
            isDigitalArrestConfirmed = isConfirmed
        )
    }

    private fun evaluateRule(
        ruleId: String,
        ruleName: String,
        patterns: List<String>,
        text: String,
        explanationOnMatch: String
    ): RuleEvaluationResult {
        val matches = patterns.filter { pattern -> text.contains(pattern) }
        val isViolated = matches.isNotEmpty()
        val confidence = if (isViolated) {
            (matches.size.toFloat() / 3f).coerceIn(0.6f, 1.0f)
        } else {
            0.0f
        }

        val explanation = if (isViolated) {
            "$explanationOnMatch Matches found: ${matches.joinToString()}."
        } else {
            "No evidence detected for $ruleName."
        }

        return RuleEvaluationResult(
            ruleId = ruleId,
            ruleName = ruleName,
            isViolated = isViolated,
            confidence = confidence,
            matchedEvidence = matches,
            explanation = explanation
        )
    }
}
