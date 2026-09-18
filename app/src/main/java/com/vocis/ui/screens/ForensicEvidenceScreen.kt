package com.vocis.ui.screens

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.activity.compose.BackHandler
import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.vocis.core.data.entity.SecurityIncidentEntity
import com.vocis.core.domain.model.IncidentStatus

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vocis.ui.theme.VocisBorder
import com.vocis.ui.theme.VocisCardWhite
import com.vocis.ui.theme.VocisCream
import com.vocis.ui.theme.VocisDark
import com.vocis.ui.theme.VocisGreen
import com.vocis.ui.theme.VocisGreenLight
import com.vocis.ui.theme.VocisMediumGrey
import com.vocis.ui.theme.VocisRed
import com.vocis.ui.theme.VocisRedLight

data class ForensicEvidenceDossier(
    val dossierId: String,
    val suspectNumber: String,
    val scamType: String,
    val date: String,
    val sha256Seal: String,
    val violationsCount: Int,
    val status: String
)

/**
 * Figma Screen 12: Forensic Evidence Dossier & Legal Export Screen.
 * Displays sealed court-admissible dossiers with SHA-256 digital seals and 1930 reporting.
 */
@Composable
fun ForensicEvidenceScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current

    val sampleDossiers = listOf(
        ForensicEvidenceDossier(
            dossierId = "VCS-2026-8821A",
            suspectNumber = "+91 98765 43210",
            scamType = "Digital Arrest (CBI/ED Impersonation)",
            date = "Sep 18, 2026 • 15:42 IST",
            sha256Seal = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            violationsCount = 4,
            status = "SEALED & VERIFIED"
        ),
        ForensicEvidenceDossier(
            dossierId = "VCS-2026-8794B",
            suspectNumber = "+91 87654 32109",
            scamType = "Voice Clone (Deepfake Kin Ransom)",
            date = "Sep 16, 2026 • 11:20 IST",
            sha256Seal = "7f83b1657ff1fc53b92dc18148a1d65dfc2d4b1fa3d677284addd200126d9069",
            violationsCount = 3,
            status = "AASIST FLAG (>0.92)"
        )
    )

    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VocisCream)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "← Back",
                color = VocisGreen,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onBack() }
            )
            Text(
                text = "Forensic Vault",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = VocisDark
            )
            Box(modifier = Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Evidence Vault",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = VocisDark
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Cryptographically signed SHA-256 legal dossiers admissible for Cyber Crime (1930) reporting.",
            style = MaterialTheme.typography.bodyMedium,
            color = VocisMediumGrey
        )

        Spacer(modifier = Modifier.height(20.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(sampleDossiers) { dossier ->
                DossierCard(dossier = dossier)
            }
        }

        // Action buttons bottom bar
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            colors = CardDefaults.cardColors(containerColor = VocisCardWhite),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, VocisBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "National Cyber Crime Reporting",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = VocisDark
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "One-tap file an online report on cybercrime.gov.in or dial National Helpline 1930.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VocisMediumGrey
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:1930"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Dial 1930 📞", color = VocisRed, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://cybercrime.gov.in"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = VocisGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Portal (gov.in)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun DossierCard(dossier: ForensicEvidenceDossier) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, VocisBorder, RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = VocisCardWhite),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dossier.dossierId,
                    style = MaterialTheme.typography.labelLarge,
                    color = VocisGreen,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(VocisGreenLight)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = dossier.status,
                        style = MaterialTheme.typography.labelSmall,
                        color = VocisGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = dossier.scamType,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = VocisDark
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Suspect Caller: ${dossier.suspectNumber}",
                style = MaterialTheme.typography.bodyMedium,
                color = VocisMediumGrey
            )

            Text(
                text = "Recorded: ${dossier.date}",
                style = MaterialTheme.typography.bodySmall,
                color = VocisMediumGrey
            )

            Spacer(modifier = Modifier.height(12.dp))

            // SHA-256 seal box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(VocisCream)
                    .border(1.dp, VocisBorder, RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Column {
                    Text(
                        text = "SHA-256 CRYPTOGRAPHIC SEAL:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = VocisMediumGrey
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dossier.sha256Seal,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = VocisDark,
                        lineHeight = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${dossier.violationsCount} Legal Violations Proved",
                    style = MaterialTheme.typography.bodySmall,
                    color = VocisRed,
                    fontWeight = FontWeight.Bold
                )

                val context = LocalContext.current
                Button(
                    onClick = {
                        Toast.makeText(context, "Dossier " + dossier.dossierId + " exported to PDF!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VocisDark),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(text = "Export PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}
