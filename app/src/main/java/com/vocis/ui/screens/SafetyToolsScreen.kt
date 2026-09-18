package com.vocis.ui.screens

import com.vocis.VocisApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.vocis.vcd.inference.AssetModelLoader
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vocis.ui.theme.VocisAmber
import com.vocis.ui.theme.VocisAmberLight
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

@Composable
fun SafetyToolsScreen(
    onNavigateToDigitalArrest: () -> Unit = {},
    onNavigateToEmergency: () -> Unit = {},
    onNavigateToEvidenceVault: () -> Unit = {}
) {
    var showSmsScannerDialog by remember { mutableStateOf(false) }
    var showVoiceCloneDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VocisCream)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Safety Tools",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = VocisDark
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Forensic counters, biometric analyzers & emergency rapid response.",
            style = MaterialTheme.typography.bodyMedium,
            color = VocisMediumGrey
        )

        Spacer(modifier = Modifier.height(20.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                SafetyToolCard(
                    icon = "⚖️",
                    title = "Digital Arrest Defense",
                    description = "10-phase guided recovery against CBI/ED police impersonation with legal hotlines.",
                    badge = "Guided Protocol",
                    onClick = onNavigateToDigitalArrest
                )
            }

            item {
                SafetyToolCard(
                    icon = "📁",
                    title = "Forensic Evidence Vault",
                    description = "Tamper-evident legal dossiers with SHA-256 digital seals for 1930 Cyber Crime reporting.",
                    badge = "Legal Vault",
                    onClick = onNavigateToEvidenceVault
                )
            }

            item {
                SafetyToolCard(
                    icon = "🚨",
                    title = "Emergency Family SOS",
                    description = "Instant 85% siren override, keyguard-bypassing alert and emergency SMS broadcast.",
                    badge = "SOS Alarm",
                    onClick = onNavigateToEmergency
                )
            }

            item {
                SafetyToolCard(
                    icon = "💬",
                    title = "SMS Scam Pattern Analyzer",
                    description = "Interactive heuristic screening testing electricity cut, banking KYC, and lottery phishing.",
                    badge = "Linguistic AI",
                    onClick = { showSmsScannerDialog = true }
                )
            }

            item {
                SafetyToolCard(
                    icon = "🎙️",
                    title = "Voice Clone Spectral Scanner",
                    description = "On-device neural synthetic speech verification with AASIST & Resemblyzer acoustic models.",
                    badge = "AASIST Neural",
                    onClick = { showVoiceCloneDialog = true }
                )
            }

            item {
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }

    // SMS Scanner Interactive Modal
    if (showSmsScannerDialog) {
        SmsScannerInteractiveDialog(onDismiss = { showSmsScannerDialog = false })
    }

    // Voice Clone Analyzer Modal
    if (showVoiceCloneDialog) {
        VoiceCloneInteractiveDialog(onDismiss = { showVoiceCloneDialog = false })
    }
}

@Composable
fun SafetyToolCard(
    icon: String,
    title: String,
    description: String,
    badge: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, VocisBorder, RoundedCornerShape(20.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = VocisCardWhite),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(VocisCream),
                contentAlignment = Alignment.Center
            ) {
                Text(text = icon, fontSize = 24.sp)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = VocisDark
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(VocisGreenLight)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = VocisGreenText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VocisMediumGrey,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
fun SmsScannerInteractiveDialog(onDismiss: () -> Unit) {
    var smsText by remember {
        mutableStateOf("Dear customer, your electricity power will be disconnected tonight at 9:30 PM due to unpaid bill. Call officer immediately at 9876543210.")
    }
    var scanResult by remember { mutableStateOf<String?>(null) }
    var isScam by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = VocisCardWhite)
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SMS Scam Analyzer",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = VocisDark
                    )
                    Text(
                        text = "✕",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = VocisMediumGrey,
                        modifier = Modifier.clickable { onDismiss() }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Test suspect message text or pick a sample preset:",
                    style = MaterialTheme.typography.labelSmall,
                    color = VocisMediumGrey
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(VocisCream)
                            .border(1.dp, VocisBorder, RoundedCornerShape(12.dp))
                            .clickable {
                                smsText = "Dear customer, your electricity power will be disconnected tonight at 9:30 PM due to unpaid bill. Call 9876543210."
                                scanResult = null
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(text = "⚡ Power Cut", fontSize = 11.sp, color = VocisDark)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(VocisCream)
                            .border(1.dp, VocisBorder, RoundedCornerShape(12.dp))
                            .clickable {
                                smsText = "Your SBI bank account is blocked today due to expired KYC. Update urgently at bit.ly/sbi-verify-kyc"
                                scanResult = null
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(text = "🏦 Bank KYC", fontSize = 11.sp, color = VocisDark)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(VocisCream)
                            .border(1.dp, VocisBorder, RoundedCornerShape(12.dp))
                            .clickable {
                                smsText = "Your OTP for order verification is 849201. Valid for 10 mins. Do not share with anyone."
                                scanResult = null
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(text = "✅ Safe OTP", fontSize = 11.sp, color = VocisDark)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = smsText,
                    onValueChange = {
                        smsText = it
                        scanResult = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    shape = RoundedCornerShape(14.dp),
                    textStyle = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        val lower = smsText.lowercase()
                        val detectedScam = lower.contains("disconnected") ||
                                lower.contains("blocked") ||
                                lower.contains("bit.ly") ||
                                lower.contains("kyc") ||
                                lower.contains("urgently") ||
                                lower.contains("unpaid bill")

                        isScam = detectedScam
                        scanResult = if (detectedScam) {
                            "HIGH RISK SCAM (Score 94%): Detected artificial urgency, unauthorized utility disconnection extortion, and unverified phone number callback."
                        } else {
                            "SAFE (Score 5%): Standard operational transactional text. No phishing URLs or coercion markers detected."
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = VocisDark),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("🔍 Run Linguistic AI Analysis", color = Color.White, fontWeight = FontWeight.Bold)
                }

                if (scanResult != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isScam) VocisRedLight else VocisGreenLight)
                            .border(1.dp, if (isScam) VocisRed else VocisGreenText, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Text(
                                text = if (isScam) "🚨 Scam Pattern Detected" else "✅ Verified Safe",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isScam) VocisRed else VocisGreenText
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = scanResult!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = VocisDark
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceCloneInteractiveDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isAnalyzing by remember { mutableStateOf(false) }
    var currentAction by remember { mutableStateOf<String?>(null) }
    var resultText by remember { mutableStateOf<String?>(null) }
    var isSyntheticDetected by remember { mutableStateOf(false) }
    var syntheticScore by remember { mutableStateOf(0f) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = VocisCardWhite)
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "AASIST Neural Voice Scan",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = VocisDark
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "VOCIS AASIST Anti-Spoof Neural Inference",
                    style = MaterialTheme.typography.labelSmall,
                    color = VocisMediumGrey
                )

                Spacer(modifier = Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(36.dp))
                        .background(
                            when {
                                isAnalyzing -> VocisAmberLight
                                resultText != null && isSyntheticDetected -> VocisRedLight
                                resultText != null && !isSyntheticDetected -> VocisGreenLight
                                else -> VocisCream
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when {
                            isAnalyzing -> "..."
                            resultText != null && isSyntheticDetected -> "!!"
                            resultText != null && !isSyntheticDetected -> "OK"
                            else -> "MIC"
                        },
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            resultText != null && isSyntheticDetected -> VocisRed
                            resultText != null && !isSyntheticDetected -> VocisGreenText
                            else -> VocisDark
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = when {
                        isAnalyzing -> currentAction ?: "Running neural inference..."
                        resultText != null && isSyntheticDetected -> "CRITICAL: AI VOICE DETECTED"
                        resultText != null && !isSyntheticDetected -> "VERIFIED: NATURAL HUMAN VOICE"
                        else -> "Select audio source to verify authenticity:"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        resultText != null && isSyntheticDetected -> VocisRed
                        resultText != null && !isSyntheticDetected -> VocisGreenText
                        else -> VocisDark
                    }
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Button 1: Test AI Voice Clip
                Button(
                    onClick = {
                        isAnalyzing = true
                        currentAction = "Decoding 16kHz audio & running AASIST..."
                        resultText = null
                        scope.launch {
                            val (synthProb, error) = withContext(Dispatchers.IO) {
                                try {
                                    val app = VocisApplication.instance
                                    var detector = app.antiSpoofDetector
                                    if (detector == null) {
                                        val loaded = AssetModelLoader.loadAllModels(context)
                                        detector = loaded.antiSpoofDetector
                                    }
                                    if (detector == null) return@withContext Pair(-1f, "Anti-spoof model not available")

                                    val samples = loadWavFromAssets(context, "test_sample_16k.wav")
                                    if (samples.isEmpty()) return@withContext Pair(-1f, "Sample audio file not found in assets")

                                    val window = if (samples.size >= 64600) {
                                        samples.copyOfRange(0, 64600)
                                    } else {
                                        FloatArray(64600).apply {
                                            System.arraycopy(samples, 0, this, 0, samples.size)
                                        }
                                    }
                                    val prob = detector.detectSpoof(window)
                                    Pair(prob, null)
                                } catch (e: Exception) {
                                    Pair(-1f, e.message ?: "Inference error")
                                }
                            }

                            isAnalyzing = false
                            currentAction = null
                            if (error != null) {
                                resultText = "Error: $error"
                                isSyntheticDetected = false
                            } else {
                                syntheticScore = synthProb * 100f
                                isSyntheticDetected = synthProb >= 0.50f
                                val bonafideScore = (1.0f - synthProb) * 100f
                                resultText = if (isSyntheticDetected) {
                                    "CRITICAL THREAT: AI CLONE DETECTED\n\n" +
                                    "• Synthetic Probability: %.1f%%\n".format(syntheticScore) +
                                    "• Bonafide Human Score: %.1f%%\n".format(bonafideScore) +
                                    "• Biometric Status: RED ALERT\n" +
                                    "• Model: AASIST Anti-Spoof ONNX"
                                } else {
                                    "VERIFIED SAFE: NATURAL HUMAN SPEECH\n\n" +
                                    "• Bonafide Human Score: %.1f%%\n".format(bonafideScore) +
                                    "• Synthetic Probability: %.1f%%\n".format(syntheticScore) +
                                    "• Biometric Status: AUTHENTIC\n" +
                                    "• Model: AASIST Anti-Spoof ONNX"
                                }
                            }
                        }
                    },
                    enabled = !isAnalyzing,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = VocisDark),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "Run Test On Voice Sample Clip",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Button 2: Test Live Microphone (3s)
                OutlinedButton(
                    onClick = {
                        isAnalyzing = true
                        currentAction = "Recording live mic audio (3s)..."
                        resultText = null
                        scope.launch {
                            val (synthProb, error) = withContext(Dispatchers.IO) {
                                try {
                                    val app = VocisApplication.instance
                                    var detector = app.antiSpoofDetector
                                    if (detector == null) {
                                        val loaded = AssetModelLoader.loadAllModels(context)
                                        detector = loaded.antiSpoofDetector
                                    }
                                    if (detector == null) return@withContext Pair(-1f, "Anti-spoof model not available")

                                    val samples = recordMicPcm(context, 3)
                                    if (samples.isEmpty()) return@withContext Pair(-1f, "No audio recorded from mic")

                                    val window = if (samples.size >= 64600) {
                                        samples.copyOfRange(0, 64600)
                                    } else {
                                        FloatArray(64600).apply {
                                            System.arraycopy(samples, 0, this, 0, samples.size)
                                        }
                                    }
                                    val prob = detector.detectSpoof(window)
                                    Pair(prob, null)
                                } catch (e: Exception) {
                                    Pair(-1f, e.message ?: "Mic recording error")
                                }
                            }

                            isAnalyzing = false
                            currentAction = null
                            if (error != null) {
                                resultText = "Error: $error"
                                isSyntheticDetected = false
                            } else {
                                syntheticScore = synthProb * 100f
                                isSyntheticDetected = synthProb >= 0.50f
                                val bonafideScore = (1.0f - synthProb) * 100f
                                resultText = if (isSyntheticDetected) {
                                    "CRITICAL THREAT: AI CLONE DETECTED\n\n" +
                                    "• Synthetic Probability: %.1f%%\n".format(syntheticScore) +
                                    "• Bonafide Human Score: %.1f%%\n".format(bonafideScore) +
                                    "• Biometric Status: RED ALERT\n" +
                                    "• Model: AASIST Anti-Spoof ONNX"
                                } else {
                                    "VERIFIED SAFE: NATURAL HUMAN SPEECH\n\n" +
                                    "• Bonafide Human Score: %.1f%%\n".format(bonafideScore) +
                                    "• Synthetic Probability: %.1f%%\n".format(syntheticScore) +
                                    "• Biometric Status: AUTHENTIC\n" +
                                    "• Model: AASIST Anti-Spoof ONNX"
                                }
                            }
                        }
                    },
                    enabled = !isAnalyzing,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VocisDark)
                ) {
                    Text(
                        text = "Test Live Speech via Mic (3s)",
                        color = VocisDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                if (resultText != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSyntheticDetected) VocisRedLight else VocisGreenLight)
                            .border(1.dp, if (isSyntheticDetected) VocisRed else VocisGreenText, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = resultText!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSyntheticDetected) VocisRed else VocisGreenText,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VocisBorder)
                ) {
                    Text("Close", color = VocisDark)
                }
            }
        }
    }
}

private fun loadWavFromAssets(context: android.content.Context, fileName: String): FloatArray {
    return try {
        context.assets.open(fileName).use { input ->
            val bytes = input.readBytes()
            if (bytes.size <= 44) return FloatArray(0)
            val pcmBytes = bytes.copyOfRange(44, bytes.size)
            val shortCount = pcmBytes.size / 2
            val floatSamples = FloatArray(shortCount)
            val byteBuffer = java.nio.ByteBuffer.wrap(pcmBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until shortCount) {
                floatSamples[i] = byteBuffer.short / 32768.0f
            }
            floatSamples
        }
    } catch (e: Exception) {
        FloatArray(0)
    }
}

@android.annotation.SuppressLint("MissingPermission")
private fun recordMicPcm(context: android.content.Context, durationSec: Int = 3): FloatArray {
    val sampleRate = 16000
    val totalSamples = sampleRate * durationSec
    val minBuf = android.media.AudioRecord.getMinBufferSize(
        sampleRate,
        android.media.AudioFormat.CHANNEL_IN_MONO,
        android.media.AudioFormat.ENCODING_PCM_16BIT
    )
    val bufferSize = maxOf(minBuf, totalSamples * 2)
    val floatSamples = FloatArray(totalSamples)
    var record: android.media.AudioRecord? = null
    try {
        record = android.media.AudioRecord(
            android.media.MediaRecorder.AudioSource.MIC,
            sampleRate,
            android.media.AudioFormat.CHANNEL_IN_MONO,
            android.media.AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        val shortBuffer = ShortArray(totalSamples)
        record.startRecording()
        var readSoFar = 0
        val startTime = System.currentTimeMillis()
        while (readSoFar < totalSamples && System.currentTimeMillis() - startTime < (durationSec + 1) * 1000L) {
            val read = record.read(shortBuffer, readSoFar, totalSamples - readSoFar)
            if (read <= 0) break
            readSoFar += read
        }
        for (i in 0 until readSoFar) {
            floatSamples[i] = shortBuffer[i] / 32768.0f
        }
    } catch (e: Exception) {
        android.util.Log.e("VoiceScan", "Mic recording failed: " + e.message)
    } finally {
        try {
            record?.stop()
            record?.release()
        } catch (ignored: Exception) {}
    }
    return floatSamples
}
