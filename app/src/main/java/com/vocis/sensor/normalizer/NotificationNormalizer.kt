package com.vocis.sensor.normalizer

import com.vocis.core.domain.model.EventType
import com.vocis.core.domain.model.SecurityEvent
import java.util.UUID

object NotificationNormalizer {

    private val FINANCIAL_KEYWORDS = listOf(
        "bank", "otp", "debit", "credit", "transferred", "upi", "account",
        "payment", "wallet", "kyc", "electricity", "bill", "trai", "police"
    )

    val FINANCIAL_PACKAGES = setOf(
        "com.google.android.apps.nbu.paisa.user", // Google Pay
        "net.one97.paytm",                        // Paytm
        "com.phonepe.app",                        // PhonePe
        "in.org.npci.upiapp",                     // BHIM
        "com.sbi.upi",                            // YONO SBI
        "com.icicibank.mobile",                   // iMobile Pay
        "com.hdfcbank.payzapp",                   // PayZapp
        "com.axis.mobile"                         // Axis Mobile
    )

    fun isFinancialPackage(packageName: String): Boolean = FINANCIAL_PACKAGES.contains(packageName)

    fun normalize(
        packageName: String,
        title: String?,
        text: String?,
        bigText: String? = null,
        subText: String? = null,
        timestampMs: Long = System.currentTimeMillis()
    ): SecurityEvent {
        val safeTitle = title ?: ""
        val safeText = text ?: ""
        val safeBigText = bigText ?: ""
        val fullContent = "$safeTitle $safeText $safeBigText".trim()

        val isRemoteDesktop = RemoteDesktopPackages.isRemoteDesktop(packageName)
        val isFinancialApp = FINANCIAL_PACKAGES.contains(packageName)
        val hasFinancialKeywords = FINANCIAL_KEYWORDS.any { fullContent.contains(it, ignoreCase = true) }

        val initialRisk = when {
            isRemoteDesktop -> "CRITICAL"
            isFinancialApp && (fullContent.contains("otp", ignoreCase = true) || fullContent.contains("debit", ignoreCase = true)) -> "HIGH"
            hasFinancialKeywords -> "ELEVATED"
            else -> "LOW"
        }

        return SecurityEvent(
            id = UUID.randomUUID().toString(),
            type = EventType.NOTIFICATION_POSTED,
            source = "NotificationSensor",
            timestamp = timestampMs,
            identity = packageName,
            metadata = fullContent,
            initialRisk = initialRisk
        )
    }
}
