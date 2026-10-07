package com.vocis

import com.vocis.intelligence.linguistic.LocalScamClassifier
import com.vocis.intelligence.linguistic.NotificationSignalExtractor
import com.vocis.intelligence.linguistic.SmsSignalExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinguisticAnalyzerTest {

    @Test
    fun testOtpExtraction() {
        assertTrue(SmsSignalExtractor.extractOtpPresence("Your bank OTP is 4821. Do not share."))
        assertTrue(SmsSignalExtractor.extractOtpPresence("Secret code: 938102 for account verification"))
        assertTrue(SmsSignalExtractor.extractOtpPresence("Use one time password 556677"))
        assertFalse(SmsSignalExtractor.extractOtpPresence("Call me later at 9876543210"))
        assertFalse(SmsSignalExtractor.extractOtpPresence("Meeting at 5 pm"))
    }

    @Test
    fun testUrgencyExtraction() {
        val signals = SmsSignalExtractor.extractUrgencySignals("Your electricity will be disconnected immediately! KYC suspended.")
        assertTrue(signals.contains("electricity"))
        assertTrue(signals.contains("disconnected"))
        assertTrue(signals.contains("immediately"))
        assertTrue(signals.contains("suspended"))
        assertTrue(signals.contains("kyc"))
    }

    @Test
    fun testPhishingLinkExtraction() {
        val text = "Click here: https://secure-bank-update.xyz/login or http://192.168.1.1/pay to update."
        val links = SmsSignalExtractor.extractPhishingLinks(text)
        assertEquals(2, links.size)
        assertTrue(links.any { it.contains("secure-bank-update.xyz") })
        assertTrue(links.any { it.contains("192.168.1.1") })
    }

    @Test
    fun testEmergencyKeyword() {
        assertTrue(SmsSignalExtractor.isEmergencyKeyword("Emergency alert VOCIS activate safe mode"))
        assertTrue(SmsSignalExtractor.isEmergencyKeyword("vocis help"))
        assertFalse(SmsSignalExtractor.isEmergencyKeyword("Just checking in"))
    }

    @Test
    fun testVoipCallNotificationDetection() {
        assertTrue(
            NotificationSignalExtractor.isVoipCallNotification(
                category = "call",
                actions = listOf("Decline", "Answer")
            )
        )
        assertFalse(
            NotificationSignalExtractor.isVoipCallNotification(
                category = "social",
                actions = listOf("Reply", "Mark as read")
            )
        )
    }

    @Test
    fun testRemoteDesktopDetection() {
        assertTrue(NotificationSignalExtractor.isRemoteDesktopActive("com.anydesk.anydeskandroid"))
        assertTrue(NotificationSignalExtractor.isRemoteDesktopActive("com.teamviewer.teamviewer.market.mobile"))
        assertFalse(NotificationSignalExtractor.isRemoteDesktopActive("com.whatsapp"))
    }

    @Test
    fun testFinancialSignalExtraction() {
        val signals = NotificationSignalExtractor.extractFinancialSignals(
            title = "Bank Alert",
            text = "INR 25,000 debited from account via UPI transaction"
        )
        assertTrue(signals.contains("debited"))
        assertTrue(signals.contains("inr"))
        assertTrue(signals.contains("upi"))
        assertTrue(signals.contains("transaction"))
    }

    @Test
    fun testLocalScamClassifierDigitalArrest() {
        val result = LocalScamClassifier.classify("This is CBI officer Sharma. A court warrant for arrest is issued under money laundering.")
        assertTrue(result.isScam)
        assertEquals("DIGITAL_ARREST", result.scamCategory)
        assertTrue(result.scamScore >= 80)
    }

    @Test
    fun testLocalScamClassifierRemoteAccess() {
        val result = LocalScamClassifier.classify("Please install AnyDesk immediately for screen share support to resolve bank issue.")
        assertTrue(result.isScam)
        assertEquals("REMOTE_ACCESS_SCAM", result.scamCategory)
        assertTrue(result.scamScore >= 80)
    }

    @Test
    fun testLocalScamClassifierOtpTheft() {
        val result = LocalScamClassifier.classify("Your account is suspended. Share the OTP verification code immediately to unblock.")
        assertTrue(result.isScam)
        assertEquals("OTP_THEFT", result.scamCategory)
        assertTrue(result.scamScore >= 75)
    }

    @Test
    fun testLocalScamClassifierSafeMessage() {
        val result = LocalScamClassifier.classify("Hey, are we still meeting for dinner tonight at 8?")
        assertFalse(result.isScam)
        assertEquals("SAFE", result.scamCategory)
        assertTrue(result.scamScore < 20)
    }
}
