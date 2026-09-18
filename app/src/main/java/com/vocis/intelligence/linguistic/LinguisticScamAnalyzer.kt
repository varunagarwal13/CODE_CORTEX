package com.vocis.intelligence.linguistic

import com.vocis.sensor.normalizer.RemoteDesktopPackages

data class SmsSignals(
    val hasOtp: Boolean,
    val urgencyKeywords: List<String>,
    val phishingLinks: List<String>,
    val isEmergency: Boolean
)

object SmsSignalExtractor {
    private val OTP_PATTERN = Regex("""\b\d{4,8}\b""")
    private val OTP_CONTEXT_KEYWORDS = listOf(
        "otp", "code", "password", "pin", "verification", "one time", "passcode", "secret"
    )
    private val URGENCY_KEYWORDS = listOf(
        "suspended", "blocked", "electricity", "kyc", "cutoff", "cut-off",
        "immediately", "urgent", "deactivate", "deactivated", "disconnect",
        "disconnected", "penalty", "24 hours", "expire", "expired", "legal action"
    )
    private val URL_PATTERN = Regex("""https?://[^\s/$.?#].[^\s]*|(?:\d{1,3}\.){3}\d{1,3}(?::\d+)?[^\s]*""", RegexOption.IGNORE_CASE)

    fun extractOtpPresence(body: String): Boolean {
        if (body.isBlank()) return false
        val hasDigits = OTP_PATTERN.containsMatchIn(body)
        val hasContext = OTP_CONTEXT_KEYWORDS.any { body.contains(it, ignoreCase = true) }
        return hasDigits && hasContext
    }

    fun extractUrgencySignals(body: String): List<String> {
        if (body.isBlank()) return emptyList()
        return URGENCY_KEYWORDS.filter { body.contains(it, ignoreCase = true) }
    }

    fun extractPhishingLinks(body: String): List<String> {
        if (body.isBlank()) return emptyList()
        return URL_PATTERN.findAll(body).map { it.value }.toList()
    }

    fun isEmergencyKeyword(body: String): Boolean {
        return body.contains("VOCIS", ignoreCase = true)
    }

    fun extractAll(body: String): SmsSignals {
        return SmsSignals(
            hasOtp = extractOtpPresence(body),
            urgencyKeywords = extractUrgencySignals(body),
            phishingLinks = extractPhishingLinks(body),
            isEmergency = isEmergencyKeyword(body)
        )
    }
}

data class NotificationSignals(
    val isVoipCall: Boolean,
    val isRemoteDesktop: Boolean,
    val financialSignals: List<String>
)

object NotificationSignalExtractor {
    private val FINANCIAL_KEYWORDS = listOf(
        "debited", "credited", "transfer", "transferred", "inr", "rs.", "rs ", "₹",
        "upi", "transaction", "balance", "payment", "bank", "account", "withdrawn"
    )

    fun isVoipCallNotification(category: String?, actions: List<String>): Boolean {
        val isCallCategory = category.equals("call", ignoreCase = true) || category.equals("msg", ignoreCase = true)
        val hasCallActions = actions.any { action ->
            action.contains("answer", ignoreCase = true) ||
            action.contains("decline", ignoreCase = true) ||
            action.contains("reject", ignoreCase = true) ||
            action.contains("accept", ignoreCase = true)
        }
        return isCallCategory && hasCallActions
    }

    fun isRemoteDesktopActive(packageName: String): Boolean {
        return RemoteDesktopPackages.isRemoteDesktop(packageName)
    }

    fun extractFinancialSignals(title: String, text: String): List<String> {
        val combined = "$title $text"
        if (combined.isBlank()) return emptyList()
        return FINANCIAL_KEYWORDS.filter { combined.contains(it, ignoreCase = true) }
    }

    fun extractAll(category: String?, actions: List<String>, packageName: String, title: String, text: String): NotificationSignals {
        return NotificationSignals(
            isVoipCall = isVoipCallNotification(category, actions),
            isRemoteDesktop = isRemoteDesktopActive(packageName),
            financialSignals = extractFinancialSignals(title, text)
        )
    }
}

data class ScamClassification(
    val isScam: Boolean,
    val scamScore: Int,
    val scamCategory: String,
    val urgencyTactics: List<String>,
    val rationale: String,
    val isLocalFallback: Boolean = true
)

object LocalScamClassifier {
    private val DIGITAL_ARREST_KEYWORDS = listOf(
        "police", "cbi", "trai", "court", "fir", "arrest", "customs",
        "narcotics", "money laundering", "cyber crime", "supreme court", "warrant"
    )

    private val OTP_THEFT_KEYWORDS = listOf(
        "otp", "pin", "password", "verification code", "one time password", "cvv"
    )

    private val REMOTE_ACCESS_KEYWORDS = listOf(
        "anydesk", "teamviewer", "rustdesk", "quicksupport", "screen share",
        "remote support", "install apk", "download app"
    )

    private val FINANCIAL_FRAUD_KEYWORDS = listOf(
        "electricity bill", "power cut", "lottery", "prize", "won cash",
        "claim reward", "kbc", "part time job", "youtube like"
    )

    fun classify(text: String): ScamClassification {
        if (text.isBlank()) {
            return ScamClassification(
                isScam = false,
                scamScore = 0,
                scamCategory = "SAFE",
                urgencyTactics = emptyList(),
                rationale = "Empty text",
                isLocalFallback = true
            )
        }

        val lower = text.lowercase()
        val digitalArrestMatches = DIGITAL_ARREST_KEYWORDS.filter { lower.contains(it) }
        val remoteAccessMatches = REMOTE_ACCESS_KEYWORDS.filter { lower.contains(it) }
        val otpTheftMatches = OTP_THEFT_KEYWORDS.filter { lower.contains(it) }
        val financialFraudMatches = FINANCIAL_FRAUD_KEYWORDS.filter { lower.contains(it) }

        val urgencyMatches = SmsSignalExtractor.extractUrgencySignals(text)

        return when {
            digitalArrestMatches.isNotEmpty() -> {
                val score = (80 + digitalArrestMatches.size * 5 + urgencyMatches.size * 5).coerceAtMost(98)
                ScamClassification(
                    isScam = true,
                    scamScore = score,
                    scamCategory = "DIGITAL_ARREST",
                    urgencyTactics = urgencyMatches + digitalArrestMatches,
                    rationale = "Matches digital arrest / law enforcement impersonation patterns: ${digitalArrestMatches.joinToString()}",
                    isLocalFallback = true
                )
            }
            remoteAccessMatches.isNotEmpty() -> {
                val score = (80 + remoteAccessMatches.size * 5 + urgencyMatches.size * 5).coerceAtMost(95)
                ScamClassification(
                    isScam = true,
                    scamScore = score,
                    scamCategory = "REMOTE_ACCESS_SCAM",
                    urgencyTactics = urgencyMatches + remoteAccessMatches,
                    rationale = "Matches remote desktop / unverified application installation: ${remoteAccessMatches.joinToString()}",
                    isLocalFallback = true
                )
            }
            otpTheftMatches.isNotEmpty() && urgencyMatches.isNotEmpty() -> {
                val score = (75 + otpTheftMatches.size * 5 + urgencyMatches.size * 5).coerceAtMost(90)
                ScamClassification(
                    isScam = true,
                    scamScore = score,
                    scamCategory = "OTP_THEFT",
                    urgencyTactics = urgencyMatches + otpTheftMatches,
                    rationale = "Matches OTP coercion under urgent pretext: ${otpTheftMatches.joinToString()}",
                    isLocalFallback = true
                )
            }
            financialFraudMatches.isNotEmpty() -> {
                val score = (70 + financialFraudMatches.size * 5 + urgencyMatches.size * 5).coerceAtMost(85)
                ScamClassification(
                    isScam = true,
                    scamScore = score,
                    scamCategory = "FINANCIAL_FRAUD",
                    urgencyTactics = urgencyMatches + financialFraudMatches,
                    rationale = "Matches financial fraud / utility cut-off bait: ${financialFraudMatches.joinToString()}",
                    isLocalFallback = true
                )
            }
            urgencyMatches.size >= 2 -> {
                ScamClassification(
                    isScam = true,
                    scamScore = 45,
                    scamCategory = "SUSPICIOUS_URGENCY",
                    urgencyTactics = urgencyMatches,
                    rationale = "Multiple high-urgency keywords detected without clear verified context",
                    isLocalFallback = true
                )
            }
            else -> {
                ScamClassification(
                    isScam = false,
                    scamScore = 5,
                    scamCategory = "SAFE",
                    urgencyTactics = urgencyMatches,
                    rationale = "No suspicious scam indicators found",
                    isLocalFallback = true
                )
            }
        }
    }

    private val groqClient by lazy { com.vocis.speech.llm.GroqLlmClient() }

    suspend fun classifyWithGroq(text: String): ScamClassification = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (text.isBlank()) return@withContext classify(text)
        try {
            val groqResult = groqClient.analyzeTranscript(text)
            if (groqResult != null) {
                val score = ((groqResult.confidence * 100).toInt()).coerceIn(0, 100)
                return@withContext ScamClassification(
                    isScam = groqResult.isScam,
                    scamScore = if (groqResult.isScam) score.coerceAtLeast(60) else score.coerceAtMost(25),
                    scamCategory = groqResult.scamCategory.name,
                    urgencyTactics = groqResult.coercionTactics,
                    rationale = groqResult.rawExplanation?.ifBlank { null }
                        ?: "Groq Cloud LLM identified ${groqResult.scamCategory.name} pattern.",
                    isLocalFallback = false
                )
            }
        } catch (_: Exception) {}
        classify(text)
    }
}
