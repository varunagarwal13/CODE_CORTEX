package com.vocis.vcd.inference

import com.vocis.vcd.domain.MathPrimitives

/**
 * Interface for speaker embedding extraction models (Resemblyzer GE2E architecture).
 */
interface SpeakerEncoderModel {
    /**
     * Extracts a 256-dimensional L2-normalized speaker embedding vector from an audio frame.
     */
    fun embed(audioWindow: FloatArray): FloatArray
}

/**
 * Interface for audio anti-spoofing detection models (AASIST architecture).
 */
interface AntiSpoofDetectorModel {
    /**
     * Analyzes a 64,600-sample audio window and outputs synthetic speech probability in [0.0, 1.0].
     */
    fun detectSpoof(audioWindow: FloatArray): Float
}

/**
 * Mock implementation of SpeakerEncoderModel for JVM unit tests without native ONNX library.
 */
class MockSpeakerEncoderModel(val fixedEmbedding: FloatArray? = null) : SpeakerEncoderModel {
    override fun embed(audioWindow: FloatArray): FloatArray {
        if (fixedEmbedding != null) {
            return MathPrimitives.l2Normalize(fixedEmbedding)
        }
        // Generate deterministic pseudo-embedding based on window energy
        val dim = 256
        val embedding = FloatArray(dim)
        var sum = 0.0f
        for (i in 0 until minOf(dim, audioWindow.size)) {
            embedding[i] = audioWindow[i] * (i + 1)
            sum += embedding[i] * embedding[i]
        }
        if (sum == 0.0f) embedding[0] = 1.0f
        return MathPrimitives.l2Normalize(embedding)
    }
}

/**
 * Mock implementation of AntiSpoofDetectorModel for JVM unit tests.
 */
class MockAntiSpoofDetectorModel(var mockSyntheticProbability: Float = 0.05f) : AntiSpoofDetectorModel {
    override fun detectSpoof(audioWindow: FloatArray): Float {
        return mockSyntheticProbability.coerceIn(0.0f, 1.0f)
    }
}
