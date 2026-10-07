package com.vocis.vcd.inference

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import com.vocis.speech.asr.SpeechRecognizerBridge
import com.vocis.speech.asr.VoskSpeechRecognizerBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vosk.Model
import java.io.File

/**
 * Handles unpacking of bundled models from APK assets to internal storage
 * and initializing ONNX Runtime sessions for speaker embedding and anti-spoofing.
 *
 * Pattern directly referenced from VOCIS ModelRuntime and WebRtcSttBridge.
 */
object AssetModelLoader {

    private const val TAG = "AssetModelLoader"

    const val ASSET_SPEAKER_ENCODER = "models/speaker_encoder.onnx"
    const val ASSET_SPOOF_DETECTOR = "models/spoof_detector.onnx"
    const val ASSET_VOSK_EN = "models/vosk-model-en-in"

    data class LoadedModels(
        val speakerEncoder: OnnxSpeakerEncoder,
        val antiSpoofDetector: OnnxAasistDetector,
        val speechRecognizer: SpeechRecognizerBridge?
    )

    suspend fun loadAllModels(context: Context): LoadedModels = withContext(Dispatchers.IO) {
        val env = OrtEnvironment.getEnvironment()
        val sessionOptions = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }

        // 1. Initialize ONNX Speaker Encoder
        Log.i(TAG, "Loading ONNX Speaker Encoder from ...")
        val speakerEncoderBytes = context.assets.open(ASSET_SPEAKER_ENCODER).use { it.readBytes() }
        val speakerSession = env.createSession(speakerEncoderBytes, sessionOptions)
        val speakerEncoder = OnnxSpeakerEncoder(speakerSession, env)

        // 2. Initialize ONNX Anti-Spoofing Detector (AASIST)
        Log.i(TAG, "Loading ONNX Anti-Spoofing Detector from ...")
        val spoofBytes = context.assets.open(ASSET_SPOOF_DETECTOR).use { it.readBytes() }
        val spoofSession = env.createSession(spoofBytes, sessionOptions)
        val antiSpoofDetector = OnnxAasistDetector(spoofSession, env)

        // 3. Unpack and initialize Vosk Kaldi Speech Model (if present)
        var speechRecognizer: SpeechRecognizerBridge? = null
        try {
            Log.i(TAG, "Unpacking Vosk Kaldi model from ...")
            val voskDir = File(context.filesDir, ASSET_VOSK_EN)
            if (!voskDir.exists()) {
                copyAssetDirectory(context, ASSET_VOSK_EN, voskDir)
            }
            if (voskDir.exists()) {
                val voskModel = Model(voskDir.absolutePath)
                speechRecognizer = VoskSpeechRecognizerBridge(voskModel)
                Log.i(TAG, "Vosk Speech Recognizer initialized successfully")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not initialize Vosk Kaldi model: ")
        }

        LoadedModels(
            speakerEncoder = speakerEncoder,
            antiSpoofDetector = antiSpoofDetector,
            speechRecognizer = speechRecognizer
        )
    }

    /**
     * Recursively copies an asset directory tree to device internal filesDir.
     * Vosk Kaldi's JNI layer requires filesystem directories, not raw compressed APK assets.
     */
    private fun copyAssetDirectory(context: Context, assetPath: String, dest: File) {
        val assets = context.assets
        val children = runCatching { assets.list(assetPath) }.getOrNull() ?: return
        if (children.isEmpty()) {
            dest.parentFile?.mkdirs()
            assets.open(assetPath).use { src ->
                dest.outputStream().use { out -> src.copyTo(out) }
            }
        } else {
            dest.mkdirs()
            for (child in children) {
                copyAssetDirectory(context, "$assetPath/$child", File(dest, child))
            }
        }
    }
}
