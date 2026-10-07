package com.vocis.vcd.audio

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import com.vocis.vcd.domain.VcdConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Microphone audio capture engine at 16 kHz mono 16-bit PCM.
 * Directly ported and adapted from VOCIS low-latency MicCapture implementation.
 *
 * Scope: Uses MediaRecorder.AudioSource.MIC to capture loudspeaker acoustic output
 * during active phone calls without requiring privileged platform signatures.
 */
class MicCapture(private val context: Context) {

    enum class CaptureStatus {
        IDLE,
        RECORDING,
        PERMISSION_DENIED,
        UNSUPPORTED_HARDWARE,
        READ_ERROR
    }

    data class State(
        val isRunning: Boolean = false,
        val status: CaptureStatus = CaptureStatus.IDLE,
        val rms: Float = 0f,
        val peak: Float = 0f,
        val totalSamples: Long = 0L,
        val errorMessage: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var isCapturing: Boolean = false
    private var captureThread: Thread? = null

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(onAudioFrame: (FloatArray, Int) -> Unit): Boolean {
        if (isCapturing) return true

        val minBufferSize = AudioRecord.getMinBufferSize(
            VcdConstants.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (minBufferSize <= 0) {
            _state.value = _state.value.copy(
                isRunning = false,
                status = CaptureStatus.UNSUPPORTED_HARDWARE,
                errorMessage = "Device does not support 16 kHz mono 16-bit PCM recording"
            )
            return false
        }

        val bufferSize = minBufferSize.coerceAtLeast(VcdConstants.SAMPLE_RATE / 10 * 2) // at least 100ms buffer
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                VcdConstants.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (e: SecurityException) {
            _state.value = _state.value.copy(
                isRunning = false,
                status = CaptureStatus.PERMISSION_DENIED,
                errorMessage = e.message
            )
            return false
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isRunning = false,
                status = CaptureStatus.READ_ERROR,
                errorMessage = e.message
            )
            return false
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            _state.value = _state.value.copy(
                isRunning = false,
                status = CaptureStatus.UNSUPPORTED_HARDWARE,
                errorMessage = "AudioRecord failed to initialize"
            )
            return false
        }

        audioRecord = record
        isCapturing = true

        record.startRecording()
        _state.value = State(isRunning = true, status = CaptureStatus.RECORDING)

        captureThread = thread(name = "Vocis-MicCapture", priority = Thread.MAX_PRIORITY) {
            val shortBuffer = ShortArray(1600) // 100ms chunk at 16kHz
            val floatBuffer = FloatArray(1600)
            var totalSamplesRead = 0L

            while (isCapturing) {
                val rec = audioRecord ?: break
                val readCount = rec.read(shortBuffer, 0, shortBuffer.size)
                if (readCount > 0) {
                    var sumSquare = 0.0
                    var peak = 0.0f
                    for (i in 0 until readCount) {
                        val sample = shortBuffer[i] / 32768.0f
                        floatBuffer[i] = sample
                        sumSquare += (sample * sample)
                        val absVal = abs(sample)
                        if (absVal > peak) peak = absVal
                    }

                    val rms = sqrt(sumSquare / readCount).toFloat()
                    totalSamplesRead += readCount

                    _state.value = _state.value.copy(
                        rms = rms,
                        peak = peak,
                        totalSamples = totalSamplesRead
                    )

                    onAudioFrame(floatBuffer, readCount)
                } else if (readCount < 0) {
                    Log.w("MicCapture", "AudioRecord.read returned error code: ")
                }
            }
        }

        return true
    }

    fun stop() {
        if (!isCapturing) return
        isCapturing = false

        try {
            audioRecord?.apply {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.w("MicCapture", "Error stopping AudioRecord: ")
        } finally {
            audioRecord = null
            captureThread = null
            _state.value = State(isRunning = false, status = CaptureStatus.IDLE)
        }
    }
}
