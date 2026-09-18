package com.vocis.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vocis.digitalarrest.DigitalArrestController
import com.vocis.digitalarrest.DigitalArrestPhase
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

/**
 * Figma Screen 11: 10-Phase Digital Arrest Defense & Recovery Protocol.
 * Guides citizen through institutional extortion recovery, debunking fake CBI/Police claims.
 */
@Composable
fun DigitalArrestScreen(
    controller: DigitalArrestController? = null,
    onBack: () -> Unit = {},
    onNavigateToEvidenceVault: () -> Unit = {}
) {
    var phaseIndex by remember { mutableIntStateOf(1) }
    val context = LocalContext.current
    val progress = (phaseIndex.toFloat() / 10f).coerceIn(0.1f, 1f)

    val currentPhase = when (phaseIndex) {
        1 -> "Phase 1: Institutional Authority Fraud" to "Extortionists impersonate CBI, Mumbai Police, or TRAI claiming your SIM was used in narcotics smuggling or money laundering."
        2 -> "Phase 2: Isolation & Coercion" to "Extortionist demands you stay on Skype/WhatsApp video call and forbids talking to family or legal counsel."
        3 -> "Phase 3: Fake Court & Police Dossier" to "Victim receives falsified Supreme Court warrants, CBI emblems, or arrest letters over WhatsApp."
        4 -> "Phase 4: Asset Verification Demand" to "Demands transferring liquid savings to 'RBI Safe Asset Verification Accounts' for clearance."
        5 -> "Phase 5: Secrecy & Threat of Imprisonment" to "Threatens immediate non-bailable arrest under NDPS/PMLA unless financial compliance is demonstrated."
        6 -> "Phase 6: Biometric & Visual Deception" to "Fraudsters wear fake police uniforms in simulated courtroom sets to induce psychological panic."
        7 -> "Phase 7: Immediate Legal Debunking" to "REALITY: Indian law enforcement NEVER conducts arrests, trials, or asset audits over video call."
        8 -> "Phase 8: Active Countermeasure Protocol" to "Disconnect call immediately. Do NOT transfer funds. Your bank will never verify funds via third-party accounts."
        9 -> "Phase 9: Sovereign Cyber Reporting" to "Mandatory immediate intimation to 1930 Cyber Crime Helpline within the golden hour to freeze fraudulent mule accounts."
        else -> "Phase 10: Cryptographic Evidence Sealing" to "Exporting SHA-256 sealed call telemetry and audio spectrogram to legal PDF dossier for filing formal FIR."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VocisCream)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Top Navigation Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "← Back to Console",
                color = VocisGreen,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.clickable { onBack() }
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(VocisRedLight)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "DEFENSE PROTOCOL",
                    style = MaterialTheme.typography.labelSmall,
                    color = VocisRed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Digital Arrest Defense",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = VocisDark,
            fontSize = 24.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Step $phaseIndex of 10: ${currentPhase.first}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = VocisMediumGrey
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Step Progress Bar
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = VocisGreen,
            trackColor = VocisBorder
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Main Phase Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = VocisCardWhite),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VocisBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(VocisRedLight, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🛡️", fontSize = 18.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = currentPhase.first,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = VocisDark
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = currentPhase.second,
                            style = MaterialTheme.typography.bodyMedium,
                            color = VocisDark,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            // Legal Rights Education Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = VocisGreenLight),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VocisGreen.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "CITIZEN LEGAL SAFEGUARD",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = VocisGreenText,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Under the Indian Code of Criminal Procedure (CrPC) & Bhartiya Nagarik Suraksha Sanhita (BNSS), no police agency, CBI, or magistrate can place a citizen under arrest via video call or demand funds.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VocisGreenText,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            // Emergency Helpline Trigger
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = VocisAmberLight),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VocisAmber.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Immediate Helpline Assistance",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = VocisDark
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Call 1930 to immediately freeze illicit mule bank transactions before funds leave the country.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VocisDark.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:1930"))
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VocisAmber),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("📞 Dial 1930 Cyber Helpline Now", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Navigation Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (phaseIndex > 1) {
                OutlinedButton(
                    onClick = { phaseIndex-- },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VocisDark)
                ) {
                    Text("Previous Step", color = VocisDark, fontWeight = FontWeight.SemiBold)
                }
            }

            if (phaseIndex < 10) {
                Button(
                    onClick = { phaseIndex++ },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VocisDark),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Next Step →", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(
                    onClick = { onNavigateToEvidenceVault() },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VocisGreen),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Seal & View Vault ✓", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
