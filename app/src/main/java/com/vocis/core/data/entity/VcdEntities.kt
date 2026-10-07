package com.vocis.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "contact_voiceprints",
    indices = [
        Index(value = ["phoneNumber"], unique = true),
        Index(value = ["name"])
    ]
)
data class ContactVoiceprintEntity(
    @PrimaryKey(autoGenerate = true)
    val contactId: Long = 0,
    val name: String,
    val relationship: String = "CONTACT",
    val phoneNumber: String,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val enrolledSeconds: Float = 0f,
    val voiceprintCipher: ByteArray,
    val iv: ByteArray,
    val embeddingDim: Int = 256,
    val modelId: String = "voice_encoder.onnx",
    val consentAcknowledgedAtEpochMs: Long = System.currentTimeMillis(),
    val baselineSynthetic: Float = 0.0f,
    val variantLabels: String = "",
    val variantBaselines: String = "",
    val challengeCipher: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ContactVoiceprintEntity
        if (contactId != other.contactId) return false
        if (phoneNumber != other.phoneNumber) return false
        if (!voiceprintCipher.contentEquals(other.voiceprintCipher)) return false
        if (!iv.contentEquals(other.iv)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = contactId.hashCode()
        result = 31 * result + phoneNumber.hashCode()
        result = 31 * result + voiceprintCipher.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        return result
    }
}

@Entity(
    tableName = "vcd_call_history",
    indices = [
        Index(value = ["startedAtEpochMs"]),
        Index(value = ["phoneNumber"])
    ]
)
data class VcdCallHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val sessionId: Long = 0,
    val peerName: String,
    val phoneNumber: String = "",
    val contactLabel: String = "",
    val isOutgoing: Boolean = false,
    val startedAtEpochMs: Long = System.currentTimeMillis(),
    val durationSeconds: Long = 0,
    val peakSyntheticScore: Float = 0f,
    val minSimilarityScore: Float = 0f,
    val finalVerdict: String = "UNKNOWN",
    val callEnding: String = "NORMAL"
)
