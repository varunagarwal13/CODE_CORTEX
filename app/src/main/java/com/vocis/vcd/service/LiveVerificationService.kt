package com.vocis.vcd.service

import android.annotation.SuppressLint
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
import com.vocis.sensor.telephony.TelephonyStateMonitor
import com.vocis.vcd.LiveVerificationPipeline
import com.vocis.vcd.audio.AudioRingBuffer
import com.vocis.vcd.audio.MicCapture
import com.vocis.vcd.audio.WindowSlicingEngine
import com.vocis.vcd.domain.ContactVoiceprint
import com.vocis.vcd.domain.VcdVerdict
import com.vocis.vcd.fusion.BaselineCalibrator
import com.vocis.vcd.fusion.BiometricFusionEngine
import com.vocis.vcd.fusion.SessionScores
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android Foreground Service managing microphone capture and live Voice Clone Defence (VCD)
 * during cellular or speakerphone calls.
 * Directly referenced from VOCIS LiveVerificationService & VerificationPipeline.
 */
class LiveVerificationService : Service() {

    companion object {
        const val TAG = "LiveVerificationService"
        const val CHANNEL_ID = "vocis_vcd_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_START = "com.vocis.vcd.action.START_VERIFICATION"
        const val ACTION_STOP = "com.vocis.vcd.action.STOP_VERIFICATION"

        const val EXTRA_PHONE_NUMBER = "com.vocis.vcd.extra.PHONE_NUMBER"
        const val EXTRA_CONTACT_NAME = "com.vocis.vcd.extra.CONTACT_NAME"

        fun start(context: Context, phoneNumber: String? = null, contactName: String? = null) {
            val intent = Intent(context, LiveVerificationService::class.java).apply {
                action = ACTION_START
                phoneNumber?.let { putExtra(EXTRA_PHONE_NUMBER, it) }
                contactName?.let { putExtra(EXTRA_CONTACT_NAME, it) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LiveVerificationService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val isRunning = AtomicBoolean(false)
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    private var micCapture: MicCapture? = null
    private val ringBuffer = AudioRingBuffer()
    private val windowSlicingEngine = WindowSlicingEngine()
    private val baselineCalibrator = BaselineCalibrator()
    private val sessionScores = SessionScores()
    private val fusionEngine = BiometricFusionEngine()

    private var verificationJob: Job? = null

    private var currentPhoneNumber: String? = null
    private var currentContactName: String? = null
    private var callStartTimeMs: Long = 0L
    private var maxSyntheticScore: Float = 0f
    private var lastVerdict: VcdVerdict = VcdVerdict.UNCERTAIN
    private var isAnyCloneFlagged: Boolean = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        micCapture = MicCapture(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val number = intent?.getStringExtra(EXTRA_PHONE_NUMBER)
        val name = intent?.getStringExtra(EXTRA_CONTACT_NAME)
        if (!number.isNullOrBlank()) currentPhoneNumber = number
        if (!name.isNullOrBlank()) currentContactName = name

        when (intent?.action) {
            ACTION_START -> startVerification()
            ACTION_STOP -> stopVerification()
        }
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startVerification() {
        if (isRunning.getAndSet(true)) return

        callStartTimeMs = System.currentTimeMillis()
        maxSyntheticScore = 0f
        isAnyCloneFlagged = false
        lastVerdict = VcdVerdict.UNCERTAIN

        val notification = buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val app = VocisApplication.instance

        // 1. Start hardware microphone capture thread feeding ring buffer and offline ASR
        try {
            micCapture?.start { floatSamples, count ->
                ringBuffer.write(floatSamples, 0, count)
                app.speechRecognizer?.let { asr ->
                    val pcmBytes = ByteArray(count * 2)
                    for (i in 0 until count) {
                        val s = (floatSamples[i] * 32767.0f).toInt().coerceIn(-32768, 32767)
                        pcmBytes[i * 2] = (s and 0xFF).toByte()
                        pcmBytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                    }
                    asr.acceptWaveform(pcmBytes, pcmBytes.size)
                }
            }
            Log.i(TAG, "Hardware microphone capture started at 16kHz")
        } catch (e: Exception) {
            Log.w(TAG, "Mic capture could not start: ${e.message}")
        }

        // 2. Wire and run live VCD pipeline (handles both enrolled & unknown callers)
        verificationJob = serviceScope.launch {
            // Await neural models ready state if background preloading is in progress
            var waitCount = 0
            while ((app.antiSpoofDetector == null || app.speakerEncoder == null) && waitCount < 30 && isRunning.get()) {
                kotlinx.coroutines.delay(100)
                waitCount++
            }

            val speakerEncoder = app.speakerEncoder
            val antiSpoofDetector = app.antiSpoofDetector

            if (speakerEncoder != null && antiSpoofDetector != null) {
                val pipeline = LiveVerificationPipeline(
                    ringBuffer = ringBuffer,
                    windowSlicingEngine = windowSlicingEngine,
                    speakerEncoder = speakerEncoder,
                    antiSpoofDetector = antiSpoofDetector,
                    baselineCalibrator = baselineCalibrator,
                    sessionScores = sessionScores,
                    fusionEngine = fusionEngine,
                    cryptoVault = app.biometricCryptoVault
                )

                val voiceprintDao = app.vcdDatabase.contactVoiceprintDao()
                val targetVoiceprint = if (!currentPhoneNumber.isNullOrBlank()) {
                    voiceprintDao.getByPhoneNumber(currentPhoneNumber!!)?.let { profile ->
                        ContactVoiceprint(
                            contactId = profile.contactId.toString(),
                            contactName = profile.name,
                            phoneNumber = profile.phoneNumber,
                            encryptedEmbedding = profile.voiceprintCipher,
                            iv = profile.iv,
                            baselineSynthetic = if (profile.baselineSynthetic > 0.0f) profile.baselineSynthetic else null
                        )
                    }
                } else null

                app.liveCallCoordinator?.startSession(targetVoiceprint)

                val flow = if (targetVoiceprint != null) {
                    pipeline.startVerification(targetVoiceprint) { isRunning.get() }
                } else {
                    pipeline.startUnknownSpeakerVerification { isRunning.get() }
                }

                flow.collect { result ->
                    handleVerificationResult(result)
                }
            } else {
                Log.w(TAG, "Neural models still loading; audio buffered in 160k ring buffer")
            }
        }
    }

    private fun handleVerificationResult(result: com.vocis.vcd.domain.VcdVerificationResult) {
        val app = VocisApplication.instance
        if (result.syntheticProbability > maxSyntheticScore) {
            maxSyntheticScore = result.syntheticProbability
        }
        lastVerdict = result.verdict

        val isClone = (result.verdict == VcdVerdict.CRITICAL_CLONE_DETECTED ||
                result.verdict == VcdVerdict.CRITICAL_UNKNOWN_SYNTHETIC)
        if (isClone) {
            isAnyCloneFlagged = true
        }

        app.interactionHub.onVoiceCloneVerdict(isClone)
        app.liveCallCoordinator?.onVcdResult(result)

        val threatScore = ((result.syntheticProbability * 100).toInt()).coerceIn(0, 100)
        val status = when (result.verdict) {
            VcdVerdict.CRITICAL_CLONE_DETECTED -> "CRITICAL CLONE DETECTED"
            VcdVerdict.CRITICAL_UNKNOWN_SYNTHETIC -> "SYNTHETIC AUDIO WARNING"
            VcdVerdict.SAFE_VERIFIED_AUTHENTIC -> "Verified Speaker (Bonafide)"
            VcdVerdict.UNCERTAIN -> "Screening Voice..."
            else -> "Call Monitoring Active"
        }

        app.protectionOverlayManager.updateRisk(threatScore, status)
    }

    private fun stopVerification() {
        if (!isRunning.getAndSet(false)) return

        verificationJob?.cancel()
        verificationJob = null

        val app = VocisApplication.instance
        micCapture?.stop()
        ringBuffer.clear()
        baselineCalibrator.reset()
        sessionScores.reset()
        app.liveCallCoordinator?.reset()

        val durationMs = if (callStartTimeMs > 0) System.currentTimeMillis() - callStartTimeMs else 0L
        val finalThreatScore = (maxSyntheticScore * 100).toInt().coerceIn(0, 100)
        val isClone = isAnyCloneFlagged || finalThreatScore >= 70
        val finalVerdictString = when {
            isClone -> "AI Voice Clone Detected (Peak synthetic risk: $finalThreatScore%)"
            finalThreatScore > 35 -> "Elevated Synthetic Risk ($finalThreatScore%)"
            else -> "Acoustically Natural Speech (Bonafide Score: ${100 - finalThreatScore}%)"
        }

        val phoneNum = currentPhoneNumber ?: TelephonyStateMonitor.lastIncomingNumber
        val contactName = currentContactName ?: TelephonyStateMonitor.lastCallerName

        // Record call in Room Interaction database and update live UI StateFlow
        app.interactionHub.recordCallInteraction(
            phoneNumber = phoneNum,
            displayName = contactName,
            threatScore = finalThreatScore,
            isClone = isClone,
            verdictText = finalVerdictString,
            durationMs = durationMs
        )

        // Record call history in VCD database
        serviceScope.launch(Dispatchers.IO) {
            try {
                app.vcdDatabase.vcdCallHistoryDao().insert(
                    com.vocis.core.data.entity.VcdCallHistoryEntity(
                        peerName = contactName ?: phoneNum ?: "Unknown Caller",
                        phoneNumber = phoneNum ?: "",
                        contactLabel = if (contactName != null) "CONTACT" else "UNKNOWN",
                        isOutgoing = false,
                        startedAtEpochMs = if (callStartTimeMs > 0) callStartTimeMs else System.currentTimeMillis(),
                        durationSeconds = durationMs / 1000,
                        peakSyntheticScore = maxSyntheticScore,
                        minSimilarityScore = 0f,
                        finalVerdict = lastVerdict.name,
                        callEnding = "NORMAL"
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "VcdCallHistory save note: ${e.message}")
            }
        }

        // Also sync device system call logs
        app.interactionHub.loadRealCallLogs(this)

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Voice Clone Defence",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active voice biometric verification monitoring"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VOCIS Voice Clone Defence")
            .setContentText("Active voice biometric verification enabled")
            .setSmallIcon(android.R.drawable.stat_sys_speakerphone)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVerification()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

