package com.vocis

import com.vocis.core.domain.model.EventType
import com.vocis.core.domain.model.IncidentType
import com.vocis.core.domain.model.ProtectionAction
import com.vocis.core.domain.model.RiskLevel
import com.vocis.core.domain.model.SecurityEvent
import com.vocis.intelligence.context.AttackContextEngine
import com.vocis.intelligence.hub.InteractionHub
import com.vocis.intelligence.identity.CallerIdentity
import com.vocis.intelligence.identity.CallerIdentityResolver
import com.vocis.intelligence.identity.CallerReputation
import com.vocis.intelligence.identity.CallerReputationProvider
import com.vocis.intelligence.identity.ReputationLevel
import com.vocis.intelligence.incident.SecurityIncidentManager
import com.vocis.intelligence.policy.ProtectionPolicyEngine
import com.vocis.intelligence.risk.EvidenceFusionEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InteractionHubTest {

    private lateinit var hub: InteractionHub
    private lateinit var contextEngine: AttackContextEngine
    private lateinit var incidentManager: SecurityIncidentManager

    private val mockProvider = object : CallerReputationProvider {
        override suspend fun lookup(e164: String): CallerIdentity {
            return if (e164 == "+919999999999") {
                CallerIdentity(e164, null, ReputationLevel.HIGH_RISK, false, "PROVIDER", 50, 20)
            } else {
                CallerIdentity(e164, null, ReputationLevel.UNKNOWN, false, "PROVIDER", 0, 0)
            }
        }
    }

    @Before
    fun setUp() {
        val identityResolver = CallerIdentityResolver(
            context = null,
            dao = null,
            reputationProvider = mockProvider
        )
        contextEngine = AttackContextEngine(dao = null)
        incidentManager = SecurityIncidentManager(dao = null)
        hub = InteractionHub(
            db = null,
            identityResolver = identityResolver,
            contextEngine = contextEngine,
            fusionEngine = EvidenceFusionEngine(),
            policyEngine = ProtectionPolicyEngine(dao = null),
            incidentManager = incidentManager
        )
    }

    @Test
    fun testIncomingCallWithRecentOtpTriggersThreat() = runBlocking {
        val now = System.currentTimeMillis()

        // 1. Receive SMS with OTP
        val smsEvent = SecurityEvent(
            type = EventType.SMS_RECEIVED,
            source = "SmsSensor",
            timestamp = now,
            identity = "VK-BANK",
            metadata = "Your OTP is 884920. Do not share."
        )
        hub.processEvent(smsEvent)

        // 2. Incoming call from unknown caller 30 seconds later
        val callEvent = SecurityEvent(
            type = EventType.INCOMING_CALL,
            source = "CallScreeningSensor",
            timestamp = now + 30_000L,
            identity = "+919876543210"
        )
        val interaction = hub.processEvent(callEvent)

        // Score should be UNKNOWN(15) + OTP(40) = 55 -> HIGH
        assertTrue(interaction.riskLevel >= RiskLevel.HIGH)
        assertNotNull(hub.activeIncident.value)
        assertEquals(IncidentType.OTP_THEFT, hub.activeIncident.value?.incidentType)
        assertTrue(hub.interactions.value.isNotEmpty())
    }

    @Test
    fun testFailOpenOnUnexpectedException() = runBlocking {
        val faultyResolver = CallerIdentityResolver(
            context = null,
            dao = null,
            reputationProvider = object : CallerReputationProvider {
                override suspend fun lookup(e164: String): CallerIdentity {
                    throw RuntimeException("Simulated backend crash")
                }
            }
        )
        val safeHub = InteractionHub(
            db = null,
            identityResolver = faultyResolver,
            contextEngine = contextEngine
        )

        val callEvent = SecurityEvent(
            type = EventType.INCOMING_CALL,
            source = "CallScreeningSensor",
            identity = "+919876543210"
        )

        val interaction = safeHub.processEvent(callEvent)
        // Must fail-open and never block
        assertEquals(ProtectionAction.MONITOR_ONLY, interaction.protectionDecision)
        assertEquals(false, interaction.isBlocked)
    }

    @Test
    fun testVoiceCloneVerdictElevatesRisk() = runBlocking {
        hub.onVoiceCloneVerdict(isCritical = true)

        val callEvent = SecurityEvent(
            type = EventType.INCOMING_CALL,
            source = "CallScreeningSensor",
            identity = "+919876543210"
        )
        val interaction = hub.processEvent(callEvent)

        // UNKNOWN(15) + VOICE_CLONE(60) = 75 -> CRITICAL
        assertEquals(RiskLevel.CRITICAL, interaction.riskLevel)
        assertEquals(ProtectionAction.BLOCK_CALL, interaction.protectionDecision)
        assertTrue(interaction.isBlocked)
    }
}
