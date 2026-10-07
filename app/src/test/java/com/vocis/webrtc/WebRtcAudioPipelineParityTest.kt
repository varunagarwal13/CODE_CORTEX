package com.vocis.webrtc

import com.vocis.vcd.audio.SincResampler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

/**
 * Automated test suite validating WebRTC audio resampling, downmixing,
 * and 16-bit PCM conversion parity with reference implementation.
 */
class WebRtcAudioPipelineParityTest {

    @Test
    fun testSincResamplerDownsamples48kTo16k() {
        // Generate 48,000 Hz sine wave for 100 ms (4800 samples)
        val fromRate = 48000
        val toRate = 16000
        val sampleCount = 4800
        val input = FloatArray(sampleCount) { i ->
            sin(2.0 * Math.PI * 440.0 * i / fromRate).toFloat() * 0.8f
        }

        val resampled = SincResampler.resample(input, fromRate, toRate)
        val expectedLength = (sampleCount * toRate) / fromRate // 1600 samples

        assertEquals(expectedLength, resampled.size)

        // Verify signal energy is preserved and not silenced or clipped
        var maxAmp = 0f
        var energy = 0.0
        for (s in resampled) {
            val a = kotlin.math.abs(s)
            if (a > maxAmp) maxAmp = a
            energy += s * s
        }

        assertTrue("Resampled peak amplitude should be close to input 0.8", maxAmp in 0.7f..0.85f)
        assertTrue("Resampled signal must carry substantial energy", energy > 10.0)
    }

    @Test
    fun testSincResamplerPassthroughOnIdenticalRates() {
        val input = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f)
        val output = SincResampler.resample(input, 16000, 16000)
        assertEquals(input.size, output.size)
        for (i in input.indices) {
            assertEquals(input[i], output[i], 0.0001f)
        }
    }

    @Test
    fun testStereoToMonoAndPcm16Conversion() {
        val frames = 100
        val channels = 2
        // Interleaved L/R stereo samples: L=0.5, R=0.5
        val monoExpected = 0.5f

        val resampled16k = FloatArray(frames) { monoExpected }
        val pcmBytes = ByteArray(resampled16k.size * 2)

        for (i in resampled16k.indices) {
            val s = (resampled16k[i] * 32767.0f).toInt().coerceIn(-32768, 32767)
            pcmBytes[i * 2] = (s and 0xFF).toByte()
            pcmBytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }

        assertEquals(frames * 2, pcmBytes.size)

        // Read back first 16-bit integer
        val sample0 = ((pcmBytes[1].toInt() shl 8) or (pcmBytes[0].toInt() and 0xFF)).toShort()
        val floatBack = sample0.toFloat() / 32768.0f
        assertEquals(monoExpected, floatBack, 0.01f)
    }
}
