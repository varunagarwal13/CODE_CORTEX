package com.vocis.vcd.enrollment

import com.vocis.vcd.crypto.BiometricCryptoVault
import com.vocis.vcd.domain.ContactVoiceprint
import com.vocis.vcd.domain.MathPrimitives
import com.vocis.vcd.domain.VcdConstants
import com.vocis.vcd.inference.SpeakerEncoderModel

/**
 * Handles the 3-utterance voice enrollment wizard for registering trusted contacts.
 */
class VoiceEnrollmentManager(
    private val speakerEncoder: SpeakerEncoderModel,
    private val cryptoVault: BiometricCryptoVault,
    private val antiSpoofDetector: com.vocis.vcd.inference.AntiSpoofDetectorModel? = null,
    private val pairwiseSimilarityThreshold: Float = VcdConstants.PAIRWISE_SIMILARITY_THRESHOLD
) {

    sealed class EnrollmentResult {
        data class Success(val voiceprint: ContactVoiceprint) : EnrollmentResult()
        data class Failure(val reason: String) : EnrollmentResult()
    }

    /**
     * Enrolls a contact from 3 distinct recorded audio utterances.
     *
     * @param contactId Unique identifier of contact.
     * @param contactName Display name.
     * @param phoneNumber Contact phone number.
     * @param utterances 3 audio sample float arrays (each 3.0-5.0s at 16kHz).
     * @param sharedSecretQuestion Optional security challenge question.
     * @param sharedSecretAnswer Optional security challenge answer.
     */
    fun enroll(
        contactId: String,
        contactName: String,
        phoneNumber: String,
        utterances: List<FloatArray>,
        sharedSecretQuestion: String? = null,
        sharedSecretAnswer: String? = null
    ): EnrollmentResult {

        if (utterances.size != 3) {
            return EnrollmentResult.Failure("Enrollment requires exactly 3 utterances, provided: ${utterances.size}")
        }

        // 1. Extract 256-dimensional L2-normalized embeddings for each utterance
        val embeddings = utterances.map { utterance ->
            speakerEncoder.embed(utterance)
        }

        // 2. Validate pairwise cosine similarities across all 3 pairs: (0,1), (0,2), (1,2)
        val sim01 = MathPrimitives.cosineSimilarity(embeddings[0], embeddings[1])
        val sim02 = MathPrimitives.cosineSimilarity(embeddings[0], embeddings[2])
        val sim12 = MathPrimitives.cosineSimilarity(embeddings[1], embeddings[2])

        if (sim01 < pairwiseSimilarityThreshold ||
            sim02 < pairwiseSimilarityThreshold ||
            sim12 < pairwiseSimilarityThreshold
        ) {
            return EnrollmentResult.Failure(
                "Inconsistent voice samples. Pairwise similarities must exceed $pairwiseSimilarityThreshold. " +
                        "Observed: sim(1,2)=$sim01, sim(1,3)=$sim02, sim(2,3)=$sim12. Please re-record."
            )
        }

        // 3. Compute centroid vector and apply L2 normalization
        val centroid = MathPrimitives.calculateNormalizedCentroid(embeddings)

        // 4. Compute synthetic baseline using AASIST across the 3 enrollment utterances
        val baselineSynthetic: Float = if (antiSpoofDetector != null) {
            val syntheticScores = utterances.mapNotNull { utterance ->
                try {
                    antiSpoofDetector.detectSpoof(utterance)
                } catch (e: Exception) {
                    null
                }
            }
            if (syntheticScores.isNotEmpty()) {
                val sorted = syntheticScores.sorted()
                sorted[sorted.size / 2] // Median
            } else {
                0.0f
            }
        } else {
            0.0f
        }

        // 5. Encrypt centroid using hardware-backed BiometricCryptoVault
        val (encryptedBytes, iv) = cryptoVault.encrypt(centroid)

        // 6. Construct ContactVoiceprint entity
        val voiceprint = ContactVoiceprint(
            contactId = contactId,
            contactName = contactName,
            phoneNumber = phoneNumber,
            encryptedEmbedding = encryptedBytes,
            iv = iv,
            baselineSynthetic = if (baselineSynthetic > 0.0f) baselineSynthetic else null,
            sharedSecretQuestion = sharedSecretQuestion,
            sharedSecretAnswer = sharedSecretAnswer
        )

        return EnrollmentResult.Success(voiceprint)
    }

    /**
     * Enrolls voice and persists directly into Person A's Room database (vcd.db) via ContactVoiceprintDao.
     */
    suspend fun enrollAndPersist(
        dao: com.vocis.core.data.dao.ContactVoiceprintDao,
        name: String,
        phoneNumber: String,
        utterances: List<FloatArray>,
        relationship: String = "CONTACT",
        sharedSecretQuestion: String? = null,
        sharedSecretAnswer: String? = null
    ): EnrollmentResult {
        val result = enroll(
            contactId = "0",
            contactName = name,
            phoneNumber = phoneNumber,
            utterances = utterances,
            sharedSecretQuestion = sharedSecretQuestion,
            sharedSecretAnswer = sharedSecretAnswer
        )

        if (result is EnrollmentResult.Success) {
            val entity = com.vocis.core.data.entity.ContactVoiceprintEntity(
                name = name,
                relationship = relationship,
                phoneNumber = phoneNumber,
                voiceprintCipher = result.voiceprint.encryptedEmbedding,
                iv = result.voiceprint.iv,
                embeddingDim = VcdConstants.EMBEDDING_DIM,
                baselineSynthetic = result.voiceprint.baselineSynthetic ?: 0.0f
            )
            val insertedId = dao.insert(entity)
            return EnrollmentResult.Success(result.voiceprint.copy(contactId = insertedId.toString()))
        }

        return result
    }
}
