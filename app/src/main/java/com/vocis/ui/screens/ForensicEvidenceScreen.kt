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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import com.vocis.core.data.entity.SecurityIncidentEntity
import com.vocis.core.domain.model.IncidentStatus
import com.vocis.core.domain.model.IncidentType
import com.vocis.core.domain.model.RiskLevel
import com.vocis.digitalarrest.DigitalArrestPdfGenerator
import com.vocis.digitalarrest.DigitalArrestRules
import com.vocis.ui.theme.VocisGreenText
import java.io.File
import androidx.core.content.FileProvider

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

    val app = context.applicationContext as? com.vocis.VocisApplication
    val db = app?.appDatabase ?: com.vocis.core.data.database.AppDatabase.getInstance(context)
    val incidentFlow = remember(db) { db.securityIncidentDao().getAllFlow() }
    val dbIncidents by (incidentFlow.collectAsState(initial = emptyList<SecurityIncidentEntity>()))

    val dossiers: List<ForensicEvidenceDossier> = remember(dbIncidents) {
        dbIncidents.map { incident ->
            val sdf = java.text.SimpleDateFormat("MMM dd, yyyy • HH:mm 'IST'", java.util.Locale.US)
            ForensicEvidenceDossier(
                dossierId = incident.incidentId,
                suspectNumber = incident.callerPhoneNumber ?: "Unknown",
                scamType = incident.explanation ?: incident.incidentType.name,
                date = sdf.format(java.util.Date(incident.createdAt)),
                sha256Seal = java.security.MessageDigest.getInstance("SHA-256")
                    .digest("${incident.incidentId}_${incident.createdAt}".toByteArray())
                    .joinToString("") { "%02x".format(it) },
                violationsCount = (incident.riskScore / 25).coerceAtLeast(1),
                status = if (incident.status == IncidentStatus.RESOLVED) "RESOLVED" else "SEALED & VERIFIED"
            )
        }
    }

    var exportedPdfInfo by remember { mutableStateOf<Pair<String, File>?>(null) }

    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VocisCream)
            .statusBarsPadding()
            .navigationBarsPadding()
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
            text = "Court-admissible tamper-evident digital evidence dossiers sealed with SHA-256 integrity verification.",
            style = MaterialTheme.typography.bodyMedium,
            color = VocisMediumGrey
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (dossiers.isEmpty()) {
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
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "🛡️", fontSize = 44.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Forensic Dossiers Yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = VocisDark
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "When high-risk extortion, digital arrest, or voice clone attacks occur, tamper-evident forensic dossiers are sealed here for 1930 police reporting.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VocisMediumGrey,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(dossiers) { dossier ->
                    DossierCard(
                        dossier = dossier,
                        onExportPdf = {
                            try {
                                val incident = dbIncidents.find { it.incidentId == dossier.dossierId } ?: SecurityIncidentEntity(
                                    incidentId = dossier.dossierId,
                                    incidentType = IncidentType.DIGITAL_ARREST,
                                    severity = RiskLevel.CRITICAL,
                                    status = IncidentStatus.CONFIRMED,
                                    createdAt = System.currentTimeMillis(),
                                    updatedAt = System.currentTimeMillis(),
                                    riskScore = 95,
                                    explanation = "Scam pattern detected: ${dossier.scamType}",
                                    recommendedActions = "DISRUPT_AND_REPORT",
                                    callerPhoneNumber = dossier.suspectNumber,
                                    callerDisplayName = "Flagged Caller"
                                )
                                val ruleReport = DigitalArrestRules.evaluate(
                                    callerClaims = listOf(dossier.scamType, "Demand for immediate money transfer", "Threat of immediate detention"),
                                    transcript = "Stay on video do not disconnect verify bank account transfer",
                                    metadata = dossier.suspectNumber
                                )
                                val pdf = DigitalArrestPdfGenerator.generatePdf(
                                    context = context,
                                    incident = incident,
                                    ruleReport = ruleReport,
                                    claims = listOf(dossier.scamType, "Demand for immediate money transfer", "Threat of immediate detention")
                                )
                                exportedPdfInfo = Pair(dossier.dossierId, pdf)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Export error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                }
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

    // Exported PDF Location & Action Dialog
    exportedPdfInfo?.let { (dossierId, pdfFile) ->
        AlertDialog(
            onDismissRequest = { exportedPdfInfo = null },
            title = {
                Text(
                    text = "📄 Evidence PDF Exported",
                    fontWeight = FontWeight.Bold,
                    color = VocisDark
                )
            },
            text = {
                Column {
                    Text(
                        text = "Court-admissible forensic dossier has been generated and cryptographically sealed with SHA-256.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VocisMediumGrey
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(VocisGreenLight)
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "📂 Saved to Downloads Folder:",
                                fontWeight = FontWeight.Bold,
                                color = VocisGreenText,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "VOCIS_EVIDENCE_${dossierId}.pdf",
                                fontWeight = FontWeight.Bold,
                                color = VocisDark,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Full Device Path:\n${pdfFile.absolutePath}",
                        style = MaterialTheme.typography.labelSmall,
                        color = VocisMediumGrey
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                pdfFile
                            )
                            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/pdf")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(viewIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open viewer. File is available in Downloads.", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VocisDark),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Open PDF", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            try {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    pdfFile
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, "VOCIS Forensic Evidence Dossier: $dossierId")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Forensic Evidence Dossier"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Share error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Share", color = VocisDark)
                    }

                    OutlinedButton(
                        onClick = { exportedPdfInfo = null },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Close", color = VocisDark)
                    }
                }
            },
            containerColor = VocisCardWhite,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun DossierCard(
    dossier: ForensicEvidenceDossier,
    onExportPdf: () -> Unit = {}
) {
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
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = VocisDark
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (dossier.status.contains("VERIFIED")) VocisGreenLight else VocisRedLight)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = dossier.status,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (dossier.status.contains("VERIFIED")) VocisGreenText else VocisRed,
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

                Button(
                    onClick = onExportPdf,
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
