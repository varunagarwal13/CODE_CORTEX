package com.vocis.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.vocis.core.data.dao.ContactVoiceprintDao
import com.vocis.intelligence.identity.E164Normalizer
import com.vocis.ui.theme.VocisBorder
import com.vocis.ui.theme.VocisCardWhite
import com.vocis.ui.theme.VocisCream
import com.vocis.ui.theme.VocisDark
import com.vocis.ui.theme.VocisGreen
import com.vocis.ui.theme.VocisGreenLight
import com.vocis.ui.theme.VocisGreenText
import com.vocis.ui.theme.VocisMediumGrey
import com.vocis.ui.theme.VocisRed
import com.vocis.ui.theme.VocisRedLight
import com.vocis.vcd.audio.VoiceSampleRecorder
import com.vocis.vcd.crypto.KeystoreBiometricCryptoVault
import com.vocis.vcd.domain.VcdConstants
import com.vocis.vcd.enrollment.VoiceEnrollmentManager
import com.vocis.vcd.inference.SpeakerEncoderProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

enum class EnrollmentStep {
    CONTACT_INFO,
    SAMPLE_1,
    SAMPLE_2,
    SAMPLE_3,
    PROCESSING,
    SUCCESS,
    ERROR
}

@Composable
fun VoiceEnrollmentDialog(
    dao: ContactVoiceprintDao?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentStep by remember { mutableStateOf(EnrollmentStep.CONTACT_INFO) }
    var contactName by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("Family Contact") }
    var phoneNumber by remember { mutableStateOf("") }

    val capturedSamples = remember { mutableListOf<FloatArray>() }
    var currentSampleCaptured by remember { mutableStateOf<FloatArray?>(null) }

    var isRecording by remember { mutableStateOf(false) }
    var recordElapsedSeconds by remember { mutableIntStateOf(0) }
    var currentAmplitude by remember { mutableFloatStateOf(0f) }

    var statusErrorMessage by remember { mutableStateOf<String?>(null) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val recorder = remember { VoiceSampleRecorder() }

    // Cleanup microphone on dispose to ensure mic is never orphaned
    DisposableEffect(Unit) {
        onDispose {
            recorder.cancelRecording()
        }
    }

    // Timer effect while recording
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordElapsedSeconds = 0
            while (isRecording) {
                delay(1000)
                recordElapsedSeconds++
            }
        }
    }

    // Permission launcher for RECORD_AUDIO
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            statusErrorMessage = null
            val startRes = recorder.startRecording(coroutineScope) { amp ->
                currentAmplitude = amp
            }
            if (startRes.isSuccess) {
                isRecording = true
            } else {
                statusErrorMessage = "Failed to start recording: ${startRes.exceptionOrNull()?.localizedMessage}"
            }
        } else {
            statusErrorMessage = "Microphone permission is required to record voice samples. Please grant permission."
        }
    }

    fun startRecordingFlow() {
        statusErrorMessage = null
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            val startRes = recorder.startRecording(coroutineScope) { amp ->
                currentAmplitude = amp
            }
            if (startRes.isSuccess) {
                isRecording = true
            } else {
                statusErrorMessage = "Failed to initialize microphone: ${startRes.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun stopRecordingFlow() {
        val stopRes = recorder.stopRecording()
        isRecording = false
        if (stopRes.isSuccess) {
            currentSampleCaptured = stopRes.getOrNull()
            statusErrorMessage = null
        } else {
            currentSampleCaptured = null
            statusErrorMessage = stopRes.exceptionOrNull()?.localizedMessage
                ?: "Recording failed. Please ensure you speak clearly for at least 3 seconds."
        }
    }

    fun executeEnrollmentPipeline() {
        currentStep = EnrollmentStep.PROCESSING
        statusErrorMessage = null

        coroutineScope.launch {
            if (dao == null) {
                statusErrorMessage = "Database connection unavailable. Please restart the app."
                currentStep = EnrollmentStep.ERROR
                return@launch
            }

            if (capturedSamples.size != 3) {
                statusErrorMessage = "Enrollment requires 3 completed voice samples. Captured: ${capturedSamples.size}"
                currentStep = EnrollmentStep.ERROR
                return@launch
            }

            // Step 1: Resolve production SpeakerEncoderModel (Resemblyzer GE2E)
            val encoderResult = SpeakerEncoderProvider.getSpeakerEncoder(context)
            if (encoderResult.isFailure) {
                // Truthful failure report when ONNX model asset is missing
                statusErrorMessage = encoderResult.exceptionOrNull()?.localizedMessage
                    ?: "Voice encoder model unavailable: Missing model asset 'voice_encoder.onnx'."
                currentStep = EnrollmentStep.ERROR
                return@launch
            }

            val speakerEncoder = encoderResult.getOrThrow()

            try {
                // Step 2: Initialize hardware-backed biometric vault and enrollment manager
                val cryptoVault = KeystoreBiometricCryptoVault()
                val enrollmentManager = VoiceEnrollmentManager(
                    speakerEncoder = speakerEncoder,
                    cryptoVault = cryptoVault,
                    pairwiseSimilarityThreshold = VcdConstants.PAIRWISE_SIMILARITY_THRESHOLD
                )

                // Step 3: Run enrollment, pairwise cosine similarity validation, centroid generation, and DB insertion
                val result = enrollmentManager.enrollAndPersist(
                    dao = dao,
                    name = contactName.trim(),
                    phoneNumber = E164Normalizer.normalize(phoneNumber.trim()),
                    utterances = capturedSamples,
                    relationship = relationship.trim()
                )

                when (result) {
                    is VoiceEnrollmentManager.EnrollmentResult.Success -> {
                        currentStep = EnrollmentStep.SUCCESS
                    }
                    is VoiceEnrollmentManager.EnrollmentResult.Failure -> {
                        statusErrorMessage = result.reason
                        currentStep = EnrollmentStep.ERROR
                    }
                }
            } catch (e: Exception) {
                statusErrorMessage = "Enrollment error: ${e.localizedMessage ?: "Unexpected error during biometric enrollment"}"
                currentStep = EnrollmentStep.ERROR
            }
        }
    }

    // Pulse animation for recording badge
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Dialog(onDismissRequest = {
        recorder.cancelRecording()
        onDismiss()
    }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = VocisCardWhite),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (currentStep) {
                    EnrollmentStep.CONTACT_INFO -> {
                        Text(
                            text = "Enroll Voice Profile",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = VocisDark
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Step 1 of 2: Enter contact details",
                            style = MaterialTheme.typography.bodySmall,
                            color = VocisMediumGrey
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        if (!validationError.isNullOrBlank()) {
                            Text(
                                text = validationError!!,
                                color = VocisRed,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        OutlinedTextField(
                            value = contactName,
                            onValueChange = {
                                contactName = it
                                validationError = null
                            },
                            label = { Text("Contact Name (e.g. Mom, Alice)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = relationship,
                            onValueChange = { relationship = it },
                            label = { Text("Relationship (e.g. Parent, Spouse)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = {
                                phoneNumber = it
                                validationError = null
                            },
                            label = { Text("Phone Number (+91...)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    recorder.cancelRecording()
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Cancel", color = VocisDark)
                            }

                            Button(
                                onClick = {
                                    val trimmedName = contactName.trim()
                                    val trimmedPhone = phoneNumber.trim()
                                    if (trimmedName.isBlank()) {
                                        validationError = "Please enter a contact name."
                                        return@Button
                                    }
                                    if (trimmedPhone.isBlank()) {
                                        validationError = "Please enter a valid phone number."
                                        return@Button
                                    }
                                    capturedSamples.clear()
                                    currentSampleCaptured = null
                                    statusErrorMessage = null
                                    currentStep = EnrollmentStep.SAMPLE_1
                                },
                                modifier = Modifier.weight(1.5f),
                                colors = ButtonDefaults.buttonColors(containerColor = VocisDark),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Continue →", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    EnrollmentStep.SAMPLE_1,
                    EnrollmentStep.SAMPLE_2,
                    EnrollmentStep.SAMPLE_3 -> {
                        val sampleIndex = when (currentStep) {
                            EnrollmentStep.SAMPLE_1 -> 1
                            EnrollmentStep.SAMPLE_2 -> 2
                            else -> 3
                        }

                        Text(
                            text = "Voice Sample $sampleIndex of 3",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = VocisDark
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Ask $contactName to speak continuously for 3–5 seconds.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VocisMediumGrey,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Progress step indicators
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (i in 1..3) {
                                val isDone = i < sampleIndex || (i == sampleIndex && currentSampleCaptured != null)
                                val isCurrent = i == sampleIndex
                                Box(
                                    modifier = Modifier
                                        .size(if (isCurrent) 12.dp else 10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isDone -> VocisGreen
                                                isCurrent -> VocisDark
                                                else -> VocisBorder
                                            }
                                        )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Mic Visualizer / Pulsing circle
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .scale(if (isRecording) pulseScale else 1f)
                                .clip(CircleShape)
                                .background(if (isRecording) VocisRedLight else VocisGreenLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(CircleShape)
                                    .background(if (isRecording) VocisRed else VocisGreen),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isRecording) "⏹️" else "🎙️",
                                    fontSize = 30.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Status message / Timer
                        if (isRecording) {
                            Text(
                                text = "Recording: 00:${String.format(Locale.US, "%02d", recordElapsedSeconds)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = VocisRed
                            )
                            Text(
                                text = "Keep speaking naturally...",
                                style = MaterialTheme.typography.bodySmall,
                                color = VocisMediumGrey
                            )
                        } else if (currentSampleCaptured != null) {
                            val durationSec = currentSampleCaptured!!.size.toFloat() / VcdConstants.SAMPLE_RATE.toFloat()
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(VocisGreenLight)
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "✓ Sample $sampleIndex captured (${String.format(Locale.US, "%.1fs", durationSec)})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = VocisGreenText,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Text(
                                text = "Ready to capture sample $sampleIndex",
                                style = MaterialTheme.typography.bodyMedium,
                                color = VocisMediumGrey
                            )
                        }

                        if (!statusErrorMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(VocisRedLight)
                                    .border(1.dp, VocisRed, RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = statusErrorMessage!!,
                                    color = VocisRed,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Controls
                        if (isRecording) {
                            Button(
                                onClick = { stopRecordingFlow() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = VocisRed),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Stop Recording ⏹️", fontWeight = FontWeight.Bold)
                            }
                        } else if (currentSampleCaptured == null) {
                            Button(
                                onClick = { startRecordingFlow() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = VocisGreen),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Start Recording 🎙️", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        currentSampleCaptured = null
                                        statusErrorMessage = null
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Re-record ↺", color = VocisDark)
                                }

                                Button(
                                    onClick = {
                                        capturedSamples.add(currentSampleCaptured!!)
                                        currentSampleCaptured = null
                                        statusErrorMessage = null

                                        when (currentStep) {
                                            EnrollmentStep.SAMPLE_1 -> currentStep = EnrollmentStep.SAMPLE_2
                                            EnrollmentStep.SAMPLE_2 -> currentStep = EnrollmentStep.SAMPLE_3
                                            EnrollmentStep.SAMPLE_3 -> executeEnrollmentPipeline()
                                            else -> {}
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = VocisDark),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        if (sampleIndex == 3) "Finish →" else "Continue →",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Cancel",
                            color = VocisMediumGrey,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .clickable {
                                    recorder.cancelRecording()
                                    onDismiss()
                                }
                                .padding(8.dp)
                        )
                    }

                    EnrollmentStep.PROCESSING -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        CircularProgressIndicator(
                            color = VocisGreen,
                            modifier = Modifier.size(54.dp),
                            strokeWidth = 4.dp
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Creating secure voice profile…",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = VocisDark
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Extracting 256-dim embedding vector and validating consistency across samples…",
                            style = MaterialTheme.typography.bodySmall,
                            color = VocisMediumGrey,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    EnrollmentStep.ERROR -> {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(VocisRedLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "⚠️", fontSize = 36.sp)
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Enrollment Blocked",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = VocisDark
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = statusErrorMessage ?: "Enrollment could not be completed.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VocisRed,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    recorder.cancelRecording()
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Close", color = VocisDark)
                            }

                            Button(
                                onClick = {
                                    capturedSamples.clear()
                                    currentSampleCaptured = null
                                    statusErrorMessage = null
                                    currentStep = EnrollmentStep.SAMPLE_1
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = VocisDark),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Try Again", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    EnrollmentStep.SUCCESS -> {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(VocisGreenLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "✓", color = VocisGreen, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Voice Profile Enrolled!",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = VocisDark
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "$contactName's voice biometric embedding is securely sealed in the AES-256 hardware vault.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VocisMediumGrey,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = VocisDark),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
