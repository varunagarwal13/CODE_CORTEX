package com.vocis.digitalarrest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DigitalArrestPdfGeneratorTest {

    @Test
    fun `test computeEvidenceSeal produces valid 64-char hex SHA-256 hash`() {
        val ruleReport = DigitalArrestRules.evaluate(
            callerClaims = listOf("Police inspector calling", "Transfer to verification account")
        )

        val metadata = DigitalArrestPdfGenerator.PdfEvidenceMetadata(
            incidentId = "INC_TEST_1001",
            timestamp = 1718000000000L,
            callerPhoneNumber = "+919876543210",
            callerName = "Suspect Fake Officer",
            riskScore = 95,
            claims = listOf("Police inspector calling", "Transfer to verification account"),
            ruleReport = ruleReport,
            osFingerprint = "Google Pixel 8 (Android 14)"
        )

        val seal = DigitalArrestPdfGenerator.computeEvidenceSeal(metadata)

        assertNotNull(seal)
        assertEquals("SHA-256 seal must be 64 characters long", 64, seal.length)
        assertTrue("SHA-256 seal must be hexadecimal", seal.matches("[0-9a-f]{64}".toRegex()))
    }

    @Test
    fun `test computeEvidenceSeal is deterministic for identical inputs`() {
        val ruleReport = DigitalArrestRules.evaluate(
            callerClaims = listOf("CBI arrest threat")
        )

        val meta1 = DigitalArrestPdfGenerator.PdfEvidenceMetadata(
            incidentId = "INC_SAME",
            timestamp = 1000L,
            callerPhoneNumber = "+911111111111",
            callerName = null,
            riskScore = 80,
            claims = listOf("CBI arrest threat"),
            ruleReport = ruleReport,
            osFingerprint = "DeviceA"
        )

        val meta2 = DigitalArrestPdfGenerator.PdfEvidenceMetadata(
            incidentId = "INC_SAME",
            timestamp = 1000L,
            callerPhoneNumber = "+911111111111",
            callerName = null,
            riskScore = 80,
            claims = listOf("CBI arrest threat"),
            ruleReport = ruleReport,
            osFingerprint = "DeviceA"
        )

        val seal1 = DigitalArrestPdfGenerator.computeEvidenceSeal(meta1)
        val seal2 = DigitalArrestPdfGenerator.computeEvidenceSeal(meta2)

        assertEquals("Identical evidence must produce identical cryptographic seal", seal1, seal2)
    }

    @Test
    fun `test tampering with evidence alters the SHA-256 seal`() {
        val ruleReport = DigitalArrestRules.evaluate(
            callerClaims = listOf("CBI arrest threat")
        )

        val authenticMeta = DigitalArrestPdfGenerator.PdfEvidenceMetadata(
            incidentId = "INC_AUTH",
            timestamp = 1000L,
            callerPhoneNumber = "+911111111111",
            callerName = null,
            riskScore = 90,
            claims = listOf("CBI arrest threat"),
            ruleReport = ruleReport,
            osFingerprint = "DeviceA"
        )

        val tamperedMeta = authenticMeta.copy(
            riskScore = 20 // Altered score
        )

        val sealAuthentic = DigitalArrestPdfGenerator.computeEvidenceSeal(authenticMeta)
        val sealTampered = DigitalArrestPdfGenerator.computeEvidenceSeal(tamperedMeta)

        assertNotEquals("Tampered evidence metadata must alter the cryptographic seal", sealAuthentic, sealTampered)
    }
}
