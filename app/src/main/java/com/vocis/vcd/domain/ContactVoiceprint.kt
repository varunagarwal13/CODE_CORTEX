package com.vocis.vcd.domain

/**
 * Encrypted biometric profile representing an enrolled trusted contact.
 */
data class ContactVoiceprint(
    val contactId: String,
    val contactName: String,
    val phoneNumber: String,
    val encryptedEmbedding: ByteArray,
    val iv: ByteArray,
    val baselineSynthetic: Float? = null,
    val sharedSecretQuestion: String? = null,
    val sharedSecretAnswer: String? = null,
    val enrolledAtMs: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ContactVoiceprint

        if (contactId != other.contactId) return false
        if (contactName != other.contactName) return false
        if (phoneNumber != other.phoneNumber) return false
        if (!encryptedEmbedding.contentEquals(other.encryptedEmbedding)) return false
        if (!iv.contentEquals(other.iv)) return false
        if (baselineSynthetic != other.baselineSynthetic) return false
        if (sharedSecretQuestion != other.sharedSecretQuestion) return false
        if (sharedSecretAnswer != other.sharedSecretAnswer) return false
        if (enrolledAtMs != other.enrolledAtMs) return false

        return true
    }

    override fun hashCode(): Int {
        var result = contactId.hashCode()
        result = 31 * result + contactName.hashCode()
        result = 31 * result + phoneNumber.hashCode()
        result = 31 * result + encryptedEmbedding.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + (baselineSynthetic?.hashCode() ?: 0)
        result = 31 * result + (sharedSecretQuestion?.hashCode() ?: 0)
        result = 31 * result + (sharedSecretAnswer?.hashCode() ?: 0)
        result = 31 * result + enrolledAtMs.hashCode()
        return result
    }
}
