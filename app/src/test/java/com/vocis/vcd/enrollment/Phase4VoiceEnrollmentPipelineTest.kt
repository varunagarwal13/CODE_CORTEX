package com.vocis.vcd.enrollment

import com.vocis.core.data.dao.ContactVoiceprintDao
import com.vocis.core.data.entity.ContactVoiceprintEntity
import com.vocis.vcd.audio.VoiceSampleRecorder
import com.vocis.vcd.crypto.InMemoryBiometricCryptoVault
import com.vocis.vcd.domain.VcdConstants
import com.vocis.vcd.inference.MockSpeakerEncoderModel
import com.vocis.vcd.inference.SpeakerEncoderModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class Phase4VoiceEnrollmentPipelineTest {

    // In-memory fake ContactVoiceprintDao for fast deterministic unit testing
    class FakeContactVoiceprintDao : ContactVoiceprintDao {
        val records = mutableListOf<ContactVoiceprintEntity>()
        private var nextId = 1L

        override suspend fun insert(voiceprint: ContactVoiceprintEntity): Long {
            val assigned = voiceprint.copy(contactId = nextId++)
            records.add(assigned)
            return assigned.contactId
        }

        override suspend fun update(voiceprint: ContactVoiceprintEntity) {
            val index = records.indexOfFirst { it.contactId == voiceprint.contactId }
            if (index != -1) records[index] = voiceprint
        }

        override suspend fun delete(voiceprint: ContactVoiceprintEntity) {
            records.removeAll { it.contactId == voiceprint.contactId }
        }

        override suspend fun getByPhoneNumber(phoneNumber: String): ContactVoiceprintEntity? {
            return records.firstOrNull { it.phoneNumber == phoneNumber }
        }

        override suspend fun getById(id: Long): ContactVoiceprintEntity? {
            return records.firstOrNull { it.contactId == id }
        }

        override fun getAllFlow(): Flow<List<ContactVoiceprintEntity>> {
            return flowOf(records.toList())
        }

        override suspend fun getAll(): List<ContactVoiceprintEntity> {
            return records.toList()
        }
    }

    @Test
    fun testPhase4FullEnrollmentAndPersistencePipeline() = runBlocking {
        val dao = FakeContactVoiceprintDao()
        val consistentEmbedding = FloatArray(256) { 0.25f }
        val encoder = MockSpeakerEncoderModel(fixedEmbedding = consistentEmbedding)
        val vault = InMemoryBiometricCryptoVault()
        val manager = VoiceEnrollmentManager(
            speakerEncoder = encoder,
            cryptoVault = vault,
            pairwiseSimilarityThreshold = VcdConstants.PAIRWISE_SIMILARITY_THRESHOLD
        )

        // 3 valid utterances (>= 32000 samples each)
        val sample1 = FloatArray(35000) { 0.05f }
        val sample2 = FloatArray(35000) { 0.05f }
        val sample3 = FloatArray(35000) { 0.05f }

        val result = manager.enrollAndPersist(
            dao = dao,
            name = "Sarah Connor",
            phoneNumber = "+12025550199",
            utterances = listOf(sample1, sample2, sample3),
            relationship = "Mother"
        )

        // Verify enrollment succeeded
        assertTrue(result is VoiceEnrollmentManager.EnrollmentResult.Success)

        // Verify entity persisted in DAO
        assertEquals(1, dao.records.size)
        val persisted = dao.records[0]
        assertEquals("Sarah Connor", persisted.name)
        assertEquals("+12025550199", persisted.phoneNumber)
        assertEquals("Mother", persisted.relationship)
        assertEquals(256, persisted.embeddingDim)
        assertTrue(persisted.voiceprintCipher.isNotEmpty())
        assertTrue(persisted.iv.isNotEmpty())

        // Verify decrypted embedding is a valid normalized centroid
        val decrypted = vault.decrypt(persisted.voiceprintCipher, persisted.iv)
        assertEquals(256, decrypted.size)
        var sumSq = 0.0
        for (v in decrypted) sumSq += (v * v)
        val norm = sqrt(sumSq).toFloat()
        assertTrue(Math.abs(norm - 1.0f) < 1e-4)
    }

    @Test
    fun testPhase4InconsistentUtterancesRejectedAndNotPersisted() = runBlocking {
        val dao = FakeContactVoiceprintDao()
        val vault = InMemoryBiometricCryptoVault()

        var callCount = 0
        // Alternating orthogonal unit vectors (cosine similarity = 0.0 < 0.75 threshold)
        val encoder = object : SpeakerEncoderModel {
            override fun embed(audioWindow: FloatArray): FloatArray {
                val vec = FloatArray(256)
                vec[callCount % 256] = 1.0f
                callCount++
                return vec
            }
        }

        val manager = VoiceEnrollmentManager(
            speakerEncoder = encoder,
            cryptoVault = vault,
            pairwiseSimilarityThreshold = 0.75f
        )

        val u1 = FloatArray(35000) { 0.1f }
        val u2 = FloatArray(35000) { 0.1f }
        val u3 = FloatArray(35000) { 0.1f }

        val result = manager.enrollAndPersist(
            dao = dao,
            name = "Unknown Caller",
            phoneNumber = "+19999999999",
            utterances = listOf(u1, u2, u3),
            relationship = "Stranger"
        )

        assertTrue(result is VoiceEnrollmentManager.EnrollmentResult.Failure)
        val failure = result as VoiceEnrollmentManager.EnrollmentResult.Failure
        assertTrue(failure.reason.contains("Inconsistent voice samples"))
        // Verify NOTHING persisted to the database
        assertEquals(0, dao.records.size)
    }

    @Test
    fun testPhase4InsufficientUtterancesRejected() = runBlocking {
        val dao = FakeContactVoiceprintDao()
        val encoder = MockSpeakerEncoderModel()
        val vault = InMemoryBiometricCryptoVault()
        val manager = VoiceEnrollmentManager(encoder, vault)

        val u1 = FloatArray(35000) { 0.1f }
        val u2 = FloatArray(35000) { 0.1f }

        val result = manager.enrollAndPersist(
            dao = dao,
            name = "Incomplete Contact",
            phoneNumber = "+11234567890",
            utterances = listOf(u1, u2) // Only 2 utterances instead of 3
        )

        assertTrue(result is VoiceEnrollmentManager.EnrollmentResult.Failure)
        assertEquals(0, dao.records.size)
    }

    @Test
    fun testVoiceSampleRecorderRejectsEmptyWhenNotRecording() {
        val recorder = VoiceSampleRecorder()
        // Stopping without recording should fail cleanly
        val result = recorder.stopRecording()
        assertTrue(result.isFailure)
    }

    @Test
    fun testSpeakerEncoderProviderFailsGracefullyWhenModelMissing() {
        val mockContext = org.mockito.Mockito.mock(android.content.Context::class.java)
        val mockAssetManager = org.mockito.Mockito.mock(android.content.res.AssetManager::class.java)
        org.mockito.Mockito.`when`(mockContext.assets).thenReturn(mockAssetManager)
        org.mockito.Mockito.`when`(mockAssetManager.open(org.mockito.ArgumentMatchers.anyString()))
            .thenThrow(java.io.FileNotFoundException("File not found"))

        val result = com.vocis.vcd.inference.SpeakerEncoderProvider.getSpeakerEncoder(mockContext)
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception is java.io.FileNotFoundException)
        assertTrue(exception!!.message!!.contains("Missing model asset 'voice_encoder.onnx'"))
    }
}
