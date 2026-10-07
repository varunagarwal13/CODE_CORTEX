package com.vocis.vcd.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Kaiser-windowed sinc resampler for WebRTC audio rate conversion.
 * Resamples incoming audio (e.g. 48,000 Hz or 44,100 Hz) down to pristine 16,000 Hz
 * without introducing broadband high-frequency aliasing artefacts that trigger false
 * synthetic voice detections in AASIST anti-spoof models.
 */
object SincResampler {

    private const val ZEROS = 24 // sinc lobes retained either side of the centre
    private const val KAISER_BETA = 8.6 // roughly -90 dB stopband

    fun resample(input: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        require(fromRate > 0 && toRate > 0) { "Sample rates must be positive" }
        if (fromRate == toRate || input.isEmpty()) return input

        val ratio = toRate.toDouble() / fromRate
        // When downsampling, the sinc must be stretched to the output band so it doubles as the
        // anti-alias filter. When upsampling it stays at the input band.
        val cutoff = if (ratio < 1.0) ratio else 1.0
        val halfWidth = ZEROS / cutoff

        val outLength = floor(input.size * ratio).toInt()
        val out = FloatArray(outLength)
        val invI0Beta = 1.0 / besselI0(KAISER_BETA)

        for (i in 0 until outLength) {
            val center = i / ratio
            val first = ceil(center - halfWidth).toInt()
            val last = floor(center + halfWidth).toInt()
            var acc = 0.0
            var norm = 0.0
            for (j in first..last) {
                if (j < 0 || j >= input.size) continue
                val w = kaiser(j - center, halfWidth, invI0Beta)
                if (w <= 0.0) continue
                val h = sinc((j - center) * cutoff) * w
                acc += input[j] * h
                norm += h
            }
            out[i] = if (norm != 0.0) (acc / norm).toFloat() else 0f
        }
        return out
    }

    private fun sinc(x: Double): Double {
        if (x == 0.0) return 1.0
        val px = PI * x
        return sin(px) / px
    }

    private fun kaiser(offset: Double, halfWidth: Double, invI0Beta: Double): Double {
        val r = offset / halfWidth
        if (abs(r) >= 1.0) return 0.0
        return besselI0(KAISER_BETA * sqrt(1.0 - r * r)) * invI0Beta
    }

    /** Zeroth-order modified Bessel function of the first kind, series form. */
    private fun besselI0(x: Double): Double {
        var sum = 1.0
        var term = 1.0
        var k = 1
        while (k < 64) {
            val f = x / (2.0 * k)
            term *= f * f
            sum += term
            if (term < sum * 1e-16) break
            k++
        }
        return sum
    }
}
