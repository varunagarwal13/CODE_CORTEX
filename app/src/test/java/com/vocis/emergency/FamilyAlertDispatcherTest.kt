package com.vocis.emergency

import com.vocis.core.domain.model.IncidentType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FamilyAlertDispatcherTest {

    private class FakeTextBeeGateway(var succeed: Boolean = true) : TextBeeSmsGateway {
        var callCount = 0
        var lastRecipients: List<String> = emptyList()
        var lastMessage: String = ""

        override suspend fun sendSms(recipients: List<String>, message: String): Boolean {
            callCount++
            lastRecipients = recipients
            lastMessage = message
            return succeed
        }
    }

    private class FakeNativeSmsSender(var succeed: Boolean = true) : NativeSmsSender {
        var callCount = 0
        val sentMessages = mutableListOf<Pair<String, String>>()

        override fun sendTextMessage(destination: String, text: String): Boolean {
            callCount++
            sentMessages.add(destination to text)
            return succeed
        }
    }

    private lateinit var fakeTextBee: FakeTextBeeGateway
    private lateinit var fakeNativeSms: FakeNativeSmsSender
    private lateinit var dispatcher: FamilyAlertDispatcher
    private val testEmergencyNumbers = listOf("+919876543210", "+919876543211")

    @Before
    fun setUp() {
        fakeTextBee = FakeTextBeeGateway(succeed = true)
        fakeNativeSms = FakeNativeSmsSender(succeed = true)
        dispatcher = FamilyAlertDispatcher(
            contactDao = null,
            textBeeGateway = fakeTextBee,
            nativeSmsSender = fakeNativeSms,
            defaultEmergencyRecipients = testEmergencyNumbers
        )
    }

    @Test
    fun `test riskScore equal to 50 does not dispatch alert`() = runBlocking {
        val result = dispatcher.sendAlert(
            riskScore = 50,
            incidentType = IncidentType.DIGITAL_ARREST,
            callerNumber = "+911234567890",
            interactionId = "session_50"
        )

        assertFalse("Score of 50 must be suppressed", result)
        assertEquals(0, fakeTextBee.callCount)
        assertEquals(0, fakeNativeSms.callCount)
    }

    @Test
    fun `test riskScore equal to 49 does not dispatch alert`() = runBlocking {
        val result = dispatcher.sendAlert(
            riskScore = 49,
            incidentType = IncidentType.OTP_THEFT,
            callerNumber = "+911234567890",
            interactionId = "session_49"
        )

        assertFalse("Score under 50 must be suppressed", result)
        assertEquals(0, fakeTextBee.callCount)
    }

    @Test
    fun `test riskScore of 51 strictly dispatches emergency alert`() = runBlocking {
        val result = dispatcher.sendAlert(
            riskScore = 51,
            incidentType = IncidentType.DIGITAL_ARREST,
            callerNumber = "+911234567890",
            interactionId = "session_51"
        )

        assertTrue("Score of 51 must trigger dispatch", result)
        assertEquals(1, fakeTextBee.callCount)
        assertEquals(testEmergencyNumbers, fakeTextBee.lastRecipients)
        assertTrue(fakeTextBee.lastMessage.contains("DIGITAL_ARREST"))
        assertTrue(fakeTextBee.lastMessage.contains("51/100"))
    }

    @Test
    fun `test idempotency lock prevents duplicate SMS for same call session`() = runBlocking {
        val firstResult = dispatcher.sendAlert(
            riskScore = 80,
            incidentType = IncidentType.FINANCIAL_FRAUD,
            callerNumber = "+911234567890",
            interactionId = "session_unique_id"
        )
        assertTrue("First attempt must succeed", firstResult)
        assertEquals(1, fakeTextBee.callCount)

        // Second dispatch with identical interactionId
        val secondResult = dispatcher.sendAlert(
            riskScore = 95,
            incidentType = IncidentType.FINANCIAL_FRAUD,
            callerNumber = "+911234567890",
            interactionId = "session_unique_id"
        )
        assertFalse("Second attempt with same session id must be blocked by idempotency lock", secondResult)
        assertEquals(1, fakeTextBee.callCount)
    }

    @Test
    fun `test global 5-minute cooldown suppresses rapid successive alerts`() = runBlocking {
        val firstResult = dispatcher.sendAlert(
            riskScore = 75,
            incidentType = IncidentType.REMOTE_ACCESS_SCAM,
            callerNumber = "+911234567890",
            interactionId = "session_first"
        )
        assertTrue(firstResult)
        assertEquals(1, fakeTextBee.callCount)

        // Different interaction ID, but within 5 minutes cooldown
        val secondResult = dispatcher.sendAlert(
            riskScore = 90,
            incidentType = IncidentType.OTP_THEFT,
            callerNumber = "+919999999999",
            interactionId = "session_second"
        )
        assertFalse("Second attempt within cooldown window must be suppressed", secondResult)
        assertEquals(1, fakeTextBee.callCount)
    }

    @Test
    fun `test fallback to native SMS when TextBee gateway fails`() = runBlocking {
        fakeTextBee.succeed = false

        val result = dispatcher.sendAlert(
            riskScore = 85,
            incidentType = IncidentType.VOICE_CLONE,
            callerNumber = "+911234567890",
            interactionId = "session_fallback"
        )

        assertTrue("Fallback to native SMS must succeed", result)
        assertEquals(1, fakeTextBee.callCount)
        assertEquals(2, fakeNativeSms.callCount)
        assertEquals(testEmergencyNumbers.size, fakeNativeSms.sentMessages.size)
    }
}
