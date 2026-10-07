package com.vocis.vcd.crypto

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Interface contract with Role A's AndroidKeyStore AES-256 GCM vault.
 * Handles cryptographic serialization of floating-point neural voiceprint embeddings.
 */
interface BiometricCryptoVault {
    /**
     * Encrypts a float embedding vector.
     * @return Pair containing ciphertext ByteArray and the 12-byte IV.
     */
    fun encrypt(embedding: FloatArray): Pair<ByteArray, ByteArray>

    /**
     * Decrypts a ciphertext ByteArray using the provided IV back into a float embedding.
     */
    fun decrypt(cipherText: ByteArray, iv: ByteArray): FloatArray
}

/**
 * Helper to convert between FloatArray and ByteArray for serialization.
 */
object FloatArraySerializer {
    fun toByteArray(floats: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(floats.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (f in floats) {
            buffer.putFloat(f)
        }
        return buffer.array()
    }

    fun toFloatArray(bytes: ByteArray): FloatArray {
        require(bytes.size % 4 == 0) { "Byte array length must be divisible by 4" }
        val floats = FloatArray(bytes.size / 4)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in floats.indices) {
            floats[i] = buffer.float
        }
        return floats
    }
}

/**
 * In-memory reference implementation of BiometricCryptoVault for independent unit testing
 * before Role A's hardware KeyStore is wired.
 */
class InMemoryBiometricCryptoVault : BiometricCryptoVault {
    // Uses a fixed XOR mask and dummy IV for mock encryption in JVM unit tests
    private val dummyIv = ByteArray(12) { (it + 7).toByte() }

    override fun encrypt(embedding: FloatArray): Pair<ByteArray, ByteArray> {
        val rawBytes = FloatArraySerializer.toByteArray(embedding)
        val cipher = ByteArray(rawBytes.size) { i -> (rawBytes[i].toInt() xor 0x5A).toByte() }
        return Pair(cipher, dummyIv.copyOf())
    }

    override fun decrypt(cipherText: ByteArray, iv: ByteArray): FloatArray {
        val rawBytes = ByteArray(cipherText.size) { i -> (cipherText[i].toInt() xor 0x5A).toByte() }
        return FloatArraySerializer.toFloatArray(rawBytes)
    }
}

/**
 * Adapter bridging Role C's BiometricCryptoVault contract directly with
 * Role A's hardware AndroidKeyStore AES-256 GCM vault (com.vocis.core.crypto.BiometricCryptoVault).
 */
class KeystoreBiometricCryptoVault(
    private val coreVault: com.vocis.core.crypto.BiometricCryptoVault = com.vocis.core.crypto.BiometricCryptoVault()
) : BiometricCryptoVault {

    override fun encrypt(embedding: FloatArray): Pair<ByteArray, ByteArray> {
        val payload = coreVault.encrypt(embedding)
        val iv = payload.copyOfRange(0, 12)
        val cipher = payload.copyOfRange(12, payload.size)
        return Pair(cipher, iv)
    }

    override fun decrypt(cipherText: ByteArray, iv: ByteArray): FloatArray {
        val payload = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, payload, 0, iv.size)
        System.arraycopy(cipherText, 0, payload, iv.size, cipherText.size)
        return coreVault.decrypt(payload)
    }
}
