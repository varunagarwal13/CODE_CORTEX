package com.vocis.vcd.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.vocis.vcd.domain.VcdConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.sqrt

/**
 * Production AudioRecord wrapper specifically designed for capturing clean 16kHz mono
 * PCM utterances for the 3-sample VCD voice enrollment pipeline.
 */
class VoiceSampleRecorder {
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val bufferLock = Any()
    private val byteStream = ByteArrayOutputStream()

    @Volatile
    var isRecording: Boolean = false
        private set

    @Volatile
    var currentAmplitude: Float = 0f
        private set

    @SuppressLint("MissingPermission")
    fun startRecording(
        scope: CoroutineScope,
        onAmplitude: (Float) -> Unit = {}
    ): Result<Unit> {
        synchronized(bufferLock) {
            if (isRecording) {
                return Result.failure(IllegalStateException("Recording is already in progress."))
            }

            val sampleRate = VcdConstants.SAMPLE_RATE
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

            if (minBufferSize <= 0) {
                return Result.failure(IllegalStateException("AudioRecord hardware buffer initialization failed."))
            }

            val bufferSize = maxOf(minBufferSize * 2, 4096)

            val record = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
            } catch (e: Exception) {
                return Result.failure(e)
            }

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return Result.failure(IllegalStateException("Microphone hardware failed to initialize. Check audio permissions."))
            }

            try {
                record.startRecording()
            } catch (e: Exception) {
                record.release()
                return Result.failure(e)
            }

            audioRecord = record
            isRecording = true
            byteStream.reset()

            recordingJob = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(bufferSize)
                while (isActive && isRecording) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        synchronized(bufferLock) {
                            byteStream.write(buffer, 0, read)
                        }
                        // Compute RMS amplitude for visual recording meter feedback
                        var sumSq = 0.0
                        val sampleCount = read / 2
                        for (i in 0 until sampleCount) {
                            val sample = (buffer[i * 2].toInt() and 0xFF) or (buffer[i * 2 + 1].toInt() shl 8)
                            val normalized = sample.toShort().toFloat() / 32768.0f
                            sumSq += (normalized * normalized)
                        }
                        val rms = if (sampleCount > 0) sqrt(sumSq / sampleCount).toFloat() else 0f
                        currentAmplitude = rms
                        onAmplitude(rms)
                    }
                }
            }

            return Result.success(Unit)
        }
    }

    fun stopRecording(): Result<FloatArray> {
        synchronized(bufferLock) {
            if (!isRecording && byteStream.size() == 0) {
                return Result.failure(IllegalStateException("No active recording session to stop."))
            }

            isRecording = false
            recordingJob?.cancel()
            recordingJob = null

            try {
                audioRecord?.stop()
            } catch (e: Exception) {
                // ignore
            }
            try {
                audioRecord?.release()
            } catch (e: Exception) {
                // ignore
            }
            audioRecord = null

            val capturedBytes = byteStream.toByteArray()
            byteStream.reset()
            currentAmplitude = 0f

            if (capturedBytes.isEmpty()) {
                return Result.failure(IllegalArgumentException("Recording captured no audio data. Please try again."))
            }

            // Convert raw PCM 16-bit to normalized 32-bit float array in [-1.0, 1.0]
            val floatSamples = AudioCaptureEngine.pcm16ToNormalizedFloat(capturedBytes)

            // Minimum 2.0s duration check (32,000 samples at 16kHz)
            val minSamples = 32000
            if (floatSamples.size < minSamples) {
                val durationSec = floatSamples.size.toFloat() / VcdConstants.SAMPLE_RATE.toFloat()
                return Result.failure(
                    IllegalArgumentException(
                        String.format(Locale.US, "Voice sample is too short (%.1fs). Please speak for at least 3 seconds.", durationSec)
                    )
                )
            }

            // Silence and signal presence check
            var sumSquares = 0.0
            for (sample in floatSamples) {
                sumSquares += (sample * sample)
            }
            val rms = sqrt(sumSquares / floatSamples.size).toFloat()
            if (rms < VcdConstants.RMS_SILENCE_THRESHOLD) {
                return Result.failure(
                    IllegalArgumentException("Voice sample is silent or too quiet. Please speak clearly into the microphone.")
                )
            }

            return Result.success(floatSamples)
        }
    }

    fun cancelRecording() {
        synchronized(bufferLock) {
            isRecording = false
            recordingJob?.cancel()
            recordingJob = null
            try {
                audioRecord?.stop()
            } catch (e: Exception) {
                // ignore
            }
            try {
                audioRecord?.release()
            } catch (e: Exception) {
                // ignore
            }
            audioRecord = null
            byteStream.reset()
            currentAmplitude = 0f
        }
    }
}
