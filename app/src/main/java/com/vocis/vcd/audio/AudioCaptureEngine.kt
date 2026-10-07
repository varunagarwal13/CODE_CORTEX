package com.vocis.vcd.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Utility for converting raw 16-bit linear PCM byte buffers (from mic or WebRTC sink)
 * into normalized 32-bit floating point arrays in the range [-1.0, 1.0].
 */
object AudioCaptureEngine {

    /**
     * Converts a 16-bit linear PCM byte array (little-endian) into normalized floats.
     */
    fun pcm16ToNormalizedFloat(pcmBytes: ByteArray, offset: Int = 0, length: Int = pcmBytes.size): FloatArray {
        val sampleCount = length / 2
        val floatSamples = FloatArray(sampleCount)
        val byteBuffer = ByteBuffer.wrap(pcmBytes, offset, length).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until sampleCount) {
            val shortVal = byteBuffer.short
            floatSamples[i] = (shortVal.toFloat() / 32768.0f).coerceIn(-1.0f, 1.0f)
        }

        return floatSamples
    }

    /**
     * Converts normalized float samples back into 16-bit linear PCM bytes (little-endian).
     */
    fun normalizedFloatToPcm16(floatSamples: FloatArray): ByteArray {
        val byteBuffer = ByteBuffer.allocate(floatSamples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in floatSamples) {
            val clamped = sample.coerceIn(-1.0f, 1.0f)
            val shortVal = (clamped * 32767.0f).toInt().toShort()
            byteBuffer.putShort(shortVal)
        }
        return byteBuffer.array()
    }
}
