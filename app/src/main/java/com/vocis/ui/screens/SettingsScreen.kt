package com.vocis.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vocis.core.data.preference.VocisPreferences
import com.vocis.core.domain.model.ProtectionMode
import com.vocis.ui.theme.VocisBorder
import com.vocis.ui.theme.VocisCardWhite
import com.vocis.ui.theme.VocisCream
import com.vocis.ui.theme.VocisDark
import com.vocis.ui.theme.VocisGreen
import com.vocis.ui.theme.VocisGreenLight
import com.vocis.ui.theme.VocisGreenText
import com.vocis.ui.theme.VocisMediumGrey

@Composable
fun SettingsScreen(
    onRevisitOnboarding: () -> Unit = {},
    onManageEmergencyContacts: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedMode by remember { mutableStateOf(ProtectionMode.BALANCED) }

    var callScreeningEnabled by remember { mutableStateOf(true) }
    var voiceDefenseEnabled by remember { mutableStateOf(true) }
    var smsShieldEnabled by remember { mutableStateOf(true) }
    var overlaysEnabled by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else true
        )
    }

    var userProfile by remember { mutableStateOf(VocisPreferences.getUserProfile(context)) }
    var showEditProfile by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VocisCream)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = VocisDark
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Security policies, permissions, and emergency contacts.",
            style = MaterialTheme.typography.bodyMedium,
            color = VocisMediumGrey
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Section 0: User Profile Card
            item {
                val initial = userProfile.name.firstOrNull()?.uppercase() ?: "U"
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, VocisBorder, RoundedCornerShape(20.dp))
                        .clickable { showEditProfile = true },
                    colors = CardDefaults.cardColors(containerColor = VocisCardWhite),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(Color(0xFF2E6F40), Color(0xFF1E4D2B))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initial,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (userProfile.name.isNotBlank()) userProfile.name else "VOCIS Sentinel User",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = VocisDark
                            )
                            if (userProfile.email.isNotBlank()) {
                                Text(
                                    text = userProfile.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VocisMediumGrey
                                )
                            }
                            if (userProfile.phone.isNotBlank()) {
                                Text(
                                    text = userProfile.phone,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VocisGreenText,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Button(
                            onClick = { showEditProfile = true },
                            colors = ButtonDefaults.buttonColors(containerColor = VocisGreenLight),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Edit", color = VocisGreenText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Section 1: Protection Policy Modes
            item {
                Text(
                    text = "Protection Policy",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = VocisDark
                )
            }

            item {
                ProtectionModeOption(
                    mode = ProtectionMode.BALANCED,
                    title = "Balanced Mode (Recommended)",
                    description = "Screen unknown callers, auto-block critical threats (Score > 75), alert on suspicious speech.",
                    selected = selectedMode == ProtectionMode.BALANCED,
                    onSelect = { selectedMode = ProtectionMode.BALANCED }
                )
            }

            item {
                ProtectionModeOption(
                    mode = ProtectionMode.AGGRESSIVE,
                    title = "Strict High-Security Mode",
                    description = "Immediately reject all high-risk numbers (>50), enforce voiceprint verification on contacts.",
                    selected = selectedMode == ProtectionMode.AGGRESSIVE,
                    onSelect = { selectedMode = ProtectionMode.AGGRESSIVE }
                )
            }

            item {
                ProtectionModeOption(
                    mode = ProtectionMode.PASSIVE,
                    title = "Monitor Only Mode",
                    description = "Log threat scores without blocking active calls or modifying audio stream.",
                    selected = selectedMode == ProtectionMode.PASSIVE,
                    onSelect = { selectedMode = ProtectionMode.PASSIVE }
                )
            }

            // Section 2: Sensor & Permission Toggles
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Sensor & Protection Toggles",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = VocisDark
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, VocisBorder, RoundedCornerShape(18.dp)),
                    colors = CardDefaults.cardColors(containerColor = VocisCardWhite),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        ToggleRow(
                            title = "Real-Time Call Screening",
                            subtitle = "Telephony sensor filtering spoofed numbers",
                            checked = callScreeningEnabled,
                            onCheckedChange = { callScreeningEnabled = it }
                        )

                        ToggleRow(
                            title = "Voice Clone Defense (AASIST)",
                            subtitle = "On-device neural synthetic speech detector",
                            checked = voiceDefenseEnabled,
                            onCheckedChange = { voiceDefenseEnabled = it }
                        )

                        ToggleRow(
                            title = "SMS Scam Heuristics",
                            subtitle = "Linguistic parser detecting extortion keywords",
                            checked = smsShieldEnabled,
                            onCheckedChange = { smsShieldEnabled = it }
                        )

                        ToggleRow(
                            title = "Floating Threat Overlay HUD",
                            subtitle = "Display live risk banner during calls",
                            checked = overlaysEnabled,
                            onCheckedChange = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                    if (!Settings.canDrawOverlays(context)) {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                    } else {
                                        overlaysEnabled = it
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // Section 3: Emergency Contacts
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Emergency Response",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = VocisDark
                )
            }

            item {
                val db = remember { com.vocis.core.data.database.AppDatabase.getInstance(context) }
                val contactList by db.familyContactDao().getAllFlow().collectAsState(initial = emptyList())

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, VocisBorder, RoundedCornerShape(18.dp)),
                    colors = CardDefaults.cardColors(containerColor = VocisCardWhite),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Family Emergency Network",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = VocisDark
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (contactList.isNotEmpty()) "${contactList.size} trusted family contact(s) configured in encrypted database."
                                           else "Automatic SMS and Siren dispatch triggers to designated contacts during emergency alerts.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VocisMediumGrey
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(VocisGreenLight)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${contactList.size} Configured",
                                    color = VocisGreenText,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = onManageEmergencyContacts,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VocisDark),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Manage Emergency Contacts →",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Section 4: Onboarding Tour Card
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, VocisBorder, RoundedCornerShape(18.dp))
                        .clickable { onRevisitOnboarding() },
                    colors = CardDefaults.cardColors(containerColor = VocisGreenLight),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📖", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Revisit Onboarding Tour",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = VocisGreenText
                            )
                            Text(
                                text = "Review app tour and permission guide.",
                                style = MaterialTheme.typography.bodySmall,
                                color = VocisGreenText.copy(alpha = 0.85f)
                            )
                        }
                        Text("→", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = VocisGreenText)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showEditProfile) {
        Dialog(
            onDismissRequest = { showEditProfile = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            SignInScreen(
                isEditMode = true,
                onSignInSuccess = {
                    userProfile = VocisPreferences.getUserProfile(context)
                    showEditProfile = false
                },
                onCancel = { showEditProfile = false }
            )
        }
    }
}

@Composable
fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = VocisDark
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = VocisMediumGrey
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = VocisGreen,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = VocisBorder
            )
        )
    }
}

@Composable
fun ProtectionModeOption(
    mode: ProtectionMode,
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (selected) VocisGreen else VocisBorder,
                RoundedCornerShape(18.dp)
            )
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) VocisGreenLight else VocisCardWhite
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(selectedColor = VocisGreen)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) VocisGreenText else VocisDark
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) VocisGreenText.copy(alpha = 0.85f) else VocisMediumGrey,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
