package com.vocis

import com.vocis.core.crypto.BiometricCryptoVault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

class BiometricCryptoVaultTest {

    @Test
    fun testEncryptionAndDecryptionRoundTrip() {
        val testKeyBytes = ByteArray(32) { it.toByte() }
        val testKey = SecretKeySpec(testKeyBytes, "AES")
        val vault = BiometricCryptoVault(fallbackSecretKey = testKey)

        val original = FloatArray(256) { (it * 0.1f) - 12.8f }
        val encrypted = vault.encrypt(original)

        // IV is 12 bytes, ciphertext + tag is 256 * 4 + 16 = 1040 bytes -> total 1052 bytes
        assertTrue(encrypted.size > 12)
        assertEquals(12 + 256 * 4 + 16, encrypted.size)

        val decrypted = vault.decrypt(encrypted)
        assertEquals(original.size, decrypted.size)
        for (i in original.indices) {
            assertEquals("Mismatch at index $i", original[i], decrypted[i], 1e-6f)
        }
    }

    @Test
    fun testDistinctInvocationsProduceDistinctIVs() {
        val testKeyBytes = ByteArray(32) { (it + 5).toByte() }
        val testKey = SecretKeySpec(testKeyBytes, "AES")
        val vault = BiometricCryptoVault(fallbackSecretKey = testKey)

        val data = FloatArray(10) { 1.0f }
        val enc1 = vault.encrypt(data)
        val enc2 = vault.encrypt(data)

        // Compare first 12 bytes (the IVs)
        val iv1 = enc1.sliceArray(0 until 12)
        val iv2 = enc2.sliceArray(0 until 12)
        assertFalse(iv1.contentEquals(iv2))
    }
}
