package com.vocis.emergency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyAlarmSystemTest {

    @Test
    fun `test generateSirenPcm produces correct buffer size`() {
        val sampleRate = 16000
        val duration = 1
        val pcm = EmergencyAlarmSystem.generateSirenPcm(sampleRate, duration)

        assertEquals("PCM buffer size must match sampleRate * duration", 16000, pcm.size)
    }

    @Test
    fun `test generateSirenPcm has active oscillation and non-zero amplitudes`() {
        val pcm = EmergencyAlarmSystem.generateSirenPcm(16000, 1)

        var nonZeroCount = 0
        var maxSample = 0
        for (sample in pcm) {
            if (sample != 0.toShort()) nonZeroCount++
            val abs = Math.abs(sample.toInt())
            if (abs > maxSample) maxSample = abs
        }

        assertTrue("PCM must contain active oscillations", nonZeroCount > 10000)
        assertTrue("Max amplitude must be substantial", maxSample > 10000)
    }

    @Test
    fun `test generateSirenPcm exhibits frequency shift between two halves`() {
        val sampleRate = 16000
        val pcm = EmergencyAlarmSystem.generateSirenPcm(sampleRate, 1)

        // Count zero-crossings in first half (800Hz) vs second half (1200Hz)
        var zeroCrossingsHalf1 = 0
        for (i in 1 until 8000) {
            if ((pcm[i - 1] < 0 && pcm[i] >= 0) || (pcm[i - 1] >= 0 && pcm[i] < 0)) {
                zeroCrossingsHalf1++
            }
        }

        var zeroCrossingsHalf2 = 0
        for (i in 8001 until 16000) {
            if ((pcm[i - 1] < 0 && pcm[i] >= 0) || (pcm[i - 1] >= 0 && pcm[i] < 0)) {
                zeroCrossingsHalf2++
            }
        }

        // 1200 Hz tone has ~1.5x more zero-crossings than 800 Hz tone
        assertTrue("Second half (1200Hz) must have more zero-crossings than first half (800Hz)",
            zeroCrossingsHalf2 > zeroCrossingsHalf1)
    }

    @Test
    fun `test alarm initially not playing`() {
        assertFalse(EmergencyAlarmSystem.isAlarmPlaying())
    }
}
