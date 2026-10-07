package com.vocis

import com.vocis.core.domain.model.EventType
import com.vocis.sensor.normalizer.EventNormalizer
import com.vocis.sensor.normalizer.NotificationNormalizer
import com.vocis.sensor.normalizer.RemoteDesktopPackages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalizerTest {

    @Test
    fun testIncomingCallNormalization() {
        val event = EventNormalizer.normalizeIncomingCall("tel:+919876543210")
        assertEquals(EventType.INCOMING_CALL, event.type)
        assertEquals("+919876543210", event.identity)
        assertEquals("CallScreeningSensor", event.source)
        assertEquals("LOW", event.initialRisk)
    }

    @Test
    fun testRestrictedNumberNormalizationToUnknown() {
        val eventRestricted = EventNormalizer.normalizeIncomingCall("-2")
        assertEquals("UNKNOWN", eventRestricted.identity)

        val eventSingleDigit = EventNormalizer.normalizeIncomingCall("2")
        assertEquals("UNKNOWN", eventSingleDigit.identity)
    }

    @Test
    fun testOutgoingCallNormalization() {
        val event = EventNormalizer.normalizeOutgoingCall("+1234567890")
        assertEquals(EventType.OUTGOING_CALL, event.type)
        assertEquals("+1234567890", event.identity)
    }

    @Test
    fun testSmsNormalizationUrgentKeywords() {
        val normalSms = EventNormalizer.normalizeSms("+919999999999", "Hello how are you")
        assertEquals("LOW", normalSms.initialRisk)

        val otpSms = EventNormalizer.normalizeSms("+919999999999", "Your OTP is 123456 for transaction")
        assertEquals("ELEVATED", otpSms.initialRisk)
    }

    @Test
    fun testRemoteDesktopPackageDetection() {
        assertTrue(RemoteDesktopPackages.isRemoteDesktop(RemoteDesktopPackages.ANYDESK))
        assertTrue(RemoteDesktopPackages.isRemoteDesktop(RemoteDesktopPackages.TEAMVIEWER))
        assertTrue(RemoteDesktopPackages.isRemoteDesktop(RemoteDesktopPackages.RUSTDESK))

        val pkgEvent = EventNormalizer.normalizePackageAdded(RemoteDesktopPackages.ANYDESK)
        assertEquals(EventType.PACKAGE_ADDED, pkgEvent.type)
        assertEquals("CRITICAL", pkgEvent.initialRisk)
    }

    @Test
    fun testNotificationNormalizerDetection() {
        val normalNotif = NotificationNormalizer.normalize(
            packageName = "com.example.chat",
            title = "Friend",
            text = "Dinner at 7?"
        )
        assertEquals("LOW", normalNotif.initialRisk)

        val remoteDesktopNotif = NotificationNormalizer.normalize(
            packageName = RemoteDesktopPackages.TEAMVIEWER,
            title = "Remote Control",
            text = "Session connected"
        )
        assertEquals("CRITICAL", remoteDesktopNotif.initialRisk)
    }
}
