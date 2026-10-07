package com.vocis.intelligence.context

import com.vocis.core.data.dao.AttackContextDao
import com.vocis.core.data.entity.AttackContextEntity
import com.vocis.core.domain.model.IncidentType
import com.vocis.core.domain.model.SecurityEvent
import com.vocis.intelligence.linguistic.NotificationSignals
import com.vocis.intelligence.linguistic.SmsSignalExtractor
import com.vocis.intelligence.linguistic.SmsSignals
import com.vocis.sensor.normalizer.NotificationNormalizer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Cross-channel 5-minute sliding temporal correlation engine.
 * Faithfully ports VOCIS multi-event attack correlation pipeline:
 * - SMS Intent Classifier (OTP, Financial, Urgency, Digital Arrest, Telecom, Utility, Parcel)
 * - URL Analyzer (Phishing, IP in URL, URL Shorteners)
 * - Callback Number Mismatch (Caller != number advertised in suspicious SMS)
 * - Screen Takeover / Remote Desktop Correlation (AnyDesk/TeamViewer/RustDesk during active call)
 */
class AttackContextEngine(
    private val dao: AttackContextDao? = null
) {
    companion object {
        const val WINDOW_MS: Long = 300_000L // 5 minutes sliding correlation window
    }

    private val mutex = Mutex()

    // Core call state
    private var hasActiveCall: Boolean = false
    private var activeCallPhoneNumber: String? = null
    private var primaryInteractionId: String? = null
    private var windowStartMs: Long = 0L

    // Correlated threat states
    private var hasActiveOtp: Boolean = false
    private var otpSender: String = ""
    private var otpTimestamp: Long = 0L

    private var remoteDesktopActive: Boolean = false
    private var phishingLinkDetected: Boolean = false

    private var hasFinancial: Boolean = false
    private var hasAuthorityThreat: Boolean = false
    private var hasDeliveryThreat: Boolean = false
    private var hasUtilityThreat: Boolean = false
    private var hasTelecomThreat: Boolean = false
    private var hasCallbackMismatch: Boolean = false
    private var callbackNumber: String? = null

    private val detectedPatterns = mutableListOf<String>()
    private val contributingSignals = mutableListOf<RiskSignal>()
    private val contributingEventIds = mutableListOf<String>()

    /**
     * Ingests incoming SMS and parses deterministic scam signals using VOCIS classifiers.
     */
    suspend fun onSmsReceived(event: SecurityEvent, text: String = "", sender: String = ""): AttackContext = mutex.withLock {
        contributingEventIds.add(event.id)
        val now = event.timestamp

        val body = if (text.isNotBlank()) text else event.metadata
        val from = if (sender.isNotBlank()) sender else event.identity

        // 1. Run VOCIS SMS Intent Classifier
        val smsSignals = SmsIntentClassifier.classify(body, from)
        contributingSignals.addAll(smsSignals)

        // 2. Run VOCIS URL Analyzer
        val urlSignals = UrlAnalyzer.analyze(body)
        contributingSignals.addAll(urlSignals)
        if (urlSignals.isNotEmpty()) {
            phishingLinkDetected = true
            detectedPatterns.add("Phishing / Suspicious Link in SMS")
        }

        // 3. Check for callback number mismatch
        val extractedNumbers = PhoneNumberExtractor.extract(body)
        val hasCallbackIntent = smsSignals.any {
            it.type == ScamSignalType.CALL_THIS_NUMBER || it.type == ScamSignalType.CONTACT_AGENT
        }
        if (hasCallbackIntent && extractedNumbers.isNotEmpty()) {
            callbackNumber = extractedNumbers.first()
            if (hasActiveCall && activeCallPhoneNumber != null && !extractedNumbers.contains(activeCallPhoneNumber)) {
                hasCallbackMismatch = true
                detectedPatterns.add("Suspicious Callback Mismatch: SMS advertised $callbackNumber but call is from $activeCallPhoneNumber")
            }
        }

        // 4. Update category states
        for (signal in smsSignals) {
            when (signal.type.category) {
                ScamSignalCategory.AUTHENTICATION -> {
                    hasActiveOtp = true
                    otpSender = from
                    otpTimestamp = now
                    detectedPatterns.add("OTP Authentication Delivery")
                }
                ScamSignalCategory.FINANCIAL -> {
                    hasFinancial = true
                    detectedPatterns.add("Financial Transaction Alert")
                }
                ScamSignalCategory.GOVERNMENT -> {
                    hasAuthorityThreat = true
                    detectedPatterns.add("Authority / Digital Arrest Notice")
                }
                ScamSignalCategory.DELIVERY -> {
                    hasDeliveryThreat = true
                    detectedPatterns.add("Parcel / Courier Impersonation")
                }
                ScamSignalCategory.UTILITY -> {
                    hasUtilityThreat = true
                    detectedPatterns.add("Utility Disconnection Notice")
                }
                ScamSignalCategory.TELECOM -> {
                    hasTelecomThreat = true
                    detectedPatterns.add("Telecom / SIM Block Notice")
                }
                ScamSignalCategory.REMOTE_ACCESS -> {
                    remoteDesktopActive = true
                    detectedPatterns.add("Remote Access Tool Request")
                }
                else -> {}
            }
        }

        // Correlate with active call if one is ongoing
        if (hasActiveCall) {
            correlateAndPersist(now)
        }

        buildContextLocked(now)
    }

    /**
     * Backward-compatible overload for legacy callers.
     */
    suspend fun onSmsReceived(event: SecurityEvent, signals: SmsSignals) = mutex.withLock {
        contributingEventIds.add(event.id)
        if (signals.hasOtp) {
            hasActiveOtp = true
            otpSender = event.identity
            otpTimestamp = event.timestamp
            detectedPatterns.add("OTP Authentication Delivery")
        }
        if (signals.phishingLinks.isNotEmpty()) {
            phishingLinkDetected = true
            detectedPatterns.add("Phishing / Suspicious Link in SMS")
        }

        if (hasActiveCall && signals.hasOtp) {
            correlateAndPersist(event.timestamp)
        }
    }

    /**
     * Ingests call started event and correlates with sliding 5-minute context window.
     */
    suspend fun onCallStarted(event: SecurityEvent): AttackContext = mutex.withLock {
        contributingEventIds.add(event.id)
        hasActiveCall = true
        activeCallPhoneNumber = event.identity
        primaryInteractionId = event.interactionId ?: event.id
        windowStartMs = event.timestamp
        val now = event.timestamp

        // Check callback mismatch against previously received SMS
        if (callbackNumber != null && activeCallPhoneNumber != null && callbackNumber != activeCallPhoneNumber) {
            hasCallbackMismatch = true
            detectedPatterns.add("Callback Mismatch: Caller $activeCallPhoneNumber != SMS contact $callbackNumber")
        }

        correlateAndPersist(now)
        buildContextLocked(now)
    }

    suspend fun onCallEnded() = mutex.withLock {
        hasActiveCall = false
        activeCallPhoneNumber = null
        // Call ended, but OTP and threat context persist for 5-minute sliding duration
    }

    suspend fun onRemoteDesktopDetected(event: SecurityEvent? = null) = mutex.withLock {
        remoteDesktopActive = true
        event?.let { contributingEventIds.add(it.id) }
        detectedPatterns.add("Remote Desktop Sharing Tool Active")
        if (hasActiveCall) {
            correlateAndPersist(System.currentTimeMillis())
        }
    }

    suspend fun onNotificationEvent(event: SecurityEvent, signals: NotificationSignals): AttackContext = mutex.withLock {
        contributingEventIds.add(event.id)
        val now = event.timestamp
        val content = event.metadata

        // 1. Remote desktop active
        if (signals.isRemoteDesktop) {
            remoteDesktopActive = true
            detectedPatterns.add("Remote Desktop Active in Notification")
        }

        // 2. Financial alert notification (GPay, PhonePe, Paytm, banking debit alerts)
        val isFinancialApp = NotificationNormalizer.isFinancialPackage(event.identity)
        if (signals.financialSignals.isNotEmpty() || isFinancialApp) {
            hasFinancial = true
            detectedPatterns.add("Financial Transaction Alert in Notification")
        }

        // 3. OTP Authentication in notification
        if (SmsSignalExtractor.extractOtpPresence(content)) {
            hasActiveOtp = true
            otpSender = event.identity
            otpTimestamp = now
            detectedPatterns.add("OTP Authentication Delivery in Notification")
        }

        if (hasActiveCall && (signals.isRemoteDesktop || hasFinancial || hasActiveOtp)) {
            correlateAndPersist(now)
        }

        buildContextLocked(now)
    }

    /**
     * Ingests outgoing call event and correlates destination number with recent SMS callback numbers.
     */
    suspend fun onOutgoingCall(event: SecurityEvent): AttackContext = mutex.withLock {
        contributingEventIds.add(event.id)
        hasActiveCall = true
        activeCallPhoneNumber = event.identity
        val destination = event.identity
        val now = event.timestamp
        windowStartMs = now

        // Check if destination matches a suspicious callback number extracted from recent SMS
        if (callbackNumber != null && destination.isNotBlank()) {
            val cleanDest = destination.filter { it.isDigit() }
            val cleanCallback = callbackNumber!!.filter { it.isDigit() }
            if (cleanDest.isNotEmpty() && cleanCallback.isNotEmpty() &&
                (cleanDest.endsWith(cleanCallback) || cleanCallback.endsWith(cleanDest))
            ) {
                hasCallbackMismatch = true
                detectedPatterns.add("Suspicious Callback Dialed: Outgoing call to advertised phishing contact $destination")
            }
        }

        correlateAndPersist(now)
        buildContextLocked(now)
    }

    /**
     * Returns current active composite AttackContext evaluated against the 5-minute sliding window.
     */
    suspend fun getActiveContext(nowMs: Long = System.currentTimeMillis()): AttackContext = mutex.withLock {
        buildContextLocked(nowMs)
    }

    private fun buildContextLocked(nowMs: Long): AttackContext {
        val otpWithinWindow = hasActiveOtp && (nowMs - otpTimestamp) < WINDOW_MS

        val contextType: ContextType
        val inferredIntent: InferredIntent
        var explanation = ""
        var riskWeight = 0

        when {
            remoteDesktopActive && hasActiveCall -> {
                contextType = ContextType.REMOTE_ACCESS_SCAM
                inferredIntent = InferredIntent.POSSIBLE_REMOTE_ACCESS_SCAM
                explanation = "Active call underway while screen sharing or remote desktop tool (AnyDesk/TeamViewer/RustDesk) is running."
                riskWeight = 60
            }
            hasAuthorityThreat && hasActiveCall -> {
                contextType = ContextType.GOVERNMENT_IMPERSONATION
                inferredIntent = InferredIntent.POSSIBLE_GOVERNMENT_IMPERSONATION
                explanation = "Caller context correlated with recent law enforcement, CBI, police, court, or digital arrest threats."
                riskWeight = 50
            }
            otpWithinWindow && hasActiveCall -> {
                contextType = ContextType.OTP_THEFT
                inferredIntent = InferredIntent.POSSIBLE_OTP_THEFT
                explanation = "Incoming call initiated within 5 minutes of sensitive OTP verification delivery from $otpSender."
                riskWeight = 40
            }
            hasDeliveryThreat && hasActiveCall -> {
                contextType = ContextType.PARCEL_SCAM
                inferredIntent = InferredIntent.POSSIBLE_PARCEL_SCAM
                explanation = "Active call correlated with fake package delivery, courier, or customs contraband alert."
                riskWeight = 35
            }
            hasTelecomThreat && hasActiveCall -> {
                contextType = ContextType.TELECOM_IMPERSONATION
                inferredIntent = InferredIntent.POSSIBLE_TELECOM_IMPERSONATION
                explanation = "Active call correlated with SIM deactivation, KYC expiry, or TRAI/DoT service suspension threat."
                riskWeight = 35
            }
            hasUtilityThreat && hasActiveCall -> {
                contextType = ContextType.UTILITY_SCAM
                inferredIntent = InferredIntent.POSSIBLE_UTILITY_SCAM
                explanation = "Active call correlated with electricity power disconnection threat."
                riskWeight = 30
            }
            hasCallbackMismatch -> {
                contextType = ContextType.SOCIAL_ENGINEERING
                inferredIntent = InferredIntent.POSSIBLE_SOCIAL_ENGINEERING
                explanation = "Suspicious callback mismatch: SMS requested call to $callbackNumber but incoming caller is $activeCallPhoneNumber."
                riskWeight = 30
            }
            hasFinancial && hasActiveCall -> {
                contextType = ContextType.FINANCIAL_FRAUD
                inferredIntent = InferredIntent.POSSIBLE_FINANCIAL_FRAUD
                explanation = "Active call during sensitive financial debit or banking alert window."
                riskWeight = 25
            }
            else -> {
                contextType = ContextType.UNKNOWN
                inferredIntent = InferredIntent.UNKNOWN
                explanation = "No correlated multi-event composite attack detected."
                riskWeight = 0
            }
        }

        return AttackContext(
            hasActiveOtp = hasActiveOtp,
            otpWithinWindow = otpWithinWindow,
            remoteDesktopActive = remoteDesktopActive,
            phishingLinkDetected = phishingLinkDetected,
            activeCallPhoneNumber = if (hasActiveCall) activeCallPhoneNumber else null,
            windowStartMs = windowStartMs,
            contextType = contextType,
            inferredIntent = inferredIntent,
            detectedPatterns = detectedPatterns.toList(),
            explanation = explanation,
            hasCallbackMismatch = hasCallbackMismatch,
            callbackNumber = callbackNumber,
            hasAuthorityThreat = hasAuthorityThreat,
            hasDeliveryThreat = hasDeliveryThreat,
            hasUtilityThreat = hasUtilityThreat,
            hasTelecomThreat = hasTelecomThreat,
            hasFinancialThreat = hasFinancial,
            contributingSignals = contributingSignals.toList(),
            compositeRiskWeight = riskWeight
        )
    }

    private suspend fun correlateAndPersist(nowMs: Long) {
        val currentDao = dao ?: return
        val context = buildContextLocked(nowMs)
        if (context.contextType == ContextType.UNKNOWN) return

        val incidentType = when (context.contextType) {
            ContextType.REMOTE_ACCESS_SCAM -> IncidentType.REMOTE_ACCESS_SCAM
            ContextType.OTP_THEFT -> IncidentType.OTP_THEFT
            ContextType.GOVERNMENT_IMPERSONATION -> IncidentType.DIGITAL_ARREST
            ContextType.FINANCIAL_FRAUD -> IncidentType.FINANCIAL_FRAUD
            else -> IncidentType.OTHER
        }

        val entity = AttackContextEntity(
            contextId = UUID.randomUUID().toString(),
            patternType = incidentType,
            triggeredAt = nowMs,
            expiresAt = nowMs + WINDOW_MS,
            primaryInteractionId = primaryInteractionId ?: UUID.randomUUID().toString(),
            contributingEventIds = contributingEventIds.joinToString(","),
            isActive = true,
            description = context.explanation
        )
        try {
            currentDao.insert(entity)
        } catch (_: Exception) {
            // Non-critical cache failure
        }
    }

    suspend fun reset() = mutex.withLock {
        hasActiveOtp = false
        otpSender = ""
        otpTimestamp = 0L
        hasActiveCall = false
        activeCallPhoneNumber = null
        primaryInteractionId = null
        windowStartMs = 0L
        remoteDesktopActive = false
        phishingLinkDetected = false
        hasFinancial = false
        hasAuthorityThreat = false
        hasDeliveryThreat = false
        hasUtilityThreat = false
        hasTelecomThreat = false
        hasCallbackMismatch = false
        callbackNumber = null
        detectedPatterns.clear()
        contributingSignals.clear()
        contributingEventIds.clear()
    }
}
