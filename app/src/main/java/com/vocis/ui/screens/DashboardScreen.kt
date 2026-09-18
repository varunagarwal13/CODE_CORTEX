package com.vocis.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vocis.core.data.entity.InteractionEntity
import com.vocis.core.domain.model.RiskLevel
import com.vocis.intelligence.hub.InteractionHub
import com.vocis.ui.components.VocisLogoBadge
import com.vocis.ui.theme.VocisAmber
import com.vocis.ui.theme.VocisAmberLight
import com.vocis.ui.theme.VocisBlue
import com.vocis.ui.theme.VocisBlueLight
import com.vocis.ui.theme.VocisBorder
import com.vocis.ui.theme.VocisCardWhite
import com.vocis.ui.theme.VocisCream
import com.vocis.ui.theme.VocisDark
import com.vocis.ui.theme.VocisGreen
import com.vocis.ui.theme.VocisGreenLight
import com.vocis.ui.theme.VocisGreenText
import com.vocis.ui.theme.VocisLightGrey
import com.vocis.ui.theme.VocisMediumGrey
import com.vocis.ui.theme.VocisRed
import com.vocis.ui.theme.VocisRedLight

/**
 * Figma Screen 04: Shield Dashboard (Home Screen).
 * Features official forest green Hero Card with geometric diamond emblem,
 * Quick stats, Quick action cards (Digital Arrest, Emergency SOS),
 * and Recent Activity stream with tap-to-inspect.
 */
@Composable
fun DashboardScreen(
    interactionHub: InteractionHub? = null,
    onNavigateToDigitalArrest: () -> Unit = {},
    onNavigateToEmergency: () -> Unit = {},
    onSelectInteraction: (InteractionEntity) -> Unit = {},
    onViewAllCalls: () -> Unit = {},
    onProfileClick: () -> Unit = {}
) {
    val interactions by (interactionHub?.interactions?.collectAsState() ?: remember {
        mutableStateOf(emptyList<InteractionEntity>())
    })
    val activeIncident by (interactionHub?.activeIncident?.collectAsState() ?: remember {
        mutableStateOf(null)
    })

    val totalScanned = if (interactions.isNotEmpty()) interactions.size else 4
    val totalThreats = interactions.count { it.riskLevel >= RiskLevel.HIGH || it.isBlocked }

    var isShieldActive by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VocisCream)
            .statusBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 100.dp)
        ) {
            // 1. Top Header Bar (Greeting + Notifications + Avatar)
            item {
                DashboardHeader(
                    unreadCount = if (totalThreats > 0) totalThreats else 0,
                    onProfileClick = onProfileClick
                )
            }

            // 2. Figma Hero Card: Protection Active Banner
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { isShieldActive = !isShieldActive },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isShieldActive) VocisGreen else Color(0xFF4A5568)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "VOCIS SHIELD",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.85f),
                                    letterSpacing = 1.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isShieldActive) "Protection Active" else "Shield Paused",
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp
                                )
                            }

                            // Official Geometric Diamond Emblem Badge (Figma)
                            VocisLogoBadge(
                                size = 46.dp,
                                withBackground = true,
                                backgroundColor = Color.White.copy(alpha = 0.22f),
                                emblemColor = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = if (isShieldActive)
                                "Real-time call screening, voice clone neural defense, and SMS telemetry running on-device."
                            else
                                "Tap to re-enable real-time acoustic neural defense and scam blocking.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.95f),
                            lineHeight = 20.sp,
                            fontSize = 13.5.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Active status pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.18f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isShieldActive) "● All Defense Systems Active" else "○ Tap to Activate",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // 3. Quick Stats Grid (Figma Screen 04)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "Calls Scanned",
                        count = totalScanned.toString(),
                        subtitle = "All safe",
                        modifier = Modifier.weight(1f),
                        badgeColor = VocisGreenLight,
                        badgeTextColor = VocisGreenText,
                        onClick = onViewAllCalls
                    )

                    StatCard(
                        title = "Threats Blocked",
                        count = totalThreats.toString(),
                        subtitle = if (totalThreats > 0) "$totalThreats detected" else "Zero threats",
                        modifier = Modifier.weight(1f),
                        badgeColor = if (totalThreats > 0) VocisRedLight else VocisGreenLight,
                        badgeTextColor = if (totalThreats > 0) VocisRed else VocisGreenText,
                        onClick = onViewAllCalls
                    )
                }
            }

            // 4. Quick Actions Grid (Figma Screen 04)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Digital Arrest Protocol Action Card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, VocisBorder, RoundedCornerShape(18.dp))
                            .clickable { onNavigateToDigitalArrest() },
                        colors = CardDefaults.cardColors(containerColor = VocisCardWhite),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(text = "🛡️", fontSize = 22.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Digital Arrest",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = VocisDark,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "10-Phase Drill",
                                style = MaterialTheme.typography.labelSmall,
                                color = VocisMediumGrey
                            )
                        }
                    }

                    // Emergency SOS Action Card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, VocisBorder, RoundedCornerShape(18.dp))
                            .clickable { onNavigateToEmergency() },
                        colors = CardDefaults.cardColors(containerColor = VocisCardWhite),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(text = "🚨", fontSize = 22.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Emergency SOS",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = VocisRed,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Family & Siren",
                                style = MaterialTheme.typography.labelSmall,
                                color = VocisMediumGrey
                            )
                        }
                    }
                }
            }

            // 5. Active Extortion Warning (if present)
            if (activeIncident != null) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, VocisAmber, RoundedCornerShape(18.dp))
                            .clickable { onNavigateToDigitalArrest() },
                        colors = CardDefaults.cardColors(containerColor = VocisAmberLight)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "🚨", fontSize = 26.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Active Incident: ${activeIncident?.incidentType?.name ?: "Digital Arrest"}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = VocisDark
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Tap to review forensic timeline & trigger lockdown",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VocisDark.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }

            // 6. Recent Activity Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Activity",
                        style = MaterialTheme.typography.titleLarge,
                        color = VocisDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )

                    Text(
                        text = "View all (${interactions.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1976D2),
                        modifier = Modifier.clickable { onViewAllCalls() }
                    )
                }
            }

            // 7. Activity Feed Items (Figma Screen 04)
            if (interactions.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, VocisBorder, RoundedCornerShape(18.dp)),
                        colors = CardDefaults.cardColors(containerColor = VocisCardWhite)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "🛡️", fontSize = 32.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "All communications safe",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = VocisDark
                            )
                            Text(
                                text = "Incoming calls and SMS will be screened and logged here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = VocisMediumGrey,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(interactions.take(5), key = { it.id }) { interaction ->
                    InteractionListItem(
                        interaction = interaction,
                        onClick = { onSelectInteraction(interaction) }
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardHeader(
    unreadCount: Int,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Good evening,",
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 14.sp,
                color = VocisMediumGrey
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Varun",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = VocisDark
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Notification bell
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(Color.White, CircleShape)
                    .border(1.dp, VocisBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Notifications,
                    contentDescription = "Notifications",
                    tint = VocisDark,
                    modifier = Modifier.size(20.dp)
                )
                if (unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = (-3).dp, y = 3.dp)
                            .background(VocisRed, CircleShape)
                    )
                }
            }

            // Profile Avatar
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF2E6F40), Color(0xFF1E4D2B))
                        )
                    )
                    .clickable { onProfileClick() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "V",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    count: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    badgeColor: Color,
    badgeTextColor: Color,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = modifier
            .border(1.dp, VocisBorder, RoundedCornerShape(18.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = VocisCardWhite),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = VocisMediumGrey,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = count,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = VocisDark
            )

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(badgeColor)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeTextColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun InteractionListItem(
    interaction: InteractionEntity,
    onClick: () -> Unit
) {
    val (badgeBg, badgeColor) = when (interaction.riskLevel) {
        RiskLevel.LOW -> VocisGreenLight to VocisGreenText
        RiskLevel.ELEVATED -> VocisBlueLight to VocisBlue
        RiskLevel.HIGH -> VocisAmberLight to VocisAmber
        RiskLevel.CRITICAL -> VocisRedLight to VocisRed
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, VocisBorder, RoundedCornerShape(18.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = VocisCardWhite),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (interaction.callerDisplayName.isNotBlank()) interaction.callerDisplayName else interaction.callerPhoneNumber.ifEmpty { interaction.title },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = VocisDark
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = interaction.summary.ifBlank { "Verified phone interaction" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 13.sp,
                    color = VocisMediumGrey,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = "${interaction.callerPhoneNumber.ifEmpty { interaction.appName }} • ${interaction.timestamp}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = VocisLightGrey
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(badgeBg)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = interaction.riskLevel.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = badgeColor
                )
            }
        }
    }
}
