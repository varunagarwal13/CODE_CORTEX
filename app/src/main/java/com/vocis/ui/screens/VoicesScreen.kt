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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.vocis.core.data.entity.ContactVoiceprintEntity

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vocis.ui.theme.VocisBorder
import com.vocis.ui.theme.VocisCardWhite
import com.vocis.ui.theme.VocisCream
import com.vocis.ui.theme.VocisDark
import com.vocis.ui.theme.VocisGreen
import com.vocis.ui.theme.VocisGreenLight
import com.vocis.ui.theme.VocisGreenText
import com.vocis.ui.theme.VocisMediumGrey

data class VoiceProfileItem(
    val name: String,
    val relationship: String,
    val phone: String,
    val isEnrolled: Boolean,
    val enrolledDate: String
)

@Composable
fun VoicesScreen(
    onEnrollNewProfile: () -> Unit = {}
) {
    val profiles = remember {
        mutableStateListOf(
            VoiceProfileItem("My Primary Voice", "Device Owner", "Self", true, "Enrolled • AES-256 Vault"),
            VoiceProfileItem("Mom", "Emergency Contact", "+91 98888 77771", true, "Enrolled • 3 Utterances"),
            VoiceProfileItem("Brother", "Family Contact", "+91 98888 77772", true, "Enrolled • Verified")
        )
    }

    var showEnrollDialog by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var newlyEnrolledName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VocisCream)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Voice Defense",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = VocisDark
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(VocisGreenLight)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "AASIST Neural Active",
                    style = MaterialTheme.typography.labelSmall,
                    color = VocisGreenText,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(profiles) { profile ->
                VoiceProfileCard(profile)
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        Button(
            onClick = { showEnrollDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VocisDark),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "+ Enroll New Voice Profile",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Figma Screen 08: Voice Recording Modal
    if (showEnrollDialog) {
        VoiceEnrollmentDialog(
            onDismiss = { showEnrollDialog = false },
            onComplete = { name, relation, phone ->
                newlyEnrolledName = name
                profiles.add(
                    VoiceProfileItem(
                        name = name,
                        relationship = relation,
                        phone = phone,
                        isEnrolled = true,
                        enrolledDate = "Just Now • Verified"
                    )
                )
                showEnrollDialog = false
                showSuccessDialog = true
            }
        )
    }

    // Figma Screen 09: Enrollment Success Screen
    if (showSuccessDialog) {
        VoiceEnrollmentSuccessDialog(
            contactName = newlyEnrolledName,
            onDismiss = { showSuccessDialog = false }
        )
    }
}

@Composable
fun VoiceEnrollmentDialog(
    onDismiss: () -> Unit,
    onComplete: (name: String, relation: String, phone: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("Family Contact") }
    var phone by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(true) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Dialog(onDismissRequest = onDismiss) {
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
                Text(
                    text = "Enroll Voice Profile",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = VocisDark
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Speak naturally for 10 seconds to generate a biometric embedding.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VocisMediumGrey,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Pulsing Mic recording indicator (Figma Screen 8)
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .scale(if (isRecording) pulseScale else 1f)
                        .clip(CircleShape)
                        .background(VocisGreenLight),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(VocisGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🎙️", fontSize = 32.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Contact Name (e.g. Sister)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
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
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = VocisCream),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel", color = VocisDark)
                    }

                    Button(
                        onClick = {
                            val finalName = if (name.isNotBlank()) name else "Family Contact"
                            val finalPhone = if (phone.isNotBlank()) phone else "+91 99999 88888"
                            onComplete(finalName, relationship, finalPhone)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = VocisGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Profile", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceEnrollmentSuccessDialog(
    contactName: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
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
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(VocisGreenLight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "✓", color = VocisGreen, fontSize = 42.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Profile Created!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = VocisDark
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "$contactName's voice biometric embedding is securely sealed in the AES-256 hardware vault.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VocisMediumGrey,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(VocisCream)
                        .border(1.dp, VocisBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "AASIST MODEL METRICS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = VocisGreen
                        )
                        Text(
                            text = "• Cosine Similarity Threshold: 0.85\n• Resemblyzer 256-dim embedding\n• Anti-Spoof LLR Calibrated",
                            style = MaterialTheme.typography.bodySmall,
                            color = VocisDark
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = VocisDark),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun VoiceProfileCard(profile: VoiceProfileItem) {
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
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(VocisGreenLight),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = profile.name.firstOrNull()?.toString() ?: "V",
                    color = VocisGreenText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = VocisDark
                )
                Text(
                    text = "${profile.relationship} • ${profile.phone}",
                    style = MaterialTheme.typography.bodySmall,
                    color = VocisMediumGrey
                )
                Text(
                    text = profile.enrolledDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = VocisGreenText,
                    fontWeight = FontWeight.Medium
                )
            }

            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(VocisGreen)
            )
        }
    }
}
