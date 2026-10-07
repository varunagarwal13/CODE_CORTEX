package com.vocis.vcd.inference

import ai.onnxruntime.OrtEnvironment
import android.content.Context
import com.vocis.VocisApplication
import java.io.FileNotFoundException

/**
 * Factory for resolving the production SpeakerEncoderModel.
 * Resolves the Resemblyzer GE2E ONNX model from application assets if available.
 * If the model asset is absent, fails truthfully without synthesizing fake embeddings.
 */
object SpeakerEncoderProvider {

    fun getSpeakerEncoder(context: Context): Result<SpeakerEncoderModel> {
        val app = (context.applicationContext as? VocisApplication)
        if (app?.speakerEncoder != null) {
            return Result.success(app.speakerEncoder!!)
        }

        return try {
            val candidatePaths = listOf(
                "models/speaker_encoder.onnx",
                "speaker_encoder.onnx",
                "voice_encoder.onnx",
                "models/voice_encoder.onnx",
                "resemblyzer.onnx",
                "models/resemblyzer.onnx"
            )

            val assetManager = context.assets
            val foundAsset = candidatePaths.firstOrNull { path ->
                try {
                    assetManager.open(path).close()
                    true
                } catch (e: Exception) {
                    false
                }
            }

            if (foundAsset != null) {
                val modelBytes = assetManager.open(foundAsset).use { it.readBytes() }
                val env = OrtEnvironment.getEnvironment()
                val session = env.createSession(modelBytes)
                Result.success(OnnxSpeakerEncoder(session, env))
            } else {
                Result.failure(
                    FileNotFoundException(
                        "Voice encoder model unavailable: Missing model asset 'voice_encoder.onnx' in app assets. Real biometric neural inference requires the Resemblyzer GE2E ONNX asset."
                    )
                )
            }
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }
}
