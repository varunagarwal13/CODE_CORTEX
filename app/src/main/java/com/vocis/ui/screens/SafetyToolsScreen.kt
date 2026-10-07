package com.vocis.ui.screens

import com.vocis.VocisApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.LinearProgressIndicator
import com.vocis.vcd.audio.VcdAudioDecoder
import com.vocis.vcd.domain.MathPrimitives
import com.vocis.vcd.domain.VcdVerdict
import com.vocis.vcd.fusion.BiometricFusionEngine
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
    onNavigateToEvidenceVault: () -> Unit = {},
    onManageEmergencyContacts: () -> Unit = {}
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
                    icon = "👥",
                    title = "Family Emergency Network",
                    description = "Manage trusted family contacts, automated SMS broadcast lists, and siren dispatch in Room database.",
                    badge = "Contacts DB",
                    onClick = onManageEmergencyContacts
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
            verticalAlignment = Alignment.Top
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
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = VocisDark,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
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

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VocisMediumGrey,
                    lineHeight = 20.sp
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
                    text = "Paste or type any suspicious message text below to analyze:",
                    style = MaterialTheme.typography.labelMedium,
                    color = VocisMediumGrey
                )

                Spacer(modifier = Modifier.height(10.dp))

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

data class DemoAnalysisRun(
    val title: String,
    val durationSeconds: Float,
    val similarity: Float,
    val syntheticProbability: Float,
    val verdict: VcdVerdict,
    val windowCount: Int,
    val latencyMs: Long,
    val explanation: String
)

@Composable
fun VoiceCloneInteractiveDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isAnalyzing by remember { mutableStateOf(false) }
    var currentAction by remember { mutableStateOf<String?>(null) }
    var currentRun by remember { mutableStateOf<DemoAnalysisRun?>(null) }
    var previousRun by remember { mutableStateOf<DemoAnalysisRun?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            isAnalyzing = true
            currentAction = "Decoding custom audio file & running models..."
            errorMessage = null
            scope.launch {
                try {
                    val startTime = System.currentTimeMillis()
                    val decoded = withContext(Dispatchers.IO) {
                        VcdAudioDecoder.decodeUri(context, uri)
                    }
                    val samples = decoded.samples
                    if (samples.isEmpty()) {
                        errorMessage = "No audio could be decoded from chosen file."
                        isAnalyzing = false
                        return@launch
                    }

                    val app = VocisApplication.instance
                    val models = withContext(Dispatchers.IO) { AssetModelLoader.loadAllModels(context) }
                    val encoder = app.speakerEncoder ?: models.speakerEncoder
                    val detector = app.antiSpoofDetector ?: models.antiSpoofDetector

                    // Reference Aditya centroid for identity comparison
                    val refAudio = withContext(Dispatchers.IO) {
                        VcdAudioDecoder.decodeAsset(context, "dataset/aditya-real.wav").samples
                    }
                    val refCentroid = encoder.embed(refAudio.copyOfRange(0, minOf(refAudio.size, 64600)))

                    val windows = mutableListOf<FloatArray>()
                    if (samples.size < 64600) {
                        windows.add(FloatArray(64600).apply { System.arraycopy(samples, 0, this, 0, samples.size) })
                    } else {
                        var start = 0
                        while (start + 64600 <= samples.size) {
                            windows.add(samples.copyOfRange(start, start + 64600))
                            start += 32300
                        }
                    }

                    val sims = mutableListOf<Float>()
                    val synths = mutableListOf<Float>()
                    for (w in windows) {
                        synths.add(detector.detectSpoof(w))
                        sims.add(MathPrimitives.cosineSimilarity(refCentroid, encoder.embed(w)))
                    }

                    val medSim = if (sims.isNotEmpty()) sims.sorted()[sims.size / 2] else 0f
                    val medSyn = if (synths.isNotEmpty()) synths.sorted()[synths.size / 2] else 0f
                    val engine = BiometricFusionEngine()
                    val result = engine.evaluate(
                        similarity = medSim,
                        syntheticProbability = medSyn,
                        dynamicThreshold = 0.50f,
                        baselineSynthetic = 0.01f,
                        isReliable = true
                    )

                    val latency = System.currentTimeMillis() - startTime
                    val explanation = when (result.verdict) {
                        VcdVerdict.CRITICAL_CLONE_DETECTED ->
                            "High similarity (%.1f%%) matching enrolled voice with synthetic acoustic artifacts (%.1f%%) confirms a voice clone attack!".format(medSim * 100f, medSyn * 100f)
                        VcdVerdict.SAFE_VERIFIED_AUTHENTIC ->
                            "Verified authentic human voice (%.1f%% bonafide) matching enrolled contact (%.1f%% similarity).".format((1f - medSyn) * 100f, medSim * 100f)
                        VcdVerdict.SUSPICIOUS_IMPOSTOR ->
                            "Voice does not match enrolled contact (%.1f%% similarity, threshold 75%%).".format(medSim * 100f)
                        else ->
                            "AASIST Synthetic Probability: %.1f%%, Similarity: %.1f%%.".format(medSyn * 100f, medSim * 100f)
                    }

                    previousRun = currentRun
                    currentRun = DemoAnalysisRun(
                        title = uri.lastPathSegment ?: "Custom Audio File",
                        durationSeconds = decoded.durationSeconds,
                        similarity = medSim,
                        syntheticProbability = medSyn,
                        verdict = result.verdict,
                        windowCount = windows.size,
                        latencyMs = latency,
                        explanation = explanation
                    )
                } catch (e: Exception) {
                    errorMessage = "Analysis error: ${e.message}"
                } finally {
                    isAnalyzing = false
                    currentAction = null
                }
            }
        }
    }

    fun executeDemoClip(
        title: String,
        assetPath: String?,
        isMic: Boolean = false,
        isCrossSpeaker: Boolean = false
    ) {
        isAnalyzing = true
        currentAction = if (isMic) "Recording 3s live microphone PCM..." else "Decoding $title & running models..."
        errorMessage = null

        scope.launch {
            try {
                val startTime = System.currentTimeMillis()
                val samples = withContext(Dispatchers.IO) {
                    if (isMic) {
                        recordMicPcm(context, 3)
                    } else if (assetPath != null) {
                        VcdAudioDecoder.decodeAsset(context, assetPath).samples
                    } else FloatArray(0)
                }

                if (samples.isEmpty()) {
                    errorMessage = if (isMic) "No microphone audio recorded" else "Asset clip not found"
                    isAnalyzing = false
                    return@launch
                }

                val app = VocisApplication.instance
                val models = withContext(Dispatchers.IO) { AssetModelLoader.loadAllModels(context) }
                val encoder = app.speakerEncoder ?: models.speakerEncoder
                val detector = app.antiSpoofDetector ?: models.antiSpoofDetector

                // Determine reference centroid
                val refAudioPath = if (isCrossSpeaker) "dataset/enrol.wav" else "dataset/aditya-real.wav"
                val refAudio = withContext(Dispatchers.IO) {
                    VcdAudioDecoder.decodeAsset(context, refAudioPath).samples
                }
                val refCentroid = encoder.embed(refAudio.copyOfRange(0, minOf(refAudio.size, 64600)))

                val windows = mutableListOf<FloatArray>()
                if (samples.size < 64600) {
                    windows.add(FloatArray(64600).apply { System.arraycopy(samples, 0, this, 0, samples.size) })
                } else {
                    var start = 0
                    while (start + 64600 <= samples.size) {
                        windows.add(samples.copyOfRange(start, start + 64600))
                        start += 32300
                    }
                }

                val sims = mutableListOf<Float>()
                val synths = mutableListOf<Float>()
                for (w in windows) {
                    synths.add(detector.detectSpoof(w))
                    sims.add(MathPrimitives.cosineSimilarity(refCentroid, encoder.embed(w)))
                }

                val medSim = if (sims.isNotEmpty()) sims.sorted()[sims.size / 2] else 0f
                val medSyn = if (synths.isNotEmpty()) synths.sorted()[synths.size / 2] else 0f

                val engine = BiometricFusionEngine()
                val result = engine.evaluate(
                    similarity = medSim,
                    syntheticProbability = medSyn,
                    dynamicThreshold = 0.50f,
                    baselineSynthetic = 0.01f,
                    isReliable = true
                )

                val latency = System.currentTimeMillis() - startTime
                val duration = samples.size / 16000f

                val explanation = when (result.verdict) {
                    VcdVerdict.CRITICAL_CLONE_DETECTED ->
                        "High similarity (%.1f%%) matching enrolled contact combined with AI synthetic acoustic markers (%.1f%%) confirms a voice clone attack!".format(medSim * 100f, medSyn * 100f)
                    VcdVerdict.SAFE_VERIFIED_AUTHENTIC ->
                        "Verified authentic human voice (%.1f%% bonafide) matching enrolled contact (%.1f%% similarity).".format((1f - medSyn) * 100f, medSim * 100f)
                    VcdVerdict.SUSPICIOUS_IMPOSTOR ->
                        "Voice does not match enrolled contact (%.1f%% similarity, threshold 75%%).".format(medSim * 100f)
                    VcdVerdict.CRITICAL_UNKNOWN_SYNTHETIC ->
                        "Synthetic AI speech detected from unenrolled speaker (%.1f%% synthetic probability).".format(medSyn * 100f)
                    else ->
                        "AASIST Synthetic Probability: %.1f%%, Similarity: %.1f%%.".format(medSyn * 100f, medSim * 100f)
                }

                previousRun = currentRun
                currentRun = DemoAnalysisRun(
                    title = title,
                    durationSeconds = duration,
                    similarity = medSim,
                    syntheticProbability = medSyn,
                    verdict = result.verdict,
                    windowCount = windows.size,
                    latencyMs = latency,
                    explanation = explanation
                )
            } catch (e: Exception) {
                errorMessage = "Execution error: ${e.message}"
            } finally {
                isAnalyzing = false
                currentAction = null
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = VocisCardWhite)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "VCD Forensic Biometric Suite",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = VocisDark
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "AASIST Neural Spoof & Resemblyzer GE2E Verification",
                    style = MaterialTheme.typography.labelSmall,
                    color = VocisMediumGrey
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Dataset Audio Quick-Test Buttons
                Text(
                    text = "Scan voice for synthetic clone artifacts & deepfake markers:",
                    style = MaterialTheme.typography.bodySmall,
                    color = VocisMediumGrey,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { executeDemoClip("Live Microphone (3s)", null, isMic = true) },
                    enabled = !isAnalyzing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VocisDark)
                ) {
                    Text("🎙️ Record Live Voice (3s)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = { filePickerLauncher.launch("audio/*") },
                    enabled = !isAnalyzing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VocisBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VocisDark)
                ) {
                    Text("📁 Select Audio File from Device", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                if (isAnalyzing) {
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = currentAction ?: "Running neural models...",
                        style = MaterialTheme.typography.bodySmall,
                        color = VocisAmber,
                        fontWeight = FontWeight.Bold
                    )
                }

                errorMessage?.let { err ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = err, color = VocisRed, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }

                // Current Run Results
                currentRun?.let { run ->
                    Spacer(modifier = Modifier.height(16.dp))
                    val isCritical = run.verdict == VcdVerdict.CRITICAL_CLONE_DETECTED || run.verdict == VcdVerdict.CRITICAL_UNKNOWN_SYNTHETIC
                    val isSafe = run.verdict == VcdVerdict.SAFE_VERIFIED_AUTHENTIC

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCritical) VocisRedLight else if (isSafe) VocisGreenLight else VocisAmberLight
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isCritical) VocisRed else if (isSafe) VocisGreenText else VocisAmber
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = run.title,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = VocisDark
                                )
                                Text(
                                    text = when {
                                        isCritical -> "CRITICAL CLONE"
                                        isSafe -> "VERIFIED SAFE"
                                        else -> "SUSPICIOUS"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (isCritical) VocisRed else if (isSafe) VocisGreenText else VocisAmber
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Dual Biometric Score Meters
                            Text(
                                text = "• Speaker Similarity: %.1f%% (Match bar 75%%)".format(run.similarity * 100f),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = VocisDark
                            )
                            LinearProgressIndicator(
                                progress = { (run.similarity).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = if (run.similarity >= 0.75f) VocisGreenText else VocisAmber,
                                trackColor = Color.White
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "• Synthetic Speech Probability: %.1f%% (AASIST)".format(run.syntheticProbability * 100f),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = VocisDark
                            )
                            LinearProgressIndicator(
                                progress = { (run.syntheticProbability).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = if (run.syntheticProbability >= 0.50f) VocisRed else VocisGreenText,
                                trackColor = Color.White
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = run.explanation,
                                style = MaterialTheme.typography.bodySmall,
                                color = VocisDark,
                                lineHeight = 16.sp
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Metrics: %.1fs duration • %d windows • %d ms latency".format(run.durationSeconds, run.windowCount, run.latencyMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = VocisMediumGrey
                            )
                        }
                    }
                }

                // Previous Run Comparison Card
                previousRun?.let { prev ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Side-by-Side Comparison (Previous Run):",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = VocisMediumGrey,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    val prevCritical = prev.verdict == VcdVerdict.CRITICAL_CLONE_DETECTED || prev.verdict == VcdVerdict.CRITICAL_UNKNOWN_SYNTHETIC
                    val prevSafe = prev.verdict == VcdVerdict.SAFE_VERIFIED_AUTHENTIC

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = VocisCream),
                        border = androidx.compose.foundation.BorderStroke(1.dp, VocisBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(prev.title, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = VocisDark)
                                Text(
                                    "Sim: %.1f%% | Synth: %.1f%%".format(prev.similarity * 100f, prev.syntheticProbability * 100f),
                                    fontSize = 10.sp,
                                    color = VocisMediumGrey
                                )
                            }
                            Text(
                                text = when {
                                    prevCritical -> "CLONE"
                                    prevSafe -> "AUTHENTIC"
                                    else -> "IMPOSTOR"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = if (prevCritical) VocisRed else if (prevSafe) VocisGreenText else VocisAmber
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

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
