package com.vocis.vcd.inference

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.vocis.vcd.domain.MathPrimitives
import com.vocis.vcd.domain.VcdConstants
import java.nio.FloatBuffer

/**
 * Production ONNX Runtime implementation of AASIST audio anti-spoofing detector.
 *
 * Input node: [1, 64600] float32 tensor representing 16kHz linear audio PCM.
 * Output node: [1, 2] logits [spoof, bonafide].
 *
 * Output matches upstream AASIST code and VOCIS reference:
 * logits ordered [spoof, bonafide], index 1 is the bonafide score,
 * so synthetic_probability is softmax(logits)[0].
 */
class OnnxAasistDetector(
    private val session: OrtSession,
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
) : AntiSpoofDetectorModel, AutoCloseable {

    override fun detectSpoof(audioWindow: FloatArray): Float {
        val inputWindow = when {
            audioWindow.size == VcdConstants.WINDOW_SAMPLES -> audioWindow
            audioWindow.size < VcdConstants.WINDOW_SAMPLES -> {
                val padded = FloatArray(VcdConstants.WINDOW_SAMPLES)
                System.arraycopy(audioWindow, 0, padded, 0, audioWindow.size)
                padded
            }
            else -> audioWindow.copyOf(VcdConstants.WINDOW_SAMPLES)
        }

        val shape = longArrayOf(1, VcdConstants.WINDOW_SAMPLES.toLong())
        val floatBuffer = FloatBuffer.wrap(inputWindow)
        val tensor = OnnxTensor.createTensor(env, floatBuffer, shape)

        tensor.use { inputTensor ->
            val inputName = session.inputNames.iterator().next()
            val results = session.run(mapOf(inputName to inputTensor))
            results.use { output ->
                val outputTensor = output[0] as OnnxTensor
                val rawValue = outputTensor.value

                // Handle 2D [1, 2] output logits array
                val logits = when (rawValue) {
                    is Array<*> -> {
                        val row0 = rawValue[0]
                        if (row0 is FloatArray) row0 else floatArrayOf(0.0f, 0.0f)
                    }
                    is FloatArray -> rawValue
                    else -> floatArrayOf(0.0f, 0.0f)
                }

                val probs = MathPrimitives.softmax2(logits)
                // AASIST logit order is [spoof, bonafide] (see AASIST specification).
                // probs[0] = spoof / synthetic probability
                // probs[1] = bonafide natural human probability
                return probs[0]
            }
        }
    }

    override fun close() {
        session.close()
    }
}

/**
 * Production ONNX Runtime implementation of Resemblyzer GE2E speaker encoder.
 * Accepts any duration by decomposing into 25,600-sample partials (1.6s) with 50% overlap,
 * normalises loudness to -30 dBFS matching reference pipeline,
 * and produces a 256-dimensional L2-normalized unit centroid embedding.
 */
class OnnxSpeakerEncoder(
    private val session: OrtSession,
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
) : SpeakerEncoderModel, AutoCloseable {

    companion object {
        private const val PARTIAL_WIDTH = 25600
        private const val PARTIAL_HOP = 12800
    }

    override fun embed(audioWindow: FloatArray): FloatArray {
        if (audioWindow.isEmpty()) return FloatArray(VcdConstants.EMBEDDING_DIM)

        // Normalise over the whole utterance before splitting, exactly as Resemblyzer specification
        val normalized = MathPrimitives.toTargetDbfs(audioWindow)

        if (normalized.size < PARTIAL_WIDTH) {
            val padded = FloatArray(PARTIAL_WIDTH)
            System.arraycopy(normalized, 0, padded, 0, normalized.size)
            return embedSinglePartial(padded)
        }

        val partials = mutableListOf<FloatArray>()
        var start = 0
        while (start + PARTIAL_WIDTH <= normalized.size) {
            val chunk = normalized.copyOfRange(start, start + PARTIAL_WIDTH)
            partials.add(embedSinglePartial(chunk))
            start += PARTIAL_HOP
        }

        if (start - PARTIAL_HOP + PARTIAL_WIDTH < normalized.size) {
            val chunk = normalized.copyOfRange(normalized.size - PARTIAL_WIDTH, normalized.size)
            partials.add(embedSinglePartial(chunk))
        }

        if (partials.isEmpty()) {
            return embedSinglePartial(normalized.copyOf(PARTIAL_WIDTH))
        }

        val meanVector = FloatArray(VcdConstants.EMBEDDING_DIM)
        for (p in partials) {
            for (i in 0 until VcdConstants.EMBEDDING_DIM) {
                meanVector[i] += p[i]
            }
        }
        val count = partials.size.toFloat()
        for (i in 0 until VcdConstants.EMBEDDING_DIM) {
            meanVector[i] /= count
        }

        return MathPrimitives.l2Normalize(meanVector)
    }

    private fun embedSinglePartial(partial: FloatArray): FloatArray {
        val shape = longArrayOf(1, PARTIAL_WIDTH.toLong())
        val floatBuffer = FloatBuffer.wrap(partial)
        val tensor = OnnxTensor.createTensor(env, floatBuffer, shape)

        tensor.use { inputTensor ->
            val inputName = session.inputNames.iterator().next()
            val results = session.run(mapOf(inputName to inputTensor))
            results.use { output ->
                val outputTensor = output[0] as OnnxTensor
                val rawValue = outputTensor.value

                val rawEmbedding = when (rawValue) {
                    is Array<*> -> {
                        val row0 = rawValue[0]
                        if (row0 is FloatArray) row0 else FloatArray(VcdConstants.EMBEDDING_DIM)
                    }
                    is FloatArray -> rawValue
                    else -> FloatArray(VcdConstants.EMBEDDING_DIM)
                }

                return MathPrimitives.l2Normalize(rawEmbedding)
            }
        }
    }

    override fun close() {
        session.close()
    }
}
