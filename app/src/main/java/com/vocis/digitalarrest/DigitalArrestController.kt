package com.vocis.digitalarrest

import android.content.Context
import com.vocis.core.data.entity.SecurityIncidentEntity
import com.vocis.emergency.FamilyAlertDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

enum class DigitalArrestPhase(val title: String, val description: String) {
    ALERT_DETECTED(
        title = "Phase 1: Alert Detected",
        description = "High-urgency authority impersonation pattern identified on active communication channel."
    ),
    CALL_ISOLATION_ASSESSMENT(
        title = "Phase 2: Call Isolation Assessment",
        description = "Screening for demands of video confinement, closed room isolation, or silence directives."
    ),
    AUTHORITY_CLAIM_VERIFICATION(
        title = "Phase 3: Authority Claim Verification",
        description = "Screening claimed departmental credentials (Police, CBI, ED, Customs, Supreme Court)."
    ),
    LEGAL_PROCEDURE_EDUCATION(
        title = "Phase 4: Legal Procedure Education",
        description = "Informing user of official Indian legal process: warrants and summons are never served over video call."
    ),
    FINANCIAL_COERCION_CHECK(
        title = "Phase 5: Financial Coercion Check",
        description = "Screening for account transfer demands, RBI verification escrow, or digital bail demands."
    ),
    EVIDENCE_COLLECTION(
        title = "Phase 6: Evidence Collection",
        description = "Gathers suspect caller identity, timestamps, voice clone verdicts, and extortion claims."
    ),
    RULE_EVALUATION(
        title = "Phase 7: Deterministic Rule Evaluation",
        description = "Executes forensic evaluation of RULE_01 through RULE_05 against gathered evidence."
    ),
    FAMILY_SOS_CONFIRMATION(
        title = "Phase 8: Family SOS Confirmation",
        description = "Verifying emergency family alerts dispatched and trusted contact notifications active."
    ),
    FORENSIC_REPORT_GENERATION(
        title = "Phase 9: Forensic Report Generation",
        description = "Compiles case dossier and generates cryptographically signed SHA-256 sealed PDF document."
    ),
    DEFENSE_COMPLETED(
        title = "Phase 10: Defense Completed",
        description = "Digital arrest threat countered. Guidance provided for filing report on cybercrime.gov.in (1930)."
    )
}

data class DigitalArrestState(
    val currentPhase: DigitalArrestPhase = DigitalArrestPhase.ALERT_DETECTED,
    val phaseIndex: Int = 1,
    val incidentId: String? = null,
    val callerPhoneNumber: String? = null,
    val callerDisplayName: String? = null,
    val collectedClaims: List<String> = emptyList(),
    val ruleReport: DigitalArrestRuleReport? = null,
    val generatedPdfFile: File? = null,
    val evidenceSeal: String? = null,
    val isSosDispatched: Boolean = false,
    val errorMessage: String? = null
)

class DigitalArrestController(
    private val familyAlertDispatcher: FamilyAlertDispatcher? = null
) {
    private val _state = MutableStateFlow(DigitalArrestState())
    val state: StateFlow<DigitalArrestState> = _state.asStateFlow()

    fun simulateTrigger(context: Context, incident: SecurityIncidentEntity? = null) {
        val claims = mutableListOf<String>()
        if (incident != null) {
            claims.add(incident.explanation)
        } else {
            claims.add("Claimed DCP Cyber Crime Mumbai; demanded video call on Skype regarding illegal package.")
            claims.add("Demanded transfer of ₹2,50,000 to RBI verification account to avoid immediate arrest warrant.")
        }

        val initialRuleReport = DigitalArrestRules.evaluate(
            callerClaims = claims,
            transcript = incident?.explanation ?: ""
        )

        _state.value = DigitalArrestState(
            currentPhase = DigitalArrestPhase.ALERT_DETECTED,
            phaseIndex = 1,
            incidentId = incident?.incidentId ?: "DA_SIM_${System.currentTimeMillis()}",
            callerPhoneNumber = incident?.callerPhoneNumber ?: "+919876543210",
            callerDisplayName = incident?.callerDisplayName ?: "Purported Cyber Cell",
            collectedClaims = claims,
            ruleReport = initialRuleReport,
            generatedPdfFile = null,
            evidenceSeal = null,
            isSosDispatched = false
        )
    }

    fun addClaim(claim: String) {
        val currentClaims = _state.value.collectedClaims.toMutableList()
        currentClaims.add(claim)
        val updatedReport = DigitalArrestRules.evaluate(callerClaims = currentClaims)
        _state.value = _state.value.copy(
            collectedClaims = currentClaims,
            ruleReport = updatedReport
        )
    }

    fun nextPhase(): DigitalArrestPhase {
        val phases = DigitalArrestPhase.values()
        val currentIndex = _state.value.currentPhase.ordinal
        if (currentIndex < phases.size - 1) {
            val next = phases[currentIndex + 1]
            _state.value = _state.value.copy(
                currentPhase = next,
                phaseIndex = next.ordinal + 1
            )
            return next
        }
        return _state.value.currentPhase
    }

    fun previousPhase(): DigitalArrestPhase {
        val phases = DigitalArrestPhase.values()
        val currentIndex = _state.value.currentPhase.ordinal
        if (currentIndex > 0) {
            val prev = phases[currentIndex - 1]
            _state.value = _state.value.copy(
                currentPhase = prev,
                phaseIndex = prev.ordinal + 1
            )
            return prev
        }
        return _state.value.currentPhase
    }

    fun evaluateRules(transcript: String = ""): DigitalArrestRuleReport {
        val report = DigitalArrestRules.evaluate(
            callerClaims = _state.value.collectedClaims,
            transcript = transcript
        )
        _state.value = _state.value.copy(ruleReport = report)
        return report
    }

    fun generateForensicReport(context: Context): File? {
        val currentReport = _state.value.ruleReport ?: evaluateRules()
        return try {
            val mockIncident = SecurityIncidentEntity(
                incidentId = _state.value.incidentId ?: "DA_${System.currentTimeMillis()}",
                incidentType = com.vocis.core.domain.model.IncidentType.DIGITAL_ARREST,
                severity = com.vocis.core.domain.model.RiskLevel.CRITICAL,
                status = com.vocis.core.domain.model.IncidentStatus.CONFIRMED,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                resolvedAt = null,
                riskScore = 95,
                explanation = "Digital Arrest extortion and authority coercion pattern detected.",
                recommendedActions = "Disconnect call. Contact 1930.",
                relatedInteractionIds = "",
                callerPhoneNumber = _state.value.callerPhoneNumber,
                callerDisplayName = _state.value.callerDisplayName
            )

            val file = DigitalArrestPdfGenerator.generatePdf(
                context = context,
                incident = mockIncident,
                ruleReport = currentReport,
                claims = _state.value.collectedClaims
            )

            val meta = DigitalArrestPdfGenerator.PdfEvidenceMetadata(
                incidentId = mockIncident.incidentId,
                timestamp = mockIncident.createdAt,
                callerPhoneNumber = mockIncident.callerPhoneNumber,
                callerName = mockIncident.callerDisplayName,
                riskScore = mockIncident.riskScore,
                claims = _state.value.collectedClaims,
                ruleReport = currentReport
            )
            val seal = DigitalArrestPdfGenerator.computeEvidenceSeal(meta)

            _state.value = _state.value.copy(
                generatedPdfFile = file,
                evidenceSeal = seal
            )
            file
        } catch (e: Exception) {
            _state.value = _state.value.copy(errorMessage = e.message)
            null
        }
    }

    fun confirmSosDispatched() {
        _state.value = _state.value.copy(isSosDispatched = true)
    }

    fun reset() {
        _state.value = DigitalArrestState()
    }
}
