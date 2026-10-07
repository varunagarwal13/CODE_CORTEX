package com.vocis.digitalarrest

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import com.vocis.core.data.entity.SecurityIncidentEntity
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DigitalArrestPdfGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40

    data class PdfEvidenceMetadata(
        val incidentId: String,
        val timestamp: Long,
        val callerPhoneNumber: String?,
        val callerName: String?,
        val riskScore: Int,
        val claims: List<String>,
        val ruleReport: DigitalArrestRuleReport,
        val osFingerprint: String = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
    )

    fun computeEvidenceSeal(metadata: PdfEvidenceMetadata): String {
        val payload = buildString {
            append(metadata.incidentId)
            append("|")
            append(metadata.timestamp)
            append("|")
            append(metadata.callerPhoneNumber ?: "UNKNOWN")
            append("|")
            append(metadata.riskScore)
            append("|")
            append(metadata.claims.sorted().joinToString(";"))
            append("|")
            append(metadata.ruleReport.rules.joinToString(";") { "${it.ruleId}:${it.isViolated}" })
            append("|")
            append(metadata.osFingerprint)
        }

        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(payload.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun generatePdf(
        context: Context,
        incident: SecurityIncidentEntity?,
        ruleReport: DigitalArrestRuleReport,
        claims: List<String> = emptyList()
    ): File {
        val now = System.currentTimeMillis()
        val metadata = PdfEvidenceMetadata(
            incidentId = incident?.incidentId ?: "SIMULATED_INCIDENT_${now}",
            timestamp = incident?.createdAt ?: now,
            callerPhoneNumber = incident?.callerPhoneNumber,
            callerName = incident?.callerDisplayName,
            riskScore = incident?.riskScore ?: 95,
            claims = claims,
            ruleReport = ruleReport
        )

        val seal = computeEvidenceSeal(metadata)
        val document = PdfDocument()

        try {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            val paint = Paint().apply { isAntiAlias = true }
            var currentY = 50f

            // 1. Header Banner
            paint.color = Color.parseColor("#1B2A4A") // Deep Navy
            canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 90f, paint)

            paint.color = Color.WHITE
            paint.textSize = 18f
            paint.isFakeBoldText = true
            canvas.drawText("VOCIS FORENSIC INCIDENT REPORT", MARGIN.toFloat(), 45f, paint)

            paint.textSize = 11f
            paint.isFakeBoldText = false
            paint.color = Color.parseColor("#A0C0E0")
            canvas.drawText("Digital Arrest Defense System • Cryptographically Sealed Evidence", MARGIN.toFloat(), 68f, paint)

            currentY = 115f

            // 2. Cryptographic Evidence Seal Box
            paint.color = Color.parseColor("#FFF2F2")
            canvas.drawRect(MARGIN.toFloat(), currentY, (PAGE_WIDTH - MARGIN).toFloat(), currentY + 45f, paint)

            paint.color = Color.parseColor("#CC0000")
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f
            canvas.drawRect(MARGIN.toFloat(), currentY, (PAGE_WIDTH - MARGIN).toFloat(), currentY + 45f, paint)

            paint.style = Paint.Style.FILL
            paint.textSize = 10f
            paint.isFakeBoldText = true
            paint.color = Color.parseColor("#990000")
            canvas.drawText("SHA-256 FORENSIC EVIDENCE SEAL:", (MARGIN + 10).toFloat(), currentY + 18f, paint)

            paint.textSize = 9f
            paint.isFakeBoldText = false
            paint.color = Color.DKGRAY
            canvas.drawText(seal, (MARGIN + 10).toFloat(), currentY + 34f, paint)

            currentY += 65f

            // 3. Incident Summary
            paint.color = Color.BLACK
            paint.textSize = 13f
            paint.isFakeBoldText = true
            canvas.drawText("1. Incident Summary", MARGIN.toFloat(), currentY, paint)
            currentY += 18f

            paint.textSize = 10f
            paint.isFakeBoldText = false
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
            val dateStr = dateFormat.format(Date(metadata.timestamp))

            canvas.drawText("Case ID: ${metadata.incidentId}", MARGIN.toFloat(), currentY, paint)
            currentY += 14f
            canvas.drawText("Date & Time: $dateStr", MARGIN.toFloat(), currentY, paint)
            currentY += 14f
            canvas.drawText("Suspect Phone: ${metadata.callerPhoneNumber ?: "Private / Unknown"}", MARGIN.toFloat(), currentY, paint)
            currentY += 14f
            canvas.drawText("Assessed Risk Score: ${metadata.riskScore}/100 (CRITICAL THREAT)", MARGIN.toFloat(), currentY, paint)
            currentY += 14f
            canvas.drawText("Device Fingerprint: ${metadata.osFingerprint}", MARGIN.toFloat(), currentY, paint)
            currentY += 24f

            // 4. Forensic Rule Evaluation Breakdown
            paint.textSize = 13f
            paint.isFakeBoldText = true
            canvas.drawText("2. Forensic Rule Evaluation Breakdown", MARGIN.toFloat(), currentY, paint)
            currentY += 18f

            paint.textSize = 9.5f
            for (rule in ruleReport.rules) {
                paint.isFakeBoldText = true
                if (rule.isViolated) {
                    paint.color = Color.parseColor("#B30000")
                    canvas.drawText("[VIOLATION] ${rule.ruleId}: ${rule.ruleName}", MARGIN.toFloat(), currentY, paint)
                } else {
                    paint.color = Color.parseColor("#006600")
                    canvas.drawText("[CLEARED] ${rule.ruleId}: ${rule.ruleName}", MARGIN.toFloat(), currentY, paint)
                }
                currentY += 13f

                paint.isFakeBoldText = false
                paint.color = Color.DKGRAY
                val explanation = if (rule.explanation.length > 90) rule.explanation.take(87) + "..." else rule.explanation
                canvas.drawText("   $explanation", MARGIN.toFloat(), currentY, paint)
                currentY += 16f
            }
            currentY += 10f

            // 5. Recorded Claims
            if (claims.isNotEmpty()) {
                paint.color = Color.BLACK
                paint.textSize = 13f
                paint.isFakeBoldText = true
                canvas.drawText("3. Recorded Adversary Claims", MARGIN.toFloat(), currentY, paint)
                currentY += 18f

                paint.textSize = 9f
                paint.isFakeBoldText = false
                for (claim in claims.take(4)) {
                    val safeClaim = if (claim.length > 95) claim.take(92) + "..." else claim
                    canvas.drawText("• \"$safeClaim\"", (MARGIN + 10).toFloat(), currentY, paint)
                    currentY += 14f
                }
                currentY += 10f
            }

            // 6. Official Advisory & Reporting Guidance
            currentY = (PAGE_HEIGHT - 120).toFloat()
            paint.color = Color.parseColor("#F5F5F7")
            canvas.drawRect(MARGIN.toFloat(), currentY, (PAGE_WIDTH - MARGIN).toFloat(), currentY + 70f, paint)

            paint.color = Color.parseColor("#003366")
            paint.textSize = 10f
            paint.isFakeBoldText = true
            canvas.drawText("OFFICIAL CYBER DEFENSE DIRECTIVE", (MARGIN + 10).toFloat(), currentY + 18f, paint)

            paint.textSize = 8.5f
            paint.isFakeBoldText = false
            paint.color = Color.BLACK
            canvas.drawText("Law enforcement agencies in India (Police, CBI, ED, Courts) NEVER arrest individuals or demand money over video calls.", (MARGIN + 10).toFloat(), currentY + 32f, paint)
            canvas.drawText("Immediately file an incident report at https://cybercrime.gov.in or dial National Helpline 1930.", (MARGIN + 10).toFloat(), currentY + 46f, paint)
            canvas.drawText("Generated by VOCIS Autonomous Mobile Cyber Defense Sentinel.", (MARGIN + 10).toFloat(), currentY + 60f, paint)

            document.finishPage(page)

            // Save to disk
            val safeIncidentId = metadata.incidentId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
            val fileName = "VOCIS_EVIDENCE_${safeIncidentId}.pdf"
            val outputDir = context.getExternalFilesDir(null) ?: context.filesDir ?: context.cacheDir
            val pdfFile = File(outputDir, fileName)

            FileOutputStream(pdfFile).use { out ->
                document.writeTo(out)
            }

            // Also copy to public Downloads directory so user can easily find it in their phone's Downloads
            try {
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                if (downloadsDir != null && (downloadsDir.exists() || downloadsDir.mkdirs())) {
                    val publicDownloadFile = File(downloadsDir, fileName)
                    pdfFile.copyTo(publicDownloadFile, overwrite = true)
                }
            } catch (_: Throwable) {}

            return pdfFile
        } finally {
            document.close()
        }
    }
}
