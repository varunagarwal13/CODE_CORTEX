package com.vocis.emergency

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

object EmergencyAlarmSystem {

    const val SAMPLE_RATE = 16000
    const val DURATION_SECONDS = 1
    const val TARGET_VOLUME_RATIO = 0.85f // 85% volume specification

    private val VIBRATION_TIMINGS = longArrayOf(0, 500, 200, 500, 200)
    private val VIBRATION_AMPLITUDES = intArrayOf(0, 255, 0, 255, 0)

    @Volatile
    private var isPlaying = false
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    fun isAlarmPlaying(): Boolean = isPlaying

    @Synchronized
    fun startEmergencyAlarm(context: Context) {
        if (isPlaying) return
        isPlaying = true

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.let { am ->
            try {
                val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                val targetVol = (maxVol * TARGET_VOLUME_RATIO).roundToInt().coerceAtLeast(1)
                am.setStreamVolume(AudioManager.STREAM_ALARM, targetVol, 0)
            } catch (e: Exception) {
                // Ignore volume adjustment restrictions if DND permission is strict
            }
        }

        startVibration(context)
        startAudioLoop()
    }

    @Synchronized
    fun stopEmergencyAlarm(context: Context? = null) {
        if (!isPlaying) return
        isPlaying = false

        playbackJob?.cancel()
        playbackJob = null

        try {
            audioTrack?.apply {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            // AudioTrack cleanup
        } finally {
            audioTrack = null
        }

        context?.let { stopVibration(it) }
    }

    private fun startAudioLoop() {
        playbackJob = scope.launch {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(SAMPLE_RATE * 2)

            val track = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(minBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack = track
            track.play()

            val sirenSamples = generateSirenPcm(SAMPLE_RATE, DURATION_SECONDS)

            try {
                while (isActive && isPlaying) {
                    track.write(sirenSamples, 0, sirenSamples.size)
                }
            } catch (e: Exception) {
                // Loop cancelled or interrupted
            } finally {
                try {
                    track.stop()
                    track.release()
                } catch (ignored: Exception) {}
            }
        }
    }

    private fun startVibration(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val effect = VibrationEffect.createWaveform(VIBRATION_TIMINGS, VIBRATION_AMPLITUDES, 0)
                vibratorManager?.vibrate(CombinedVibration.createParallel(effect))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(VIBRATION_TIMINGS, VIBRATION_AMPLITUDES, 0)
                    vibrator?.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(VIBRATION_TIMINGS, 0)
                }
            }
        } catch (e: Exception) {
            // Vibration permission or device unsupported
        }
    }

    private fun stopVibration(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.cancel()
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.cancel()
            }
        } catch (e: Exception) {
            // Ignore vibration cancel error
        }
    }

    /**
     * Synthesizes a clean two-tone alternating 800Hz / 1200Hz emergency siren PCM buffer.
     */
    fun generateSirenPcm(sampleRate: Int, durationSeconds: Int): ShortArray {
        val totalSamples = sampleRate * durationSeconds
        val buffer = ShortArray(totalSamples)
        val halfDuration = totalSamples / 2

        val freq1 = 800.0  // Low tone
        val freq2 = 1200.0 // High tone

        for (i in 0 until totalSamples) {
            val freq = if (i < halfDuration) freq1 else freq2
            val angle = 2.0 * PI * i * (freq / sampleRate)
            val sample = (sin(angle) * Short.MAX_VALUE * 0.85).toInt().toShort()
            buffer[i] = sample
        }
        return buffer
    }
}
