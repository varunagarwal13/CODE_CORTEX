package com.vocis.vcd.enrollment

import com.vocis.vcd.crypto.InMemoryBiometricCryptoVault
import com.vocis.vcd.inference.MockSpeakerEncoderModel
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceEnrollmentManagerTest {

    @Test
    fun testEnrollmentSuccessWithConsistentUtterances() {
        // Enrolled vector model returning a consistent speaker embedding
        val consistentEmbedding = FloatArray(256) { 0.5f }
        val encoder = MockSpeakerEncoderModel(fixedEmbedding = consistentEmbedding)
        val vault = InMemoryBiometricCryptoVault()
        val manager = VoiceEnrollmentManager(speakerEncoder = encoder, cryptoVault = vault)

        val utterance1 = FloatArray(64600) { 0.1f }
        val utterance2 = FloatArray(64600) { 0.1f }
        val utterance3 = FloatArray(64600) { 0.1f }

        val result = manager.enroll(
            contactId = "contact_01",
            contactName = "Mom",
            phoneNumber = "+919876543210",
            utterances = listOf(utterance1, utterance2, utterance3),
            sharedSecretQuestion = "Pet name in 2018?",
            sharedSecretAnswer = "Bruno"
        )

        assertTrue(result is VoiceEnrollmentManager.EnrollmentResult.Success)
        val voiceprint = (result as VoiceEnrollmentManager.EnrollmentResult.Success).voiceprint

        // Verify decrypted embedding has unit length (norm = 1.0)
        val decrypted = vault.decrypt(voiceprint.encryptedEmbedding, voiceprint.iv)
        var normSq = 0.0
        for (v in decrypted) normSq += (v * v)
        val norm = Math.sqrt(normSq).toFloat()
        assertTrue(Math.abs(norm - 1.0f) < 1e-4)
    }

    @Test
    fun testEnrollmentRejectionWithInconsistentUtterances() {
        val vault = InMemoryBiometricCryptoVault()

        // Mock encoder that returns alternating orthogonal vectors
        var callCount = 0
        val encoder = object : com.vocis.vcd.inference.SpeakerEncoderModel {
            override fun embed(audioWindow: FloatArray): FloatArray {
                val vec = FloatArray(256)
                vec[callCount % 256] = 1.0f
                callCount++
                return vec
            }
        }

        val manager = VoiceEnrollmentManager(speakerEncoder = encoder, cryptoVault = vault)
        val u1 = FloatArray(64600) { 0.1f }
        val u2 = FloatArray(64600) { 0.1f }
        val u3 = FloatArray(64600) { 0.1f }

        val result = manager.enroll(
            contactId = "contact_02",
            contactName = "Impostor Test",
            phoneNumber = "+919999999999",
            utterances = listOf(u1, u2, u3)
        )

        assertTrue(result is VoiceEnrollmentManager.EnrollmentResult.Failure)
    }

    @Test
    fun testEnrollmentMeasuresAndStoresBaselineSynthetic() {
        val consistentEmbedding = FloatArray(256) { 0.5f }
        val encoder = MockSpeakerEncoderModel(fixedEmbedding = consistentEmbedding)
        val vault = InMemoryBiometricCryptoVault()
        val detector = com.vocis.vcd.inference.MockAntiSpoofDetectorModel(mockSyntheticProbability = 0.14f)

        val manager = VoiceEnrollmentManager(
            speakerEncoder = encoder,
            cryptoVault = vault,
            antiSpoofDetector = detector
        )

        val u1 = FloatArray(64600) { 0.1f }
        val u2 = FloatArray(64600) { 0.1f }
        val u3 = FloatArray(64600) { 0.1f }

        val result = manager.enroll(
            contactId = "contact_03",
            contactName = "Alice",
            phoneNumber = "+919876543211",
            utterances = listOf(u1, u2, u3)
        )

        assertTrue(result is VoiceEnrollmentManager.EnrollmentResult.Success)
        val voiceprint = (result as VoiceEnrollmentManager.EnrollmentResult.Success).voiceprint
        org.junit.Assert.assertEquals(0.14f, voiceprint.baselineSynthetic ?: 0.0f, 1e-4f)
    }
}
