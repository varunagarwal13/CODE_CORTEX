package com.vocis.integration

import com.vocis.core.domain.model.EventType
import com.vocis.core.domain.model.RiskLevel
import com.vocis.core.domain.model.SecurityEvent
import com.vocis.digitalarrest.DigitalArrestController
import com.vocis.digitalarrest.DigitalArrestPdfGenerator
import com.vocis.digitalarrest.DigitalArrestPhase
import com.vocis.digitalarrest.DigitalArrestRules
import com.vocis.intelligence.context.AttackContextEngine
import com.vocis.intelligence.hub.InteractionHub
import com.vocis.intelligence.identity.CallerIdentityResolver
import com.vocis.intelligence.risk.EvidenceFusionEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.MessageDigest

class FullSystemEndToEndVerificationTest {

    private lateinit var identityResolver: CallerIdentityResolver
    private lateinit var contextEngine: AttackContextEngine
    private lateinit var fusionEngine: EvidenceFusionEngine
    private lateinit var interactionHub: InteractionHub
    private lateinit var digitalArrestController: DigitalArrestController

    @Before
    fun setUp() {
        identityResolver = CallerIdentityResolver()
        contextEngine = AttackContextEngine()
        fusionEngine = EvidenceFusionEngine()
        interactionHub = InteractionHub(
            identityResolver = identityResolver,
            contextEngine = contextEngine,
            fusionEngine = fusionEngine
        )
        digitalArrestController = DigitalArrestController()
    }

    @Test
    fun `test end to end scam detection to digital arrest guided recovery flow`() = runBlocking {
        val now = System.currentTimeMillis()

        // Step 1: Inbound call security event ingested by InteractionHub
        val incomingEvent = SecurityEvent(
            type = EventType.INCOMING_CALL,
            source = "CallScreeningSensor",
            timestamp = now,
            identity = "+919876543210",
            interactionId = "SESSION_CALL_E2E_01"
        )

        val interaction = interactionHub.processEvent(incomingEvent)

        val recordedInteractions = interactionHub.interactions.value
        assertTrue("InteractionHub must record the inbound call event", recordedInteractions.isNotEmpty())
        assertEquals("+919876543210", interaction.callerPhoneNumber)
        assertNotNull(interaction.riskLevel)

        // Step 2: Digital Arrest rule evaluation on extortion claims
        val claims = listOf(
            "I am DCP Cyber Crime Mumbai branch.",
            "You are under digital arrest; do not disconnect and stay on video call.",
            "Immediate payment required to RBI verification account to avoid jail."
        )

        val ruleReport = DigitalArrestRules.evaluate(
            callerClaims = claims,
            transcript = "DCP Cyber Crime Mumbai demands immediate RBI transfer to avoid warrant"
        )

        assertTrue("Digital arrest must be confirmed by deterministic rules", ruleReport.isDigitalArrestConfirmed)
        assertTrue("Violations count must be >= 3", ruleReport.totalViolations >= 3)

        // Step 3: Digital Arrest State Machine Navigation across 10 Phases
        val stateFlow = digitalArrestController.state
        assertEquals(DigitalArrestPhase.ALERT_DETECTED, stateFlow.value.currentPhase)

        val phases = DigitalArrestPhase.values()
        assertEquals(10, phases.size)

        for (i in 0 until phases.size - 1) {
            val next = digitalArrestController.nextPhase()
            assertEquals("State machine must step forward sequentially", phases[i + 1], next)
            assertEquals(phases[i + 1], stateFlow.value.currentPhase)
        }

        assertEquals(DigitalArrestPhase.DEFENSE_COMPLETED, stateFlow.value.currentPhase)

        // Step 4: Cryptographic Forensic Evidence Sealing & Chain of Custody
        val metadata = DigitalArrestPdfGenerator.PdfEvidenceMetadata(
            incidentId = "INCIDENT_E2E_01",
            timestamp = now,
            callerPhoneNumber = "+919876543210",
            callerName = "Claimed Police Officer",
            riskScore = 95,
            claims = claims,
            ruleReport = ruleReport
        )

        val sha256Seal = DigitalArrestPdfGenerator.computeEvidenceSeal(metadata)

        assertNotNull("SHA-256 seal must not be null", sha256Seal)
        assertEquals("SHA-256 seal must be exactly 64 hexadecimal characters", 64, sha256Seal.length)

        // Verify cryptographic integrity / tamper-evident property
        val tamperedMetadata = metadata.copy(riskScore = 96)
        val tamperedSeal = DigitalArrestPdfGenerator.computeEvidenceSeal(tamperedMetadata)
        assertNotEquals("Tampered evidence payload must produce completely distinct hash", sha256Seal, tamperedSeal)
    }
}
