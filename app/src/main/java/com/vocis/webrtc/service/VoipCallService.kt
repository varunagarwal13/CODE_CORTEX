package com.vocis.webrtc.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.vocis.VocisApplication
import com.vocis.vcd.LiveVerificationPipeline
import com.vocis.vcd.audio.AudioRingBuffer
import com.vocis.vcd.audio.WindowSlicingEngine
import com.vocis.vcd.domain.VcdVerdict
import com.vocis.vcd.fusion.BaselineCalibrator
import com.vocis.vcd.fusion.BiometricFusionEngine
import com.vocis.vcd.fusion.SessionScores
import com.vocis.webrtc.media.VoipCallState
import com.vocis.webrtc.media.VoipMediaEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android Foreground Service managing the WebRTC VoIP call lifecycle, audio tapping,
 * and real-time Voice Clone Defence (VCD) & Vosk speech recognition on remote peer PCM.
 */
class VoipCallService : Service() {

    private val isRunning = AtomicBoolean(false)
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    lateinit var mediaEngine: VoipMediaEngine
        private set

    private val ringBuffer = AudioRingBuffer(160000)
    private val windowSlicingEngine = WindowSlicingEngine()
    private val baselineCalibrator = BaselineCalibrator()
    private val sessionScores = SessionScores()
    private val fusionEngine = BiometricFusionEngine()

    private var pcmStreamJob: Job? = null
    private var floatStreamJob: Job? = null
    private var verificationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaEngine = VoipMediaEngine(this)
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val peerName = intent?.getStringExtra(EXTRA_PEER_NAME) ?: "VOCIS Peer"
        val notification = buildForegroundNotification(peerName)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startVoipPipeline(peerName)

        return START_NOT_STICKY
    }

    private fun startVoipPipeline(peerName: String) {
        if (isRunning.getAndSet(true)) return

        val app = try { VocisApplication.instance } catch (_: Exception) { null }
        app?.liveCallCoordinator?.startSession()

        try {
            mediaEngine.initialize()
            mediaEngine.setCallState(VoipCallState.CONNECTED)
            Log.i(TAG, "VoipMediaEngine initialized and connected for $peerName")
        } catch (e: Exception) {
            Log.w(TAG, "VoipMediaEngine initialization note: ${e.message}")
        }

        // 1. Pipe 16kHz PCM stream to Vosk offline speech recognizer
        pcmStreamJob = serviceScope.launch {
            mediaEngine.remotePcmStream.collect { pcmBytes ->
                app?.speechRecognizer?.acceptWaveform(pcmBytes, pcmBytes.size)
            }
        }

        // 2. Buffer 16kHz float stream into AudioRingBuffer for VCD neural inference
        floatStreamJob = serviceScope.launch {
            mediaEngine.remoteFloatStream.collect { floatSamples ->
                ringBuffer.write(floatSamples, 0, floatSamples.size)
            }
        }

        // 3. Launch live VCD verification pipeline if models are loaded
        verificationJob = serviceScope.launch {
            val encoder = app?.speakerEncoder
            val detector = app?.antiSpoofDetector
            val vault = app?.biometricCryptoVault

            if (encoder != null && detector != null && vault != null) {
                val pipeline = LiveVerificationPipeline(
                    ringBuffer = ringBuffer,
                    windowSlicingEngine = windowSlicingEngine,
                    speakerEncoder = encoder,
                    antiSpoofDetector = detector,
                    baselineCalibrator = baselineCalibrator,
                    sessionScores = sessionScores,
                    fusionEngine = fusionEngine,
                    cryptoVault = vault
                )

                val flow = pipeline.startUnknownSpeakerVerification { isRunning.get() }
                flow.collect { result ->
                    app.liveCallCoordinator?.onVcdResult(result)
                    val threatScore = (result.syntheticProbability * 100).toInt().coerceIn(0, 100)
                    val status = when (result.verdict) {
                        VcdVerdict.CRITICAL_CLONE_DETECTED -> "CRITICAL CLONE DETECTED"
                        VcdVerdict.CRITICAL_UNKNOWN_SYNTHETIC -> "SYNTHETIC AUDIO WARNING"
                        VcdVerdict.SAFE_VERIFIED_AUTHENTIC -> "Verified Speaker (Bonafide)"
                        VcdVerdict.UNCERTAIN -> "Screening Remote Audio..."
                        else -> "VoIP Protection Active"
                    }
                    app.protectionOverlayManager.updateRisk(threatScore, status)
                }
            } else {
                Log.w(TAG, "Neural models still loading for VoIP stream; audio buffered in ring buffer")
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VOCIS VoIP Call",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing encrypted peer-to-peer VoIP call"
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(peerName: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VOCIS Encrypted Call")
            .setContentText("Connected to $peerName")
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        isRunning.set(false)
        pcmStreamJob?.cancel()
        floatStreamJob?.cancel()
        verificationJob?.cancel()
        serviceScope.cancel()

        try {
            mediaEngine.close()
            ringBuffer.clear()
            baselineCalibrator.reset()
            sessionScores.reset()
        } catch (e: Exception) {
            Log.w(TAG, "VoIP teardown note: ${e.message}")
        }

        val app = try { VocisApplication.instance } catch (_: Exception) { null }
        app?.liveCallCoordinator?.reset()
        if (instance == this) instance = null

        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "VoipCallService"
        const val CHANNEL_ID = "vocis_voip_channel"
        const val NOTIFICATION_ID = 2002
        const val EXTRA_PEER_NAME = "extra_peer_name"

        var instance: VoipCallService? = null
            private set

        fun start(context: Context, peerName: String) {
            val intent = Intent(context, VoipCallService::class.java).apply {
                putExtra(EXTRA_PEER_NAME, peerName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VoipCallService::class.java))
        }
    }
}
