package com.vocis.intelligence.hub

import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.vocis.core.data.database.AppDatabase
import com.vocis.core.data.entity.InteractionEntity
import com.vocis.core.data.entity.SecurityIncidentEntity
import com.vocis.core.domain.model.EventType
import com.vocis.core.domain.model.IncidentType
import com.vocis.core.domain.model.ProtectionAction
import com.vocis.core.domain.model.RiskLevel
import com.vocis.core.domain.model.SecurityEvent
import com.vocis.intelligence.context.AttackContextEngine
import com.vocis.intelligence.identity.CallerIdentity
import com.vocis.intelligence.identity.CallerIdentityResolver
import com.vocis.intelligence.identity.ReputationLevel
import com.vocis.intelligence.linguistic.LocalScamClassifier
import com.vocis.intelligence.linguistic.NotificationSignalExtractor
import com.vocis.intelligence.linguistic.ScamClassification
import com.vocis.intelligence.linguistic.SmsSignalExtractor
import com.vocis.emergency.FamilyAlertDispatcher
import com.vocis.intelligence.incident.SecurityIncidentManager
import com.vocis.intelligence.policy.ProtectionPolicyEngine
import com.vocis.intelligence.risk.EvidenceFusionEngine
import com.vocis.intelligence.risk.RiskEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class InteractionHub(
    private val db: AppDatabase? = null,
    private val identityResolver: CallerIdentityResolver,
    private val contextEngine: AttackContextEngine,
    private val fusionEngine: EvidenceFusionEngine = EvidenceFusionEngine(),
    private val policyEngine: ProtectionPolicyEngine = ProtectionPolicyEngine(db?.protectionPolicyDao()),
    private val incidentManager: SecurityIncidentManager = SecurityIncidentManager(db?.securityIncidentDao()),
    private val familyAlertDispatcher: FamilyAlertDispatcher = FamilyAlertDispatcher(db?.familyContactDao()),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val _interactions = MutableStateFlow<List<InteractionEntity>>(getSeedInteractions())
    val interactions: StateFlow<List<InteractionEntity>> = _interactions.asStateFlow()

    val activeIncident: StateFlow<SecurityIncidentEntity?> = incidentManager.activeIncident

    @Volatile
    private var isVoiceCloneCritical: Boolean = false

    init {
        // Hydrate persisted interactions from Room DB on boot
        scope.launch(Dispatchers.IO) {
            try {
                val dbInteractions = db?.interactionDao()?.getRecent(100) ?: emptyList()
                if (dbInteractions.isNotEmpty()) {
                    val current = _interactions.value
                    val combined = (dbInteractions + current).distinctBy { it.id }.sortedByDescending { it.timestampMs }
                    _interactions.value = combined
                }
            } catch (e: Exception) {
                // Non-fatal Room hydration exception
            }
        }
    }

    fun onVoiceCloneVerdict(isCritical: Boolean) {
        this.isVoiceCloneCritical = isCritical
    }

    fun processEventAsync(event: SecurityEvent) {
        scope.launch {
            processEvent(event)
        }
    }

    suspend fun processEvent(event: SecurityEvent): InteractionEntity {
        return try {
            handleEventInternal(event)
        } catch (e: Throwable) {
            // Fail-open invariant: Never block or crash on exception, allow communication
            createFailOpenInteraction(event, e.message ?: "Unknown error")
        }
    }

    private suspend fun handleEventInternal(event: SecurityEvent): InteractionEntity {
        // 1. Resolve Identity
        val identity = when (event.type) {
            EventType.INCOMING_CALL, EventType.OUTGOING_CALL, EventType.SMS_RECEIVED -> {
                identityResolver.resolve(event.identity)
            }
            else -> {
                CallerIdentity(
                    phoneNumber = event.identity,
                    displayName = null,
                    reputationLevel = ReputationLevel.NEUTRAL,
                    isKnownContact = false,
                    source = "system"
                )
            }
        }

        // 2. Extract linguistic signals and update attack context
        var scamClassification: ScamClassification? = null

        when (event.type) {
            EventType.SMS_RECEIVED -> {
                val smsSignals = SmsSignalExtractor.extractAll(event.metadata)
                contextEngine.onSmsReceived(event, smsSignals)
                scamClassification = LocalScamClassifier.classifyWithGroq(event.metadata)
            }
            EventType.NOTIFICATION_POSTED -> {
                val notifSignals = NotificationSignalExtractor.extractAll(
                    category = null,
                    actions = emptyList(),
                    packageName = event.identity,
                    title = "",
                    text = event.metadata
                )
                contextEngine.onNotificationEvent(event, notifSignals)
                scamClassification = LocalScamClassifier.classifyWithGroq(event.metadata)
            }
            EventType.INCOMING_CALL -> {
                contextEngine.onCallStarted(event)
            }
            EventType.OUTGOING_CALL -> {
                // Outgoing call start
            }
            EventType.PACKAGE_ADDED -> {
                if (NotificationSignalExtractor.isRemoteDesktopActive(event.identity)) {
                    contextEngine.onRemoteDesktopDetected(event)
                }
            }
            EventType.SYSTEM_EVENT -> {
                // Handle lifecycle reset if needed
            }
        }

        // 3. Obtain attack context snapshot
        val attackContext = contextEngine.getActiveContext(event.timestamp)

        // 4. Evidence Fusion & Risk Calculation
        val assessment = fusionEngine.evaluate(
            identity = identity,
            context = attackContext,
            scamClassification = scamClassification,
            isVoiceCloneCritical = isVoiceCloneCritical
        )

        // 5. Protection Policy Decision
        val protectionAction = policyEngine.decide(
            riskScore = assessment.score,
            riskLevel = assessment.level,
            callerIdentity = identity
        )

        // 6. Security Incident reporting if score >= 50 or action is blocking
        val incidentType = determineIncidentType(scamClassification, attackContext)
        val interactionId = event.interactionId ?: event.id

        if (assessment.score >= 50 || protectionAction == ProtectionAction.BLOCK_CALL) {
            incidentManager.reportThreat(
                riskScore = assessment.score,
                riskLevel = assessment.level,
                incidentType = incidentType,
                interactionId = interactionId,
                callerIdentity = identity,
                evidenceFactors = assessment.factors
            )
        }

        // Emergency Family Alert Dispatcher strictly on score > 50
        if (assessment.score > 50) {
            scope.launch {
                familyAlertDispatcher.sendAlert(
                    riskScore = assessment.score,
                    incidentType = incidentType,
                    callerNumber = identity.phoneNumber,
                    interactionId = interactionId
                )
            }
        }

        // 7. Assemble Interaction Entity
        val interaction = InteractionEntity(
            id = interactionId,
            title = "${event.type.name}: ${identity.displayName ?: identity.phoneNumber}",
            timestamp = formatTimestamp(event.timestamp),
            timestampMs = event.timestamp,
            riskLevel = assessment.level,
            summary = assessment.factors.joinToString(separator = "; ") { it.description },
            callerPhoneNumber = identity.phoneNumber,
            callerDisplayName = identity.displayName ?: "",
            callerIdentityType = identity.source,
            callerIdentitySource = identity.source,
            repLevel = identity.reputationLevel.name,
            groqIsScam = scamClassification?.isScam ?: false,
            groqScamScore = scamClassification?.scamScore ?: 0,
            groqScamCategory = scamClassification?.scamCategory ?: "",
            groqUrgencyTactics = scamClassification?.urgencyTactics?.joinToString() ?: "",
            groqAnalysisRationale = scamClassification?.rationale ?: "",
            protectionDecision = protectionAction,
            incidentType = incidentType,
            isBlocked = (protectionAction == ProtectionAction.BLOCK_CALL)
        )

        // 8. Persist and emit
        try {
            db?.interactionDao()?.insert(interaction)
        } catch (e: Exception) {
            // Ignore Room insert exception
        }
        updateInteractionsState(interaction)

        return interaction
    }

    /**
     * Records a completed live call interaction into Room and updates the active StateFlow immediately.
     */
    fun recordCallInteraction(
        phoneNumber: String?,
        displayName: String?,
        threatScore: Int,
        isClone: Boolean,
        verdictText: String,
        durationMs: Long = 0L
    ) {
        scope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val resolvedNumber = phoneNumber?.takeIf { it.isNotBlank() } ?: "Unknown Caller"
            val resolvedName = displayName?.takeIf { it.isNotBlank() }
                ?: if (resolvedNumber != "Unknown Caller") identityResolver.resolve(resolvedNumber).displayName else null
            val title = resolvedName?.takeIf { it.isNotBlank() } ?: resolvedNumber
            val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.US)

            val riskLevel = when {
                isClone || threatScore >= 70 -> RiskLevel.CRITICAL
                threatScore >= 40 -> RiskLevel.HIGH
                threatScore >= 20 -> RiskLevel.ELEVATED
                else -> RiskLevel.LOW
            }

            val summary = if (isClone) {
                "AI Voice Clone Detected: Acoustic spoof score $threatScore%. $verdictText"
            } else if (threatScore > 35) {
                "Suspicious call: Synthetic risk score $threatScore%. $verdictText"
            } else {
                "Screened call: Voice verified bonafide (Threat score: $threatScore%). $verdictText"
            }

            val interaction = InteractionEntity(
                id = "call_${now}_${resolvedNumber.hashCode()}",
                title = title,
                timestamp = sdf.format(Date(now)),
                timestampMs = now,
                riskLevel = riskLevel,
                summary = summary,
                callerPhoneNumber = resolvedNumber,
                callerDisplayName = resolvedName ?: "",
                callerIdentityType = if (resolvedName != null) "CONTACT" else "UNKNOWN",
                groqIsScam = isClone,
                groqScamScore = threatScore,
                groqScamCategory = if (isClone) "Voice Clone Extortion" else "",
                groqUrgencyTactics = if (isClone) "Voice synthesis detected" else "",
                groqAnalysisRationale = verdictText,
                protectionDecision = if (isClone) ProtectionAction.SHOW_SECURITY_INTERVENTION else ProtectionAction.MONITOR_ONLY,
                incidentType = if (isClone) IncidentType.VOICE_CLONE else IncidentType.OTHER,
                isBlocked = false
            )

            try {
                db?.interactionDao()?.insert(interaction)
            } catch (e: Exception) {
                // Ignore Room error
            }
            updateInteractionsState(interaction)
        }
    }

    private fun determineIncidentType(
        scam: ScamClassification?,
        context: com.vocis.intelligence.context.AttackContext
    ): IncidentType {
        return when {
            context.remoteDesktopActive -> IncidentType.REMOTE_ACCESS_SCAM
            context.otpWithinWindow -> IncidentType.OTP_THEFT
            scam?.scamCategory == "DIGITAL_ARREST" -> IncidentType.DIGITAL_ARREST
            scam?.scamCategory == "REMOTE_ACCESS_SCAM" -> IncidentType.REMOTE_ACCESS_SCAM
            scam?.scamCategory == "OTP_THEFT" -> IncidentType.OTP_THEFT
            scam?.scamCategory == "FINANCIAL_FRAUD" -> IncidentType.FINANCIAL_FRAUD
            isVoiceCloneCritical -> IncidentType.VOICE_CLONE
            else -> IncidentType.OTHER
        }
    }

    private fun updateInteractionsState(newInteraction: InteractionEntity) {
        val current = _interactions.value.toMutableList()
        val index = current.indexOfFirst { it.id == newInteraction.id }
        if (index >= 0) {
            current[index] = newInteraction
        } else {
            current.add(0, newInteraction)
        }
        _interactions.value = current
    }

    private fun createFailOpenInteraction(event: SecurityEvent, reason: String): InteractionEntity {
        return InteractionEntity(
            id = event.interactionId ?: event.id,
            title = "${event.type.name}: ${event.identity}",
            timestamp = formatTimestamp(event.timestamp),
            timestampMs = event.timestamp,
            riskLevel = RiskLevel.LOW,
            summary = "Fail-open fallback triggered: $reason",
            callerPhoneNumber = event.identity,
            protectionDecision = ProtectionAction.MONITOR_ONLY,
            isBlocked = false
        )
    }

    private fun formatTimestamp(timestampMs: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return sdf.format(Date(timestampMs))
    }

    companion object {
        @Volatile
        private var instance: InteractionHub? = null

        fun getInstance(context: android.content.Context): InteractionHub {
            return instance ?: synchronized(this) {
                instance ?: createDefault(context.applicationContext).also { instance = it }
            }
        }

        fun setInstance(hub: InteractionHub) {
            instance = hub
        }

        private fun createDefault(context: android.content.Context): InteractionHub {
            val db = AppDatabase.getInstance(context)
            val identityResolver = CallerIdentityResolver(
                context = context,
                dao = db.callerIdentityDao()
            )
            val contextEngine = AttackContextEngine(dao = db.attackContextDao())
            val incidentManager = SecurityIncidentManager(dao = db.securityIncidentDao())
            val policyEngine = ProtectionPolicyEngine(dao = db.protectionPolicyDao())
            return InteractionHub(
                db = db,
                identityResolver = identityResolver,
                contextEngine = contextEngine,
                fusionEngine = EvidenceFusionEngine(),
                policyEngine = policyEngine,
                incidentManager = incidentManager
            )
        }
    }

    private fun getSeedInteractions(): List<InteractionEntity> {
        val now = System.currentTimeMillis()
        val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.US)
        return listOf(
            InteractionEntity(
                id = "seed_cbi_arrest_01",
                title = "DCP Cyber Crime (CBI)",
                timestamp = sdf.format(Date(now - 1000 * 60 * 18)),
                timestampMs = now - 1000 * 60 * 18,
                riskLevel = RiskLevel.CRITICAL,
                summary = "Digital Arrest Extortion: Synthesized CBI officer audio demanding asset transfer under National Security Act.",
                callerPhoneNumber = "+91 98765 43210",
                callerDisplayName = "DCP Cyber Crime (CBI)",
                incidentType = IncidentType.DIGITAL_ARREST,
                groqIsScam = true,
                groqScamScore = 96,
                groqScamCategory = "Digital Arrest / Impersonation",
                groqPrimaryIntent = "COERCION_EXTORTION",
                groqUrgencyTactics = "Threatened physical arrest within 30 minutes unless verified in 'digital custody'.",
                groqAnalysisRationale = "AASIST spectral spoof detector flagged acoustic resonance mismatch. Caller demanded victim stay on camera.",
                isBlocked = true
            ),
            InteractionEntity(
                id = "seed_fedex_scam_02",
                title = "Customs Logistics Authority",
                timestamp = sdf.format(Date(now - 1000 * 60 * 125)),
                timestampMs = now - 1000 * 60 * 125,
                riskLevel = RiskLevel.HIGH,
                summary = "Narcotics Parcel Scam: Fake Mumbai Customs notice claiming contraband parcel seized in victim's name.",
                callerPhoneNumber = "+91 91234 56789",
                callerDisplayName = "Customs Clearance Bureau",
                incidentType = IncidentType.FINANCIAL_FRAUD,
                groqIsScam = true,
                groqScamScore = 88,
                groqScamCategory = "Courier / Customs Fraud",
                groqPrimaryIntent = "FEE_EXTORTION",
                groqUrgencyTactics = "Immediate payment of Rs 48,000 required to avoid police escalation.",
                groqAnalysisRationale = "High pressure urgency keywords detected with unverified caller routing.",
                isBlocked = true
            ),
            InteractionEntity(
                id = "seed_family_mom_03",
                title = "Mom",
                timestamp = sdf.format(Date(now - 1000 * 60 * 360)),
                timestampMs = now - 1000 * 60 * 360,
                riskLevel = RiskLevel.LOW,
                summary = "Incoming Call: Voice matched enrolled biometric baseline (Cosine 0.91). Verified authentic speaker.",
                callerPhoneNumber = "+91 98888 77771",
                callerDisplayName = "Mom",
                callerIdentityType = "FAMILY_CONTACT",
                callerIdentityConfidence = "HIGH",
                isBlocked = false
            ),
            InteractionEntity(
                id = "seed_family_brother_04",
                title = "Brother",
                timestamp = sdf.format(Date(now - 1000 * 60 * 720)),
                timestampMs = now - 1000 * 60 * 720,
                riskLevel = RiskLevel.LOW,
                summary = "Incoming Call: Biometric verification pass. Natural vocal tract prosody and zero synthetic markers.",
                callerPhoneNumber = "+91 98888 77772",
                callerDisplayName = "Brother",
                callerIdentityType = "FAMILY_CONTACT",
                callerIdentityConfidence = "HIGH",
                isBlocked = false
            ),
            InteractionEntity(
                id = "seed_bank_alert_05",
                title = "ICICI Bank Official",
                timestamp = sdf.format(Date(now - 1000 * 60 * 1440)),
                timestampMs = now - 1000 * 60 * 1440,
                riskLevel = RiskLevel.LOW,
                summary = "Official banking SMS: Regular transaction alert Rs 1,500 debited for groceries.",
                callerPhoneNumber = "VK-ICICIB",
                callerDisplayName = "ICICI Bank Alerts",
                isBlocked = false
            )
        )
    }

    fun loadRealCallLogs(context: Context) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                val realCalls = mutableListOf<InteractionEntity>()
                val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.US)
                val cursor = context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    arrayOf(CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.TYPE, CallLog.Calls.CACHED_NAME),
                    null,
                    null,
                    "${CallLog.Calls.DATE} DESC LIMIT 50"
                )

                cursor?.use { c ->
                    val numIdx = c.getColumnIndex(CallLog.Calls.NUMBER)
                    val dateIdx = c.getColumnIndex(CallLog.Calls.DATE)
                    val typeIdx = c.getColumnIndex(CallLog.Calls.TYPE)
                    val nameIdx = c.getColumnIndex(CallLog.Calls.CACHED_NAME)

                    while (c.moveToNext()) {
                        val number = if (numIdx >= 0) c.getString(numIdx) ?: "Unknown" else "Unknown"
                        val dateMs = if (dateIdx >= 0) c.getLong(dateIdx) else System.currentTimeMillis()
                        val type = if (typeIdx >= 0) c.getInt(typeIdx) else CallLog.Calls.INCOMING_TYPE
                        val name = if (nameIdx >= 0) c.getString(nameIdx) else null

                        val typeStr = when (type) {
                            CallLog.Calls.INCOMING_TYPE -> "Incoming Call"
                            CallLog.Calls.OUTGOING_TYPE -> "Outgoing Call"
                            CallLog.Calls.MISSED_TYPE -> "Missed Call"
                            CallLog.Calls.BLOCKED_TYPE -> "Blocked Call"
                            else -> "Phone Call"
                        }

                        val isKnown = !name.isNullOrBlank()
                        realCalls.add(
                            InteractionEntity(
                                id = "calllog_${dateMs}_${number.hashCode()}",
                                title = name ?: number,
                                timestamp = sdf.format(Date(dateMs)),
                                timestampMs = dateMs,
                                riskLevel = if (isKnown) RiskLevel.LOW else RiskLevel.ELEVATED,
                                summary = "$typeStr from ${name ?: number} - screened by VOCIS.",
                                callerPhoneNumber = number,
                                callerDisplayName = name ?: "",
                                callerIdentityType = if (isKnown) "CONTACT" else "UNKNOWN"
                            )
                        )
                    }
                }

                if (realCalls.isNotEmpty()) {
                    val current = _interactions.value
                    val currentNonCallLog = current.filter { !it.id.startsWith("calllog_") }
                    val merged = (currentNonCallLog + realCalls).distinctBy { it.id }.sortedByDescending { it.timestampMs }
                    _interactions.value = merged
                }
            } catch (e: Exception) {
                // Ignore query exceptions
            }
        }
    }
}

