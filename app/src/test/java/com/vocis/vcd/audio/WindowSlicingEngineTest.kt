package com.vocis.vcd.audio

import com.vocis.vcd.domain.VcdConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WindowSlicingEngineTest {

    @Test
    fun testWindowSlicingHopStride() {
        val windowSize = VcdConstants.WINDOW_SAMPLES // 64,600
        val hopSize = VcdConstants.HOP_SAMPLES       // 48,000
        val ringBuffer = AudioRingBuffer(capacity = 200000)
        val engine = WindowSlicingEngine(windowSize = windowSize, hopSize = hopSize)

        // 1. Initial empty buffer should return null
        assertNull(engine.extractNextWindow(ringBuffer))

        // 2. Feed 64,600 active samples
        val audioChunk1 = FloatArray(windowSize) { 0.2f }
        ringBuffer.write(audioChunk1)

        // Now first window should be extracted
        val window1 = engine.extractNextWindow(ringBuffer)
        assertNotNull(window1)
        assertEquals(windowSize, window1!!.size)

        // After extracting window 1, hopSize (48,000) was advanced, remaining unread = 64,600 - 48,000 = 16,600
        assertEquals(16600, ringBuffer.available())

        // Trying to extract next window should return null because 16,600 < 64,600
        assertNull(engine.extractNextWindow(ringBuffer))

        // Feed another 48,000 samples (matching hop size) -> total becomes 16,600 + 48,000 = 64,600
        val audioChunk2 = FloatArray(hopSize) { 0.3f }
        ringBuffer.write(audioChunk2)

        // Window 2 should now extract successfully
        val window2 = engine.extractNextWindow(ringBuffer)
        assertNotNull(window2)
        assertEquals(windowSize, window2!!.size)
    }

    @Test
    fun testSilenceRejection() {
        val windowSize = VcdConstants.WINDOW_SAMPLES
        val ringBuffer = AudioRingBuffer(capacity = 100000)
        val engine = WindowSlicingEngine(windowSize = windowSize, hopSize = VcdConstants.HOP_SAMPLES)

        // Feed silence (RMS < 0.01)
        val silentAudio = FloatArray(windowSize) { 0.0001f }
        ringBuffer.write(silentAudio)

        // Should return null and discard/advance the silent window
        val window = engine.extractNextWindow(ringBuffer)
        assertNull(window)
    }
}
