package com.vocis.ui.screens

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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

enum class ActiveCallState {
    IDLE,
    CONNECTING,
    ANALYZING,
    SAFE,
    SUSPICIOUS,
    CRITICAL,
    UNAVAILABLE,
    ERROR
}

@Composable
fun ActiveCallScreen(
    callerName: String = "Incoming Caller",
    callerNumber: String = "+91 98765 43210",
    isCloneDetected: Boolean = false,
    syntheticProbability: Float = 0.05f,
    cosineSimilarity: Float = 0.88f,
    callState: ActiveCallState = if (isCloneDetected) ActiveCallState.CRITICAL else ActiveCallState.UNAVAILABLE,
    onEndCall: () -> Unit = {},
    onTriggerEmergency: () -> Unit = {},
    onBack: () -> Unit = onEndCall
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val isAlert = callState == ActiveCallState.CRITICAL || callState == ActiveCallState.SUSPICIOUS

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VocisCream)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Bar with Back Navigation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(VocisCardWhite)
                    .border(1.dp, VocisBorder, RoundedCornerShape(12.dp))
                    .clickable { onBack() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "← Back",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = VocisDark
                )
            }

            Text(
                text = "LIVE CALL SCREENING",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = VocisMediumGrey,
                letterSpacing = 2.sp
            )

            Box(modifier = Modifier.width(48.dp))
        }

        // Center Caller & Waveform Section
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Animated Avatar Pulse Circle
            Box(
                modifier = Modifier
                    .size((110 * pulseScale).dp)
                    .clip(CircleShape)
                    .background(if (isAlert) VocisRedLight else VocisGreenLight)
                    .border(2.dp, if (isAlert) VocisRed else VocisGreen, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(if (isAlert) VocisRed else VocisGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = callerName.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = callerName,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = VocisDark
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = callerNumber,
                style = MaterialTheme.typography.titleMedium,
                color = VocisMediumGrey
            )

            Spacer(modifier = Modifier.height(20.dp))

            // State-Specific Status Card
            when (callState) {
                ActiveCallState.UNAVAILABLE -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, VocisAmber, RoundedCornerShape(18.dp)),
                        colors = CardDefaults.cardColors(containerColor = VocisAmberLight),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "ℹ️ Live Voice Verification Unavailable",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = VocisDark
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                 text = "Biometric neural models standby. Telephony call screening and risk reputation active.",
                                 style = MaterialTheme.typography.bodySmall,
                                 color = VocisDark,
                                 textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                ActiveCallState.CRITICAL -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, VocisRed, RoundedCornerShape(18.dp)),
                        colors = CardDefaults.cardColors(containerColor = VocisRedLight),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "⚠️ VOICE CLONE DETECTED",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = VocisRed
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Synthetic Prob: ${(syntheticProbability * 100).toInt()}% • High threat clone signature detected",
                                style = MaterialTheme.typography.bodyMedium,
                                color = VocisRed
                            )
                        }
                    }
                }

                ActiveCallState.SAFE -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, VocisGreen, RoundedCornerShape(18.dp)),
                        colors = CardDefaults.cardColors(containerColor = VocisGreenLight),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "✓ CALLER SIGNED & SAFE",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = VocisGreenText
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Biometric Match: ${(cosineSimilarity * 100).toInt()}% • L2 centroid verified",
                                style = MaterialTheme.typography.bodyMedium,
                                color = VocisGreenText
                            )
                        }
                    }
                }

                ActiveCallState.ANALYZING, ActiveCallState.CONNECTING -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, VocisBorder, RoundedCornerShape(18.dp)),
                        colors = CardDefaults.cardColors(containerColor = VocisCardWhite),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🎙️ Analyzing Call Audio...",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = VocisDark
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Screening spectral components for synthetic vocoder artifacts",
                                style = MaterialTheme.typography.bodySmall,
                                color = VocisMediumGrey
                            )
                        }
                    }
                }

                else -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, VocisBorder, RoundedCornerShape(18.dp)),
                        colors = CardDefaults.cardColors(containerColor = VocisCardWhite),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "📞 Call Active",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = VocisDark
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Telephony connection established.",
                                style = MaterialTheme.typography.bodySmall,
                                color = VocisMediumGrey
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Simulated Audio Waveform Bar Graph
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(VocisCardWhite)
                    .border(1.dp, VocisBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val heights = listOf(12, 24, 18, 30, 14, 28, 22, 10, 26, 32, 16, 20, 14, 28, 12)
                for (h in heights) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height((h * pulseScale).dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (isAlert) VocisRed else VocisGreen)
                    )
                }
            }
        }

        // Bottom Call Control Buttons
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Button(
                    onClick = onTriggerEmergency,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VocisRedLight),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "🚨 SOS Alert",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = VocisRed
                    )
                }

                Button(
                    onClick = onEndCall,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VocisRed),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "End Call",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
