package com.vocis.speech.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalHeuristicScamClassifierTest {

    private val classifier = LocalHeuristicScamClassifier()

    @Test
    fun testDigitalArrestLureDetection() {
        val transcript = "This is senior CBI officer from Mumbai Police. Your passport is seized and you are placed under digital arrest for money laundering."
        val result = classifier.classify(transcript)

        assertTrue(result.isScam)
        assertEquals(ScamCategory.DIGITAL_ARREST, result.scamCategory)
        assertEquals(UrgencyLevel.EXTREME, result.urgencyLevel)
        assertTrue(result.confidence >= 0.85f)
        assertTrue(result.isFromFallback)
    }

    @Test
    fun testOtpTheftDetection() {
        val transcript = "To stop the unauthorized transaction, read back the 6 digit verification code and OTP sent to your phone."
        val result = classifier.classify(transcript)

        assertTrue(result.isScam)
        assertEquals(ScamCategory.OTP_THEFT, result.scamCategory)
        assertEquals(UrgencyLevel.EXTREME, result.urgencyLevel)
    }

    @Test
    fun testRemoteAccessLureDetection() {
        val transcript = "Please open play store and install AnyDesk quicksupport app so our technician can assist you."
        val result = classifier.classify(transcript)

        assertTrue(result.isScam)
        assertEquals(ScamCategory.REMOTE_ACCESS, result.scamCategory)
        assertEquals(UrgencyLevel.HIGH, result.urgencyLevel)
    }

    @Test
    fun testBenignConversation() {
        val transcript = "Hey, are you free this evening? Let's grab coffee around 6 PM near the park."
        val result = classifier.classify(transcript)

        assertFalse(result.isScam)
        assertEquals(ScamCategory.NONE, result.scamCategory)
        assertEquals(UrgencyLevel.LOW, result.urgencyLevel)
    }
}
