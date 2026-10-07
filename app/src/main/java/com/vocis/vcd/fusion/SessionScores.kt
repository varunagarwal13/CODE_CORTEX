package com.vocis.vcd.fusion

import com.vocis.vcd.domain.VcdConstants
import com.vocis.vcd.domain.VcdVerdict
import java.util.ArrayDeque

/**
 * Maintains a sliding FIFO window of recent scores with EMA smoothing and asymmetric
 * hysteresis to stabilize instantaneous acoustic glitches and transient micro-bursts.
 */
class SessionScores(val windowSize: Int = VcdConstants.MEDIAN_FILTER_WINDOW_SIZE) {

    companion object {
        const val ALPHA = 0.4f
        const val BUFFER_WINDOWS = 5
        const val MIN_WINDOWS = 3
        const val ESCALATE_WINDOWS = 2
        const val DE_ESCALATE_WINDOWS = 4
    }

    private val similarityDeque = ArrayDeque<Float>(windowSize)
    private val syntheticDeque = ArrayDeque<Float>(windowSize)
    private val verdictHistory = mutableListOf<VcdVerdict>()

    var smoothedSimilarity: Float? = null
        private set
    var smoothedSynthetic: Float? = null
        private set

    var currentVerdict: VcdVerdict = VcdVerdict.UNCERTAIN
        private set
    var stableVerdict: VcdVerdict = VcdVerdict.UNCERTAIN
        private set
    var peakVerdict: VcdVerdict = VcdVerdict.UNCERTAIN
        private set

    private var pendingVerdict: VcdVerdict? = null
    private var pendingCount: Int = 0

    fun push(similarity: Float, syntheticProbability: Float) {
        if (similarityDeque.size >= windowSize) {
            similarityDeque.removeFirst()
        }
        similarityDeque.addLast(similarity)

        if (syntheticDeque.size >= windowSize) {
            syntheticDeque.removeFirst()
        }
        syntheticDeque.addLast(syntheticProbability)

        smoothedSimilarity = ema(smoothedSimilarity, similarity)
        smoothedSynthetic = ema(smoothedSynthetic, syntheticProbability)
    }

    fun acceptVerdict(verdict: VcdVerdict) {
        verdictHistory.add(verdict)
        currentVerdict = verdict

        if (verdict.severity() > peakVerdict.severity()) {
            peakVerdict = verdict
        }

        val recent = verdictHistory.takeLast(BUFFER_WINDOWS)
        if (recent.size >= MIN_WINDOWS) {
            val candidate = recent.last()
            if (candidate == stableVerdict) {
                pendingVerdict = null
                pendingCount = 0
            } else if (stableVerdict == VcdVerdict.UNCERTAIN) {
                stableVerdict = candidate
                pendingVerdict = null
                pendingCount = 0
            } else {
                val required = if (candidate.severity() > stableVerdict.severity()) ESCALATE_WINDOWS else DE_ESCALATE_WINDOWS
                if (pendingVerdict == candidate) {
                    pendingCount++
                } else {
                    pendingVerdict = candidate
                    pendingCount = 1
                }
                if (pendingCount >= required) {
                    stableVerdict = candidate
                    pendingVerdict = null
                    pendingCount = 0
                }
            }
        }
    }

    private fun ema(previous: Float?, sample: Float): Float =
        if (previous == null) sample else previous * (1f - ALPHA) + sample * ALPHA

    private fun VcdVerdict.severity(): Int = when (this) {
        VcdVerdict.CRITICAL_CLONE_DETECTED, VcdVerdict.CRITICAL_UNKNOWN_SYNTHETIC -> 3
        VcdVerdict.SUSPICIOUS_IMPOSTOR -> 2
        VcdVerdict.SAFE_VERIFIED_AUTHENTIC -> 1
        VcdVerdict.UNCERTAIN, VcdVerdict.UNRELIABLE_LINE_SATURATED -> 0
    }

    fun medianSimilarity(): Float {
        if (similarityDeque.isEmpty()) return 0.0f
        val sorted = similarityDeque.sorted()
        return sorted[sorted.size / 2]
    }

    fun medianSynthetic(): Float {
        if (syntheticDeque.isEmpty()) return 0.0f
        val sorted = syntheticDeque.sorted()
        return sorted[sorted.size / 2]
    }

    fun isFull(): Boolean = similarityDeque.size >= windowSize

    fun reset() {
        similarityDeque.clear()
        syntheticDeque.clear()
        verdictHistory.clear()
        smoothedSimilarity = null
        smoothedSynthetic = null
        currentVerdict = VcdVerdict.UNCERTAIN
        stableVerdict = VcdVerdict.UNCERTAIN
        peakVerdict = VcdVerdict.UNCERTAIN
        pendingVerdict = null
        pendingCount = 0
    }
}
