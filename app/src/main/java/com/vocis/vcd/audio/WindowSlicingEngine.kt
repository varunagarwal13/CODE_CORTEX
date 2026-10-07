package com.vocis.vcd.audio

import com.vocis.vcd.domain.MathPrimitives
import com.vocis.vcd.domain.VcdConstants

/**
 * Slices continuous streaming audio into overlapping analysis windows.
 * Window Size: 64,600 samples (4.0375s at 16kHz)
 * Hop Size: 48,000 samples (3.0s at 16kHz)
 *
 * Rejects silent windows based on RMS energy threshold.
 */
class WindowSlicingEngine(
    val windowSize: Int = VcdConstants.WINDOW_SAMPLES,
    val hopSize: Int = VcdConstants.HOP_SAMPLES,
    val silenceRmsThreshold: Float = VcdConstants.RMS_SILENCE_THRESHOLD
) {

    /**
     * Polls the ring buffer for ready windows.
     * Extracts full window of 64,600 samples and advances the read pointer by hopSize (48,000 samples).
     *
     * @return Sliced FloatArray if available, or null if insufficient samples are in the buffer.
     */
    fun extractNextWindow(ringBuffer: AudioRingBuffer): FloatArray? {
        if (ringBuffer.available() < windowSize) {
            return null
        }

        val window = FloatArray(windowSize)
        val readCount = ringBuffer.peek(window, windowSize)
        if (readCount < windowSize) {
            return null
        }

        // Advance buffer read head by hopSize to maintain 48,000 sample sliding stride
        ringBuffer.advance(hopSize)

        // Check if window is acoustic silence
        val rms = MathPrimitives.calculateRms(window)
        if (rms < silenceRmsThreshold) {
            // Silence frame: omit from neural inference to save CPU/battery
            return null
        }

        return window
    }

    /**
     * Batch slices a static audio buffer into multiple overlapping windows.
     * Useful for file-based processing or integration tests.
     */
    fun sliceBuffer(samples: FloatArray): List<FloatArray> {
        val result = mutableListOf<FloatArray>()
        var start = 0

        while (start + windowSize <= samples.size) {
            val window = samples.copyOfRange(start, start + windowSize)
            val rms = MathPrimitives.calculateRms(window)
            if (rms >= silenceRmsThreshold) {
                result.add(window)
            }
            start += hopSize
        }

        return result
    }
}
