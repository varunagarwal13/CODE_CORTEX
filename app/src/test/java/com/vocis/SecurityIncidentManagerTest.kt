package com.vocis

import com.vocis.core.domain.model.IncidentStatus
import com.vocis.core.domain.model.IncidentType
import com.vocis.core.domain.model.RiskLevel
import com.vocis.intelligence.identity.CallerIdentity
import com.vocis.intelligence.identity.ReputationLevel
import com.vocis.intelligence.incident.SecurityIncidentManager
import com.vocis.intelligence.risk.EvidenceFactor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SecurityIncidentManagerTest {

    private lateinit var manager: SecurityIncidentManager

    @Before
    fun setUp() {
        manager = SecurityIncidentManager(dao = null)
    }

    @Test
    fun testInitialThreatReportCreatesIncident() = runBlocking {
        val identity = CallerIdentity(
            phoneNumber = "+919876543210",
            displayName = null,
            reputationLevel = ReputationLevel.UNKNOWN,
            isKnownContact = false,
            source = "mock"
        )
        val factors = listOf(
            EvidenceFactor("CALLER_REPUTATION", 15, "Unknown caller")
        )

        val incident = manager.reportThreat(
            riskScore = 35,
            riskLevel = RiskLevel.ELEVATED,
            incidentType = IncidentType.DIGITAL_ARREST,
            interactionId = "interaction-1",
            callerIdentity = identity,
            evidenceFactors = factors
        )

        assertNotNull(incident)
        assertEquals(IncidentType.DIGITAL_ARREST, incident.incidentType)
        assertEquals(IncidentStatus.NEW, incident.status)
        assertEquals(35, incident.riskScore)
        assertEquals(incident.incidentId, manager.activeIncident.value?.incidentId)
    }

    @Test
    fun testFifteenMinuteGroupingMergesThreats() = runBlocking {
        val identity = CallerIdentity(
            phoneNumber = "+919876543210",
            displayName = null,
            reputationLevel = ReputationLevel.UNKNOWN,
            isKnownContact = false,
            source = "mock"
        )

        // Event 1
        val incident1 = manager.reportThreat(
            riskScore = 35,
            riskLevel = RiskLevel.ELEVATED,
            incidentType = IncidentType.OTP_THEFT,
            interactionId = "interaction-1",
            callerIdentity = identity,
            evidenceFactors = listOf(EvidenceFactor("OTP", 40, "OTP received"))
        )

        // Event 2 (same type and caller) within 15 min
        val incident2 = manager.reportThreat(
            riskScore = 75,
            riskLevel = RiskLevel.HIGH,
            incidentType = IncidentType.OTP_THEFT,
            interactionId = "interaction-2",
            callerIdentity = identity,
            evidenceFactors = listOf(EvidenceFactor("CALL_ACTIVE", 35, "Call active"))
        )

        // Same incident ID merged
        assertEquals(incident1.incidentId, incident2.incidentId)
        assertEquals(75, incident2.riskScore)
        assertEquals(IncidentStatus.CONFIRMED, incident2.status)
        assertEquals("interaction-1,interaction-2", incident2.relatedInteractionIds)
    }

    @Test
    fun testResolveIncidentClearsActiveIncident() = runBlocking {
        val identity = CallerIdentity(
            phoneNumber = "+919876543210",
            displayName = null,
            reputationLevel = ReputationLevel.UNKNOWN,
            isKnownContact = false,
            source = "mock"
        )
        val incident = manager.reportThreat(
            riskScore = 80,
            riskLevel = RiskLevel.HIGH,
            incidentType = IncidentType.REMOTE_ACCESS_SCAM,
            interactionId = "interaction-1",
            callerIdentity = identity,
            evidenceFactors = listOf(EvidenceFactor("REMOTE_APP", 60, "AnyDesk running"))
        )

        assertEquals(incident.incidentId, manager.activeIncident.value?.incidentId)

        val resolved = manager.resolveIncident(incident.incidentId)
        assertNotNull(resolved)
        assertEquals(IncidentStatus.RESOLVED, resolved?.status)
        assertNull(manager.activeIncident.value)
    }
}
