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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.vocis.ui.theme.VocisRed
import com.vocis.ui.theme.VocisRedLight
import kotlinx.coroutines.launch

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
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val vcdDb = remember { com.vocis.core.data.database.VcdDatabase.getInstance(context) }
    val dbProfiles by vcdDb.contactVoiceprintDao().getAllFlow().collectAsState(initial = emptyList<ContactVoiceprintEntity>())

    var showEnrollDialog by remember { mutableStateOf(false) }
    var profileToDelete by remember { mutableStateOf<ContactVoiceprintEntity?>(null) }

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
                    text = "${dbProfiles.size} Enrolled",
                    style = MaterialTheme.typography.labelSmall,
                    color = VocisGreenText,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (dbProfiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, VocisBorder, RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(containerColor = VocisCardWhite),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "🎙️", fontSize = 44.sp)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No Enrolled Voices Yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = VocisDark,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Enroll yourself or family members so VOCIS can authenticate their authentic voice and detect AI voice clones in real time.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VocisMediumGrey,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(dbProfiles, key = { it.contactId }) { voiceprint ->
                    VoiceProfileCard(
                        voiceprint = voiceprint,
                        onDelete = { profileToDelete = voiceprint }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(12.dp))
                }
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

    // Production Biometric Voice Enrollment Modal (3 Utterances, Resemblyzer ONNX, AES-256 Keystore Vault)
    if (showEnrollDialog) {
        VoiceEnrollmentDialog(
            dao = vcdDb.contactVoiceprintDao(),
            onDismiss = { showEnrollDialog = false }
        )
    }

    // Delete Voiceprint Confirmation Dialog
    if (profileToDelete != null) {
        val target = profileToDelete!!
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { profileToDelete = null },
            title = {
                Text(
                    text = "Delete Voiceprint",
                    fontWeight = FontWeight.Bold,
                    color = VocisDark
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove the enrolled biometric voiceprint for '${target.name}' (${target.phoneNumber})? This biometric data will be permanently wiped from the Keystore vault.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VocisMediumGrey
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                vcdDb.contactVoiceprintDao().delete(target)
                            } catch (_: Exception) {}
                            profileToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VocisRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { profileToDelete = null }
                ) {
                    Text("Cancel", color = VocisDark)
                }
            }
        )
    }
}

@Composable
fun VoiceProfileCard(
    voiceprint: ContactVoiceprintEntity,
    onDelete: () -> Unit
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
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(VocisGreenLight),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = voiceprint.name.firstOrNull()?.uppercase() ?: "V",
                    color = VocisGreenText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = voiceprint.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = VocisDark
                )
                Text(
                    text = voiceprint.phoneNumber,
                    style = MaterialTheme.typography.bodySmall,
                    color = VocisMediumGrey
                )
                Text(
                    text = "Hardware Keystore • AES-256 Vault",
                    style = MaterialTheme.typography.labelSmall,
                    color = VocisGreenText,
                    fontWeight = FontWeight.Medium
                )
            }

            // Delete action button
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(VocisRedLight)
                    .border(1.dp, VocisRed.copy(alpha = 0.3f), CircleShape)
                    .clickable { onDelete() }
                    .padding(8.dp)
            ) {
                Text(
                    text = "🗑️",
                    fontSize = 14.sp
                )
            }
        }
    }
}
