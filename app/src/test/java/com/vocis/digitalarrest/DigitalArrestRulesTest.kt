package com.vocis.digitalarrest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DigitalArrestRulesTest {

    @Test
    fun `test RULE_01 flags police and CBI authority claims`() {
        val claims = listOf("I am calling from Mumbai Police Cyber Cell and CBI headquarters.")
        val report = DigitalArrestRules.evaluate(callerClaims = claims)

        val rule1 = report.rules.first { it.ruleId == "RULE_01" }
        assertTrue("RULE_01 must be violated", rule1.isViolated)
        assertTrue(rule1.matchedEvidence.contains("police"))
        assertTrue(rule1.matchedEvidence.contains("cbi"))
        assertTrue(rule1.confidence > 0f)
    }

    @Test
    fun `test RULE_02 flags video confinement and room isolation demands`() {
        val claims = listOf("Stay on video call, do not disconnect, lock the door and keep camera on.")
        val report = DigitalArrestRules.evaluate(callerClaims = claims)

        val rule2 = report.rules.first { it.ruleId == "RULE_02" }
        assertTrue("RULE_02 must be violated", rule2.isViolated)
        assertTrue(rule2.matchedEvidence.contains("stay on video"))
        assertTrue(rule2.matchedEvidence.contains("do not disconnect"))
        assertTrue(rule2.matchedEvidence.contains("lock the door"))
    }

    @Test
    fun `test RULE_03 flags RBI verification account and escrow transfer demands`() {
        val claims = listOf("Transfer money immediately to RBI verification account for court escrow clearance.")
        val report = DigitalArrestRules.evaluate(callerClaims = claims)

        val rule3 = report.rules.first { it.ruleId == "RULE_03" }
        assertTrue("RULE_03 must be violated", rule3.isViolated)
        assertTrue(rule3.matchedEvidence.contains("transfer money"))
        assertTrue(rule3.matchedEvidence.contains("verification account"))
        assertTrue(rule3.matchedEvidence.contains("court escrow"))
    }

    @Test
    fun `test RULE_04 flags WhatsApp warrant and digital arrest summons`() {
        val claims = listOf("Check your phone, we have sent a digital arrest warrant and instant summons on WhatsApp.")
        val report = DigitalArrestRules.evaluate(callerClaims = claims)

        val rule4 = report.rules.first { it.ruleId == "RULE_04" }
        assertTrue("RULE_04 must be violated", rule4.isViolated)
        assertTrue(rule4.matchedEvidence.contains("digital arrest warrant") || rule4.matchedEvidence.contains("instant summons"))
    }

    @Test
    fun `test RULE_05 flags immediate arrest warrant threats`() {
        val claims = listOf("If you do not comply, immediate arrest and police raid within 1 hour will occur!")
        val report = DigitalArrestRules.evaluate(callerClaims = claims)

        val rule5 = report.rules.first { it.ruleId == "RULE_05" }
        assertTrue("RULE_05 must be violated", rule5.isViolated)
        assertTrue(rule5.matchedEvidence.contains("immediate arrest"))
        assertTrue(rule5.matchedEvidence.contains("within 1 hour"))
    }

    @Test
    fun `test multi-rule violation confirms digital arrest scheme`() {
        val claims = listOf(
            "This is Inspector Sharma from Enforcement Directorate.",
            "Stay on video, lock the door, do not tell anyone.",
            "Transfer funds to safe custody account within 2 hours or face immediate arrest."
        )
        val report = DigitalArrestRules.evaluate(callerClaims = claims)

        assertTrue(report.totalViolations >= 3)
        assertTrue("Digital arrest must be confirmed on composite indicators", report.isDigitalArrestConfirmed)
    }

    @Test
    fun `test benign communication clears all rules`() {
        val claims = listOf("Hello, this is Dr. Verma regarding your dental appointment tomorrow at 10 AM.")
        val report = DigitalArrestRules.evaluate(callerClaims = claims)

        assertEquals("No rules should be violated for benign text", 0, report.totalViolations)
        assertFalse(report.isDigitalArrestConfirmed)
        for (rule in report.rules) {
            assertFalse(rule.isViolated)
            assertEquals(0.0f, rule.confidence, 0.001f)
        }
    }
}
