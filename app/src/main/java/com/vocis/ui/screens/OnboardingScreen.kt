package com.vocis.ui.screens

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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vocis.ui.components.VocisLogoBadge
import com.vocis.ui.theme.VocisBorder
import com.vocis.ui.theme.VocisCardWhite
import com.vocis.ui.theme.VocisCream
import com.vocis.ui.theme.VocisDark
import com.vocis.ui.theme.VocisDarkGrey
import com.vocis.ui.theme.VocisGreen
import com.vocis.ui.theme.VocisGreenLight
import com.vocis.ui.theme.VocisGreenText
import com.vocis.ui.theme.VocisMediumGrey

/**
 * 3-Page Onboarding Flow:
 * Page 1: Welcome / Introduction
 * Page 2: Protection Features Explanation
 * Page 3: Permission / Enable Protection
 */
@Composable
fun OnboardingScreen(
    onCompleteOnboarding: () -> Unit,
    onRequestPermissions: () -> Unit = {}
) {
    var step by remember { mutableIntStateOf(1) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VocisCream)
            .systemBarsPadding()
    ) {
        when (step) {
            1 -> OnboardingWelcomeView(onContinue = { step = 2 })
            2 -> OnboardingFeaturesView(
                onBack = { step = 1 },
                onContinue = { step = 3 }
            )
            else -> OnboardingPermissionsView(
                onBack = { step = 2 },
                onContinue = onCompleteOnboarding,
                onRequestPermissions = onRequestPermissions
            )
        }
    }
}

// ── PAGE 1: Welcome & Introduction ──────────────────────────────────────────
@Composable
fun OnboardingWelcomeView(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VocisCream)
            .padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Hero illustration card with diamond shield emblem
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(VocisGreenLight)
                .border(1.dp, VocisBorder, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                VocisLogoBadge(
                    size = 90.dp,
                    withBackground = true
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "VOCIS SHIELD",
                    style = MaterialTheme.typography.labelLarge,
                    color = VocisGreen,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Title and description
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Text(
                text = "Smart Voice,\nSuper Shield",
                style = MaterialTheme.typography.displayLarge,
                textAlign = TextAlign.Center,
                color = VocisDark,
                lineHeight = 38.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "VOCIS guards your calls, messages, and voice against modern deepfakes, extortion scams, and OTP theft with on-device AI.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = VocisMediumGrey
            )
        }

        // Step indicator & Continue button
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                StepIndicatorDot(isActive = true)
                Spacer(modifier = Modifier.width(8.dp))
                StepIndicatorDot(isActive = false)
                Spacer(modifier = Modifier.width(8.dp))
                StepIndicatorDot(isActive = false)
            }

            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VocisDark),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Continue",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ── PAGE 2: Protection Features Explanation ─────────────────────────────────
@Composable
fun OnboardingFeaturesView(
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VocisCream)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
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

                Spacer(modifier = Modifier.width(14.dp))

                Text(
                    text = "Protection Features",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = VocisDark
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Four dedicated layers of real-time security protecting your communication.",
                style = MaterialTheme.typography.bodyMedium,
                color = VocisMediumGrey
            )

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FeatureExplanationCard(
                    icon = "⚖️",
                    title = "Digital Arrest Countermeasures",
                    description = "Detects fake CBI, Police, or ED judicial summons, blocking institutional coercion and generating sealed court dossiers."
                )

                FeatureExplanationCard(
                    icon = "🎙️",
                    title = "Voice Clone Defense",
                    description = "Biometric speaker verification analyzing spectral vocoder artifacts to expose AI-generated synthetic voice impersonation."
                )

                FeatureExplanationCard(
                    icon = "💬",
                    title = "SMS Scam Pattern Analyzer",
                    description = "On-device linguistic engine screening banking OTP theft, electricity disconnection threats, and malicious APK traps."
                )

                FeatureExplanationCard(
                    icon = "🚨",
                    title = "Emergency Family SOS",
                    description = "Keyguard-bypassing siren override and instant automated SMS dispatch to verified emergency contacts."
                )
            }
        }

        // Step indicator & Navigation buttons
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                StepIndicatorDot(isActive = false)
                Spacer(modifier = Modifier.width(8.dp))
                StepIndicatorDot(isActive = true)
                Spacer(modifier = Modifier.width(8.dp))
                StepIndicatorDot(isActive = false)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "Back",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = VocisDark
                    )
                }

                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .weight(2f)
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VocisDark),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "Continue",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ── PAGE 3: Permissions & Shield Activation ─────────────────────────────────
@Composable
fun OnboardingPermissionsView(
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    var callScreeningEnabled by remember { mutableStateOf(true) }
    var smsSecurityEnabled by remember { mutableStateOf(true) }
    var voiceProtectionEnabled by remember { mutableStateOf(true) }
    var overlayEnabled by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VocisCream)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
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

                Spacer(modifier = Modifier.width(14.dp))

                Text(
                    text = "Enable Protection",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = VocisDark
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Enable system-level sensor access to detect and isolate threats in real-time.",
                style = MaterialTheme.typography.bodyMedium,
                color = VocisMediumGrey
            )

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Permission Card 1: Call Screening
                PermissionItemCard(
                    icon = "📞",
                    title = "Call Screening Service",
                    description = "Filter suspicious callers & digital arrest extortion in real time (<2000ms)",
                    checked = callScreeningEnabled,
                    onCheckedChange = { callScreeningEnabled = it }
                )

                // Permission Card 2: SMS Sensor
                PermissionItemCard(
                    icon = "💬",
                    title = "SMS Protection Sensor",
                    description = "Block banking fraud, fake electricity bills, and OTP theft traps",
                    checked = smsSecurityEnabled,
                    onCheckedChange = { smsSecurityEnabled = it }
                )

                // Permission Card 3: Biometric Audio Protection
                PermissionItemCard(
                    icon = "🎙️",
                    title = "Voice Clone Defense",
                    description = "Microphone sensor access for synthetic voice validation and voiceprint enrollment",
                    checked = voiceProtectionEnabled,
                    onCheckedChange = { voiceProtectionEnabled = it }
                )

                // Permission Card 4: Floating System Overlay
                PermissionItemCard(
                    icon = "🪟",
                    title = "Live Call Threat HUD",
                    description = "Floating real-time safety indicator rendered over active phone dialer",
                    checked = overlayEnabled,
                    onCheckedChange = { overlayEnabled = it }
                )
            }
        }

        // Step indicator & Action buttons
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                StepIndicatorDot(isActive = false)
                Spacer(modifier = Modifier.width(8.dp))
                StepIndicatorDot(isActive = false)
                Spacer(modifier = Modifier.width(8.dp))
                StepIndicatorDot(isActive = true)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "Back",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = VocisDark
                    )
                }

                Button(
                    onClick = {
                        onRequestPermissions()
                        onContinue()
                    },
                    modifier = Modifier
                        .weight(2f)
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VocisGreen),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "Grant & Enable",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun StepIndicatorDot(isActive: Boolean) {
    Box(
        modifier = Modifier
            .size(if (isActive) 24.dp else 8.dp, 8.dp)
            .clip(CircleShape)
            .background(if (isActive) VocisGreen else VocisBorder)
    )
}

@Composable
fun FeatureExplanationCard(
    icon: String,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, VocisBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = VocisCardWhite),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(VocisCream),
                contentAlignment = Alignment.Center
            ) {
                Text(text = icon, fontSize = 22.sp)
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = VocisDark
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = VocisMediumGrey,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
fun PermissionItemCard(
    icon: String,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, VocisBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = VocisCardWhite),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(VocisCream),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = icon, fontSize = 22.sp)
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = VocisDark
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = VocisMediumGrey,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = VocisGreen,
                    uncheckedThumbColor = VocisDarkGrey,
                    uncheckedTrackColor = VocisBorder
                )
            )
        }
    }
}
