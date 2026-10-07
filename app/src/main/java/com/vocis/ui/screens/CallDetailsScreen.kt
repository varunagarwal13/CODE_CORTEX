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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vocis.core.data.entity.InteractionEntity
import com.vocis.core.domain.model.IncidentType
import com.vocis.core.domain.model.ProtectionAction
import com.vocis.core.domain.model.RiskLevel
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

@Composable
fun CallDetailsScreen(
    interaction: InteractionEntity,
    onBack: () -> Unit = {},
    onNavigateToDigitalArrest: () -> Unit = {},
    onNavigateToEmergency: () -> Unit = {},
    onNavigateToActiveCall: () -> Unit = {},
    onViewReport: () -> Unit = {}
) {
    val (pillBg, pillTextColor) = when (interaction.riskLevel) {
        RiskLevel.LOW -> VocisGreenLight to VocisGreenText
        RiskLevel.ELEVATED -> VocisBlueLight to VocisBlue
        RiskLevel.HIGH -> VocisAmberLight to VocisAmber
        RiskLevel.CRITICAL -> VocisRedLight to VocisRed
    }

    val displayName = if (interaction.callerDisplayName.isNotBlank()) {
        interaction.callerDisplayName
    } else if (interaction.callerPhoneNumber.isNotBlank()) {
        interaction.callerPhoneNumber
    } else {
        "Unknown Caller"
    }

    val isDigitalArrestApplicable = interaction.riskLevel >= RiskLevel.HIGH ||
            interaction.incidentType == IncidentType.DIGITAL_ARREST ||
            interaction.groqScamCategory.contains("ARREST", ignoreCase = true) ||
            interaction.summary.contains("Arrest", ignoreCase = true)

    androidx.activity.compose.BackHandler {
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VocisCream)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Top Navigation Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "← Back",
                color = VocisGreen,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.clickable { onBack() }
            )

            Text(
                text = "Call Dossier",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = VocisDark
            )

            Box(modifier = Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            // Caller Identity Header Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, VocisBorder, RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(containerColor = VocisCardWhite),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(if (interaction.isBlocked) VocisRedLight else VocisGreenLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (interaction.isBlocked) "🚫" else displayName.take(1).uppercase(),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (interaction.isBlocked) VocisRed else VocisGreenText
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = VocisDark
                                )

                                if (interaction.callerPhoneNumber.isNotBlank() && interaction.callerDisplayName.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = interaction.callerPhoneNumber,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = VocisMediumGrey
                                    )
                                }

                                if (interaction.callerIdentitySource.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Source: ${interaction.callerIdentitySource.uppercase()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VocisMediumGrey
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(pillBg)
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = interaction.riskLevel.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = pillTextColor
                                )
                            }
                        }
                    }
                }
            }

            // Threat Assessment Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, VocisBorder, RoundedCornerShape(18.dp)),
                    colors = CardDefaults.cardColors(containerColor = VocisCardWhite),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Threat Assessment",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = VocisDark
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Protection Decision:", style = MaterialTheme.typography.bodyMedium, color = VocisMediumGrey)
                            Text(
                                text = interaction.protectionDecision?.name ?: "MONITOR_ONLY",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (interaction.isBlocked) VocisRed else VocisDark
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Call Blocked at Hardware Level:", style = MaterialTheme.typography.bodyMedium, color = VocisMediumGrey)
                            Text(
                                text = if (interaction.isBlocked) "YES (Disallowed)" else "NO (Allowed)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (interaction.isBlocked) VocisRed else VocisGreenText
                            )
                        }

                        if (interaction.groqScamScore > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Heuristic Scam Score:", style = MaterialTheme.typography.bodyMedium, color = VocisMediumGrey)
                                Text(
                                    text = "${interaction.groqScamScore} / 100",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (interaction.groqScamScore >= 50) VocisRed else VocisGreenText
                                )
                            }
                        }
                    }
                }
            }

            // AI & Linguistic Analysis Card
            val hasAnalysis = interaction.groqIsScam ||
                    interaction.groqScamCategory.isNotBlank() ||
                    interaction.groqAnalysisRationale.isNotBlank() ||
                    interaction.summary.isNotBlank()

            if (hasAnalysis) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, VocisBorder, RoundedCornerShape(18.dp)),
                        colors = CardDefaults.cardColors(containerColor = VocisCardWhite),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "Linguistic & Threat Analysis",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = VocisDark
                            )

                            if (interaction.groqScamCategory.isNotBlank() || interaction.incidentType != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                val cat = interaction.groqScamCategory.ifBlank { interaction.incidentType?.name ?: "" }
                                Text(
                                    text = "Category: $cat",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = VocisDark
                                )
                            }

                            if (interaction.groqUrgencyTactics.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tactics: ${interaction.groqUrgencyTactics}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VocisRed
                                )
                            }

                            val summaryText = interaction.groqAnalysisRationale.ifBlank { interaction.summary }
                            if (summaryText.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = summaryText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = VocisMediumGrey
                                )
                            }
                        }
                    }
                }
            }

            // Reputation Intelligence Card
            val hasReputation = interaction.repCategory.isNotBlank() ||
                    interaction.repSpamReports > 0 ||
                    interaction.repFraudReports > 0

            if (hasReputation) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, VocisBorder, RoundedCornerShape(18.dp)),
                        colors = CardDefaults.cardColors(containerColor = VocisCardWhite),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "Reputation Intelligence",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = VocisDark
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            if (interaction.repCategory.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Reputation Level:", style = MaterialTheme.typography.bodyMedium, color = VocisMediumGrey)
                                    Text(interaction.repLevel.ifBlank { "NEUTRAL" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = VocisDark)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Community Spam Reports:", style = MaterialTheme.typography.bodyMedium, color = VocisMediumGrey)
                                Text("${interaction.repSpamReports}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = VocisDark)
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Community Fraud Reports:", style = MaterialTheme.typography.bodyMedium, color = VocisMediumGrey)
                                Text("${interaction.repFraudReports}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = VocisRed)
                            }
                        }
                    }
                }
            }

            // Interaction Metadata Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, VocisBorder, RoundedCornerShape(18.dp)),
                    colors = CardDefaults.cardColors(containerColor = VocisCardWhite),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Interaction Metadata",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = VocisDark
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Recorded: ${interaction.timestamp}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VocisMediumGrey
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Interaction ID: ${interaction.id}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = VocisLightGrey
                        )
                    }
                }
            }

            // Live Screening Inspection Card
            item {
                OutlinedButton(
                    onClick = onNavigateToActiveCall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "🎙️ Inspect Live Screening HUD",
                        fontWeight = FontWeight.SemiBold,
                        color = VocisDark
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Action Buttons Bottom Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            // View Full Forensic Report button
            OutlinedButton(
                onClick = onViewReport,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, VocisDark)
            ) {
                Text(
                    text = "📊 View Full Forensic Report",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = VocisDark
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isDigitalArrestApplicable) {
                    Button(
                        onClick = onNavigateToDigitalArrest,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VocisDark),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = "⚖️ Digital Arrest",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                Button(
                    onClick = onNavigateToEmergency,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VocisRed),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "🚨 Emergency SOS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}
