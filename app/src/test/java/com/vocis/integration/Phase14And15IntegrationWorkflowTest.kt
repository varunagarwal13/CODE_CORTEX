package com.vocis.integration

import android.content.Context
import com.vocis.core.data.dao.FamilyContactDao
import com.vocis.core.data.entity.FamilyContactEntity
import com.vocis.core.data.entity.SecurityIncidentEntity
import com.vocis.core.domain.model.EventType
import com.vocis.core.domain.model.IncidentStatus
import com.vocis.core.domain.model.IncidentType
import com.vocis.core.domain.model.RiskLevel
import com.vocis.core.domain.model.SecurityEvent
import com.vocis.digitalarrest.DigitalArrestController
import com.vocis.digitalarrest.DigitalArrestPdfGenerator
import com.vocis.digitalarrest.DigitalArrestPhase
import com.vocis.digitalarrest.DigitalArrestRules
import com.vocis.emergency.EmergencyAlarmSystem
import com.vocis.emergency.EmergencySmsKeywordMatcher
import com.vocis.emergency.FamilyAlertDispatcher
import com.vocis.emergency.NativeSmsSender
import com.vocis.emergency.TextBeeSmsGateway
import com.vocis.intelligence.context.AttackContextEngine
import com.vocis.intelligence.hub.InteractionHub
import com.vocis.intelligence.identity.CallerIdentity
import com.vocis.intelligence.identity.CallerIdentityResolver
import com.vocis.intelligence.identity.CallerReputationProvider
import com.vocis.intelligence.identity.ReputationLevel
import com.vocis.intelligence.incident.SecurityIncidentManager
import com.vocis.intelligence.policy.ProtectionPolicyEngine
import com.vocis.intelligence.risk.EvidenceFusionEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class Phase14And15IntegrationWorkflowTest {

    private lateinit var mockTextBeeGateway: TestTextBeeGateway
    private lateinit var mockNativeSmsSender: TestNativeSmsSender
    private lateinit var mockContactDao: FamilyContactDao
    private lateinit var familyAlertDispatcher: FamilyAlertDispatcher
    private lateinit var interactionHub: InteractionHub
    private lateinit var contextEngine: AttackContextEngine
    private lateinit var incidentManager: SecurityIncidentManager

    private val testContacts = listOf(
        FamilyContactEntity(
            phoneNumber = "+919888877771",
            name = "Mom",
            relationship = "Parent",
            isEmergencyAlertEnabled = true
        ),
        FamilyContactEntity(
            phoneNumber = "+919888877772",
            name = "Brother",
            relationship = "Sibling",
            isEmergencyAlertEnabled = true
        )
    )

    class TestTextBeeGateway(var shouldSucceed: Boolean = true) : TextBeeSmsGateway {
        val sentMessages = mutableListOf<Pair<List<String>, String>>()
        override suspend fun sendSms(recipients: List<String>, message: String): Boolean {
            if (shouldSucceed) {
                sentMessages.add(recipients to message)
                return true
            }
            return false
        }
    }

    class TestNativeSmsSender(var shouldSucceed: Boolean = true) : NativeSmsSender {
        val sentMessages = mutableListOf<Pair<String, String>>()
        override fun sendTextMessage(destination: String, text: String): Boolean {
            if (shouldSucceed) {
                sentMessages.add(destination to text)
                return true
            }
            return false
        }
    }

    @Before
    fun setUp() = runBlocking {
        mockTextBeeGateway = TestTextBeeGateway(shouldSucceed = true)
        mockNativeSmsSender = TestNativeSmsSender(shouldSucceed = true)
        mockContactDao = mock(FamilyContactDao::class.java)
        `when`(mockContactDao.getAll()).thenReturn(testContacts)

        familyAlertDispatcher = FamilyAlertDispatcher(
            contactDao = mockContactDao,
            textBeeGateway = mockTextBeeGateway,
            nativeSmsSender = mockNativeSmsSender
        )

        val reputationProvider = object : CallerReputationProvider {
            override suspend fun lookup(e164: String): CallerIdentity {
                return if (e164 == "+919999900001") {
                    CallerIdentity(e164, "Extortion Caller", ReputationLevel.HIGH_RISK, false, "TEST", 60, 30)
                } else {
                    CallerIdentity(e164, null, ReputationLevel.UNKNOWN, false, "TEST", 0, 0)
                }
            }
        }

        val identityResolver = CallerIdentityResolver(
            context = null,
            dao = null,
            reputationProvider = reputationProvider
        )

        contextEngine = AttackContextEngine(dao = null)
        incidentManager = SecurityIncidentManager(dao = null)

        interactionHub = InteractionHub(
            db = null,
            identityResolver = identityResolver,
            contextEngine = contextEngine,
            fusionEngine = EvidenceFusionEngine(),
            policyEngine = ProtectionPolicyEngine(dao = null),
            incidentManager = incidentManager,
            familyAlertDispatcher = familyAlertDispatcher
        )
    }

    // =========================================================================
    // WORKFLOW 1: End-to-End Emergency SOS Alerting & Alarm Workflow (Phase 14)
    // =========================================================================
    @Test
    fun testWorkflow1_EmergencySosDispatch_FromHighThreatCall() = runBlocking {
        val now = System.currentTimeMillis()

        // 1. Simulate inbound banking OTP SMS
        val smsEvent = SecurityEvent(
            type = EventType.SMS_RECEIVED,
            source = "SmsSensor",
            timestamp = now,
            identity = "VK-HDFCBK",
            metadata = "Your OTP for Rs 49,999 transfer is 739201. Never share your OTP."
        )
        interactionHub.processEvent(smsEvent)

        // 2. High risk incoming call arrives within attack window
        val callEvent = SecurityEvent(
            type = EventType.INCOMING_CALL,
            source = "CallScreeningSensor",
            timestamp = now + 15_000L,
            identity = "+919999900001",
            interactionId = "SESSION_CALL_001"
        )
        val interaction = interactionHub.processEvent(callEvent)

        // 3. Verify Threat Escalation
        assertTrue("Risk level should be at least HIGH", interaction.riskLevel >= RiskLevel.HIGH)
        assertNotNull("Active incident should be created", interactionHub.activeIncident.value)

        // Give InteractionHub's launched coroutine a moment to complete dispatch
        kotlinx.coroutines.delay(300)

        // 4. Verify Family Alert Dispatcher was automatically triggered by InteractionHub
        for ((_, msg) in mockTextBeeGateway.sentMessages) {
            println("CAPTURED_DISPATCH_MESSAGE: $msg")
        }
        assertEquals("TextBee should receive alert from InteractionHub", 1, mockTextBeeGateway.sentMessages.size)

        val (recipients, message) = mockTextBeeGateway.sentMessages[0]
        assertEquals(2, recipients.size)
        assertTrue(recipients.contains("+919888877771"))
        assertTrue(recipients.contains("+919888877772"))
        assertTrue(message.contains("[VOCIS EMERGENCY ALERT]"))

        // 5. Test idempotency lock: A duplicate call for the same session must be suppressed
        val duplicateSent = familyAlertDispatcher.sendAlert(
            riskScore = 90,
            incidentType = IncidentType.OTP_THEFT,
            callerNumber = "+919999900001",
            interactionId = "SESSION_CALL_001"
        )
        assertFalse("Duplicate alert for same session must be suppressed", duplicateSent)

        // 6. Test fallback to Native SMS if TextBee gateway encounters outage
        familyAlertDispatcher.clearSessionHistory()
        mockTextBeeGateway.shouldSucceed = false

        val fallbackSent = familyAlertDispatcher.sendAlert(
            riskScore = 75,
            incidentType = IncidentType.OTP_THEFT,
            callerNumber = "+919999900001",
            interactionId = "SESSION_CALL_002"
        )
        assertTrue("Fallback dispatch should succeed", fallbackSent)
        assertEquals("Native SMS sender should receive 2 messages", 2, mockNativeSmsSender.sentMessages.size)
    }

    // =========================================================================
    // WORKFLOW 2: Emergency Alarm Audio Synthesis & Audio Lock (Phase 14)
    // =========================================================================
    @Test
    fun testWorkflow2_EmergencyAlarmAcousticSirenGeneration() {
        val sampleRate = 16000
        val durationSeconds = 1
        val buffer = EmergencyAlarmSystem.generateSirenPcm(sampleRate, durationSeconds)

        val totalSamples = sampleRate * durationSeconds
        assertEquals(totalSamples, buffer.size)

        // Verify audio waveform dynamics: values must oscillate and peak
        var hasPositiveAmplitude = false
        var hasNegativeAmplitude = false
        var zeroCrossings = 0

        for (i in 0 until buffer.size - 1) {
            val sample = buffer[i].toInt()
            val nextSample = buffer[i + 1].toInt()
            if (sample > 10000) hasPositiveAmplitude = true
            if (sample < -10000) hasNegativeAmplitude = true
            if ((sample >= 0 && nextSample < 0) || (sample < 0 && nextSample >= 0)) {
                zeroCrossings++
            }
        }

        assertTrue("Siren buffer must have strong positive peaks", hasPositiveAmplitude)
        assertTrue("Siren buffer must have strong negative peaks", hasNegativeAmplitude)
        assertTrue("Zero crossings should reflect high audible frequency ($zeroCrossings)", zeroCrossings > 1000)

        // Verify volume lock constant calculation
        val targetVolumeFraction = EmergencyAlarmSystem.TARGET_VOLUME_RATIO
        assertEquals(0.85f, targetVolumeFraction, 0.001f)
    }

    // =========================================================================
    // WORKFLOW 3: Emergency SMS Inbound Keyword Matching Workflow (Phase 14)
    // =========================================================================
    @Test
    fun testWorkflow3_EmergencySmsKeywordMatcher_TriggerWorkflow() = runBlocking {
        val matcher = EmergencySmsKeywordMatcher(contactDao = mockContactDao)

        // 1. Authorized contact sends emergency keyword VOCIS
        val authorizedSender = "+919888877771"
        val isAuthorized = matcher.isAuthorizedSender(authorizedSender)
        assertTrue("Registered family member must be authorized", isAuthorized)

        val matchesKeyword = matcher.containsKeyword("URGENT VOCIS HELP ME")
        assertTrue("Keyword VOCIS must match case-insensitively", matchesKeyword)

        // 2. Body without keyword must not trigger
        assertFalse(matcher.containsKeyword("Hello are you home?"))

        // 3. Sender authorization check for unknown number when DB has contacts
        val unauthorizedSender = "+911234567890"
        val isUnauthorized = matcher.isAuthorizedSender(unauthorizedSender)
        assertFalse("Unregistered sender must be rejected", isUnauthorized)
    }

    // =========================================================================
    // WORKFLOW 4: Digital Arrest Threat Detection & Forensic Analysis (Phase 15)
    // =========================================================================
    @Test
    fun testWorkflow4_DigitalArrestRules_DeterministicEvaluation() {
        val realWorldScamClaims = listOf(
            "This is Inspector Sharma from Mumbai Police and CBI Cyber Crime branch.",
            "Stay on video, lock the door, do not disconnect camera.",
            "Transfer money immediately to RBI verification account for court escrow.",
            "Sent instant summons and digital arrest warrant on WhatsApp.",
            "Face immediate arrest and police raid within 1 hour."
        )

        val report = DigitalArrestRules.evaluate(
            callerClaims = realWorldScamClaims,
            transcript = realWorldScamClaims.joinToString(" ")
        )

        assertEquals("All 5 rules must be violated", 5, report.totalViolations)
        assertTrue("Digital arrest must be confirmed", report.isDigitalArrestConfirmed)

        val rule01 = report.rules.first { it.ruleId == "RULE_01" }
        val rule02 = report.rules.first { it.ruleId == "RULE_02" }
        val rule03 = report.rules.first { it.ruleId == "RULE_03" }
        val rule04 = report.rules.first { it.ruleId == "RULE_04" }
        val rule05 = report.rules.first { it.ruleId == "RULE_05" }

        assertTrue("RULE_01 must be violated", rule01.isViolated)
        assertTrue("RULE_02 must be violated", rule02.isViolated)
        assertTrue("RULE_03 must be violated", rule03.isViolated)
        assertTrue("RULE_04 must be violated", rule04.isViolated)
        assertTrue("RULE_05 must be violated", rule05.isViolated)
    }

    // =========================================================================
    // WORKFLOW 5: 10-Phase Guided Defense Controller State Machine (Phase 15)
    // =========================================================================
    @Test
    fun testWorkflow5_DigitalArrest10PhaseGuidedWorkflow() = runBlocking {
        val controller = DigitalArrestController(familyAlertDispatcher = familyAlertDispatcher)

        val incident = SecurityIncidentEntity(
            incidentId = "INC_DA_007",
            incidentType = IncidentType.DIGITAL_ARREST,
            severity = RiskLevel.CRITICAL,
            status = IncidentStatus.CONFIRMED,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            resolvedAt = null,
            riskScore = 95,
            explanation = "Demanding video Skype arrest and fund transfer",
            recommendedActions = "Disconnect immediately",
            relatedInteractionIds = "INT_001",
            callerPhoneNumber = "+919876543210",
            callerDisplayName = "Fake Police"
        )

        // 1. Trigger state machine
        val context = mock(Context::class.java)
        controller.simulateTrigger(context, incident)

        assertEquals(DigitalArrestPhase.ALERT_DETECTED, controller.state.value.currentPhase)
        assertEquals(1, controller.state.value.phaseIndex)

        // 2. Step through all 10 phases sequentially
        val expectedPhases = listOf(
            DigitalArrestPhase.CALL_ISOLATION_ASSESSMENT,
            DigitalArrestPhase.AUTHORITY_CLAIM_VERIFICATION,
            DigitalArrestPhase.LEGAL_PROCEDURE_EDUCATION,
            DigitalArrestPhase.FINANCIAL_COERCION_CHECK,
            DigitalArrestPhase.EVIDENCE_COLLECTION,
            DigitalArrestPhase.RULE_EVALUATION,
            DigitalArrestPhase.FAMILY_SOS_CONFIRMATION,
            DigitalArrestPhase.FORENSIC_REPORT_GENERATION,
            DigitalArrestPhase.DEFENSE_COMPLETED
        )

        for (expected in expectedPhases) {
            val next = controller.nextPhase()
            assertEquals(expected, next)
            assertEquals(expected, controller.state.value.currentPhase)
        }

        assertEquals(DigitalArrestPhase.DEFENSE_COMPLETED, controller.state.value.currentPhase)
        assertEquals(10, controller.state.value.phaseIndex)

        // 3. Confirm SOS dispatch flag
        controller.confirmSosDispatched()
        assertTrue("SOS dispatch flag should be confirmed", controller.state.value.isSosDispatched)

        // 4. Test state reset
        controller.reset()
        assertEquals(DigitalArrestPhase.ALERT_DETECTED, controller.state.value.currentPhase)
        assertEquals(1, controller.state.value.phaseIndex)
    }

    // =========================================================================
    // WORKFLOW 6: Cryptographic PDF Sealing & Tamper Verification (Phase 15)
    // =========================================================================
    @Test
    fun testWorkflow6_CryptographicPdfEvidenceSealingAndVerification() {
        val claims = listOf(
            "Claiming to be DCP Cyber Crime Mumbai",
            "Demanding payment to avoid immediate non-bailable arrest"
        )

        val ruleReport = DigitalArrestRules.evaluate(callerClaims = claims)

        val metadata = DigitalArrestPdfGenerator.PdfEvidenceMetadata(
            incidentId = "INC_CRIME_999",
            timestamp = 1716000000000L,
            callerPhoneNumber = "+919876543210",
            callerName = "Fraudster",
            riskScore = 95,
            claims = claims,
            ruleReport = ruleReport,
            osFingerprint = "Android 14 Test Device"
        )

        // 1. Compute SHA-256 seal
        val seal = DigitalArrestPdfGenerator.computeEvidenceSeal(metadata)
        assertNotNull(seal)
        assertEquals("SHA-256 seal must be 64-character hexadecimal", 64, seal.length)
        assertTrue("Must be hexadecimal", seal.matches(Regex("^[0-9a-f]{64}$")))

        // 2. Determinism check: Same metadata must yield identical seal
        val duplicateSeal = DigitalArrestPdfGenerator.computeEvidenceSeal(metadata)
        assertEquals("Seal must be deterministic", seal, duplicateSeal)

        // 3. Tamper detection: Modifying caller number must alter seal
        val tamperedCaller = metadata.copy(callerPhoneNumber = "+910000000000")
        val tamperedCallerSeal = DigitalArrestPdfGenerator.computeEvidenceSeal(tamperedCaller)
        assertNotEquals("Tampered caller number must alter seal", seal, tamperedCallerSeal)

        // 4. Tamper detection: Modifying risk score must alter seal
        val tamperedScore = metadata.copy(riskScore = 20)
        val tamperedScoreSeal = DigitalArrestPdfGenerator.computeEvidenceSeal(tamperedScore)
        assertNotEquals("Tampered risk score must alter seal", seal, tamperedScoreSeal)

        // 5. Verification check
        val isAuthentic = (DigitalArrestPdfGenerator.computeEvidenceSeal(metadata) == seal)
        val isTamperedRejected = (DigitalArrestPdfGenerator.computeEvidenceSeal(tamperedScore) != seal)
        assertTrue("Authentic evidence must verify", isAuthentic)
        assertTrue("Tampered evidence must be rejected", isTamperedRejected)
    }

    // =========================================================================
    // WORKFLOW 7: Integrated Cross-Phase Pipeline (Phase 14 + Phase 15)
    // =========================================================================
    @Test
    fun testWorkflow7_EndToEndCrossPhaseDefensePipeline() = runBlocking {
        // Step A: Victim receives institutional coercion call
        val callerNumber = "+919999900001"
        val callClaims = listOf(
            "I am from CBI Cyber Branch",
            "Transfer money to avoid non-bailable digital arrest"
        )

        // Step B: Phase 15 Rule Engine identifies Digital Arrest pattern
        val ruleReport = DigitalArrestRules.evaluate(callerClaims = callClaims)
        val rule01 = ruleReport.rules.first { it.ruleId == "RULE_01" }
        assertTrue("RULE_01 must be violated", rule01.isViolated)

        // Step C: Interaction Hub processes event and triggers Phase 14 Family Alert
        val alertDispatched = familyAlertDispatcher.sendAlert(
            riskScore = 95,
            incidentType = IncidentType.DIGITAL_ARREST,
            callerNumber = callerNumber,
            interactionId = "SESSION_DA_COMBINED"
        )
        assertTrue("Emergency alert must dispatch for high confidence Digital Arrest", alertDispatched)

        // Step D: Phase 15 Controller executes guided defense
        val controller = DigitalArrestController(familyAlertDispatcher = familyAlertDispatcher)
        val mockIncident = SecurityIncidentEntity(
            incidentId = "INC_COMBINED_01",
            incidentType = IncidentType.DIGITAL_ARREST,
            severity = RiskLevel.CRITICAL,
            status = IncidentStatus.CONFIRMED,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            resolvedAt = null,
            riskScore = 95,
            explanation = "CBI impersonation and digital arrest warrant extortion",
            recommendedActions = "Disconnect call. Dial 1930.",
            relatedInteractionIds = "SESSION_DA_COMBINED",
            callerPhoneNumber = callerNumber,
            callerDisplayName = "Purported CBI"
        )

        controller.simulateTrigger(mock(Context::class.java), mockIncident)
        assertEquals(DigitalArrestPhase.ALERT_DETECTED, controller.state.value.currentPhase)

        // Step through all phases to DEFENSE_COMPLETED
        for (i in 1..9) {
            controller.nextPhase()
        }
        assertEquals(DigitalArrestPhase.DEFENSE_COMPLETED, controller.state.value.currentPhase)

        // Step E: Verify Cryptographic Evidence Sealing in metadata
        val metadata = DigitalArrestPdfGenerator.PdfEvidenceMetadata(
            incidentId = mockIncident.incidentId,
            timestamp = mockIncident.createdAt,
            callerPhoneNumber = mockIncident.callerPhoneNumber,
            callerName = mockIncident.callerDisplayName,
            riskScore = mockIncident.riskScore,
            claims = callClaims,
            ruleReport = ruleReport,
            osFingerprint = "Android 14 Test Device"
        )
        val seal = DigitalArrestPdfGenerator.computeEvidenceSeal(metadata)
        assertEquals(64, seal.length)
        assertEquals("Seal must be deterministic and authentic",
            seal,
            DigitalArrestPdfGenerator.computeEvidenceSeal(metadata)
        )
    }
}
