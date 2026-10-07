package com.vocis

import com.vocis.core.domain.model.EventType
import com.vocis.core.domain.model.SecurityEvent
import com.vocis.intelligence.context.AttackContextEngine
import com.vocis.intelligence.linguistic.NotificationSignals
import com.vocis.intelligence.linguistic.SmsSignals
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AttackContextEngineTest {

    private lateinit var engine: AttackContextEngine

    @Before
    fun setUp() {
        engine = AttackContextEngine(dao = null)
    }

    @Test
    fun testOtpWithin5MinuteWindow() = runBlocking {
        val now = 1000000L
        val smsEvent = SecurityEvent(
            type = EventType.SMS_RECEIVED,
            source = "SmsSensor",
            timestamp = now,
            identity = "VK-HDFCBK"
        )
        val signals = SmsSignals(
            hasOtp = true,
            urgencyKeywords = listOf("urgent"),
            phishingLinks = emptyList(),
            isEmergency = false
        )

        engine.onSmsReceived(smsEvent, signals)

        // At now + 2 minutes (120,000 ms), OTP should still be active and within window
        val contextWithinWindow = engine.getActiveContext(now + 120_000L)
        assertTrue(contextWithinWindow.hasActiveOtp)
        assertTrue(contextWithinWindow.otpWithinWindow)

        // At now + 6 minutes (360,000 ms), OTP is outside window
        val contextExpired = engine.getActiveContext(now + 360_000L)
        assertTrue(contextExpired.hasActiveOtp)
        assertFalse(contextExpired.otpWithinWindow)
    }

    @Test
    fun testCallLifecyclePreservesOtp() = runBlocking {
        val now = 1000000L
        val smsEvent = SecurityEvent(
            type = EventType.SMS_RECEIVED,
            source = "SmsSensor",
            timestamp = now,
            identity = "VK-SBI"
        )
        engine.onSmsReceived(smsEvent, SmsSignals(hasOtp = true, emptyList(), emptyList(), false))

        val callEvent = SecurityEvent(
            type = EventType.INCOMING_CALL,
            source = "CallScreeningSensor",
            timestamp = now + 10_000L,
            identity = "+919876543210"
        )
        engine.onCallStarted(callEvent)

        val activeContext = engine.getActiveContext(now + 15_000L)
        assertEquals("+919876543210", activeContext.activeCallPhoneNumber)
        assertTrue(activeContext.otpWithinWindow)

        engine.onCallEnded()

        val postCallContext = engine.getActiveContext(now + 20_000L)
        assertNull(postCallContext.activeCallPhoneNumber)
        // OTP still within 5 min window
        assertTrue(postCallContext.otpWithinWindow)
    }

    @Test
    fun testRemoteDesktopDetection() = runBlocking {
        val notifEvent = SecurityEvent(
            type = EventType.NOTIFICATION_POSTED,
            source = "NotificationSensor",
            identity = "com.anydesk.anydeskandroid"
        )
        val notifSignals = NotificationSignals(
            isVoipCall = false,
            isRemoteDesktop = true,
            financialSignals = emptyList()
        )

        engine.onNotificationEvent(notifEvent, notifSignals)
        val context = engine.getActiveContext()
        assertTrue(context.remoteDesktopActive)
    }

    @Test
    fun testPhishingLinkDetection() = runBlocking {
        val smsEvent = SecurityEvent(
            type = EventType.SMS_RECEIVED,
            source = "SmsSensor",
            identity = "+919876500000"
        )
        val signals = SmsSignals(
            hasOtp = false,
            urgencyKeywords = emptyList(),
            phishingLinks = listOf("http://fake-kyc.com"),
            isEmergency = false
        )

        engine.onSmsReceived(smsEvent, signals)
        val context = engine.getActiveContext()
        assertTrue(context.phishingLinkDetected)
    }

    @Test
    fun testSmsFullTextThreatSignals() = runBlocking {
        // Government / Digital arrest threat
        val cbiSms = SecurityEvent(
            type = EventType.SMS_RECEIVED,
            source = "SmsSensor",
            timestamp = 1000L,
            identity = "+919999911111",
            metadata = "CBI Digital Arrest Warrant: FIR 2049 issued. Your account is frozen. Contact 9876543210 immediately."
        )
        val context = engine.onSmsReceived(cbiSms, text = cbiSms.metadata, sender = cbiSms.identity)
        assertTrue(context.hasAuthorityThreat)
        assertEquals("+919876543210", context.callbackNumber)

        // Courier / parcel threat
        val parcelSms = SecurityEvent(
            type = EventType.SMS_RECEIVED,
            source = "SmsSensor",
            timestamp = 2000L,
            identity = "VK-DHL",
            metadata = "DHL Express: Your package customs clearance detained due to illegal narcotics."
        )
        val parcelContext = engine.onSmsReceived(parcelSms, text = parcelSms.metadata, sender = parcelSms.identity)
        assertTrue(parcelContext.hasDeliveryThreat)
    }

    @Test
    fun testNotificationFinancialAndOtpSignals() = runBlocking {
        val notifEvent = SecurityEvent(
            type = EventType.NOTIFICATION_POSTED,
            source = "NotificationSensor",
            timestamp = 5000L,
            identity = "com.google.android.apps.nbu.paisa.user",
            metadata = "Google Pay: Your account was debited INR 25,000 via UPI. OTP 739210 used for auth."
        )
        val notifSignals = NotificationSignals(
            isVoipCall = false,
            isRemoteDesktop = false,
            financialSignals = listOf("debited", "inr", "upi")
        )

        val context = engine.onNotificationEvent(notifEvent, notifSignals)
        assertTrue(context.hasFinancialThreat)
        assertTrue(context.hasActiveOtp)
    }

    @Test
    fun testOutgoingCallCallbackMismatch() = runBlocking {
        // Step 1: SMS arrives with scam callback number
        val sms = SecurityEvent(
            type = EventType.SMS_RECEIVED,
            source = "SmsSensor",
            timestamp = 1000L,
            identity = "VK-SBI",
            metadata = "SBI Alert: Electricity service will be disconnected tonight. Call agent at 9876543210 immediately."
        )
        engine.onSmsReceived(sms, text = sms.metadata, sender = sms.identity)

        // Step 2: Victim dials the advertised callback number
        val outgoingCall = SecurityEvent(
            type = EventType.OUTGOING_CALL,
            source = "OutgoingCallMonitor",
            timestamp = 2000L,
            identity = "+919876543210"
        )
        val context = engine.onOutgoingCall(outgoingCall)

        // Step 3: Verify callback mismatch is flagged
        assertTrue(context.hasCallbackMismatch)
    }
}
