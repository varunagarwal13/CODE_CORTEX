package com.vocis.emergency

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EmergencySmsKeywordMatcherTest {

    private lateinit var matcher: EmergencySmsKeywordMatcher

    @Before
    fun setUp() {
        matcher = EmergencySmsKeywordMatcher(contactDao = null)
    }

    @Test
    fun `test exact keyword matches`() {
        assertTrue(matcher.containsKeyword("VOCIS"))
    }

    @Test
    fun `test case-insensitive keyword matches`() {
        assertTrue(matcher.containsKeyword("vocis"))
        assertTrue(matcher.containsKeyword("Vocis"))
        assertTrue(matcher.containsKeyword("VoCiS"))
    }

    @Test
    fun `test keyword embedded in sentence matches`() {
        val message = "EMERGENCY: Suspect caller claiming digital arrest! Please activate VOCIS defense system now!"
        assertTrue(matcher.containsKeyword(message))
    }

    @Test
    fun `test benign SMS without keyword does not match`() {
        val normalSms = "Your OTP for HDFC Bank login is 482910. Valid for 10 minutes."
        assertFalse(matcher.containsKeyword(normalSms))

        val familyMessage = "Dinner is ready, please come home soon."
        assertFalse(matcher.containsKeyword(familyMessage))
    }

    @Test
    fun `test null or blank SMS does not match`() {
        assertFalse(matcher.containsKeyword(null))
        assertFalse(matcher.containsKeyword(""))
        assertFalse(matcher.containsKeyword("   "))
    }

    @Test
    fun `test authorized sender allows all when no contacts configured`() = runBlocking {
        assertTrue(matcher.isAuthorizedSender("+919876543210"))
        assertTrue(matcher.isAuthorizedSender("12345"))
    }

    @Test
    fun `test blank sender rejected`() = runBlocking {
        assertFalse(matcher.isAuthorizedSender(null))
        assertFalse(matcher.isAuthorizedSender(""))
    }
}
