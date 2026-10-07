package com.vocis.sensor.normalizer

import com.vocis.core.domain.model.EventType
import com.vocis.core.domain.model.SecurityEvent
import java.util.UUID

object EventNormalizer {

    fun normalizeIncomingCall(
        handle: String?,
        presentation: Int = 1,
        timestampMs: Long = System.currentTimeMillis()
    ): SecurityEvent {
        val sanitizedNumber = sanitizeNumber(handle)
        val initialRisk = if (sanitizedNumber.isBlank() || sanitizedNumber == "UNKNOWN") "ELEVATED" else "LOW"
        val metadataJson = """{"handle":"$sanitizedNumber","presentation":$presentation,"direction":"INCOMING"}"""
        return SecurityEvent(
            id = UUID.randomUUID().toString(),
            type = EventType.INCOMING_CALL,
            source = "CallScreeningSensor",
            timestamp = timestampMs,
            identity = sanitizedNumber,
            metadata = metadataJson,
            initialRisk = initialRisk
        )
    }

    fun normalizeOutgoingCall(
        destinationNumber: String?,
        timestampMs: Long = System.currentTimeMillis()
    ): SecurityEvent {
        val sanitizedNumber = sanitizeNumber(destinationNumber)
        val metadataJson = """{"destination":"$sanitizedNumber","direction":"OUTGOING"}"""
        return SecurityEvent(
            id = UUID.randomUUID().toString(),
            type = EventType.OUTGOING_CALL,
            source = "OutgoingCallMonitor",
            timestamp = timestampMs,
            identity = sanitizedNumber,
            metadata = metadataJson,
            initialRisk = "LOW"
        )
    }

    fun normalizeSms(
        originatingAddress: String?,
        messageBody: String?,
        timestampMs: Long = System.currentTimeMillis()
    ): SecurityEvent {
        val sender = sanitizeNumber(originatingAddress)
        val body = messageBody ?: ""
        val isSuspect = body.contains("OTP", ignoreCase = true) ||
                body.contains("verify", ignoreCase = true) ||
                body.contains("blocked", ignoreCase = true) ||
                body.contains("urgent", ignoreCase = true)
        return SecurityEvent(
            id = UUID.randomUUID().toString(),
            type = EventType.SMS_RECEIVED,
            source = "SmsSensor",
            timestamp = timestampMs,
            identity = sender,
            metadata = body,
            initialRisk = if (isSuspect) "ELEVATED" else "LOW"
        )
    }

    fun normalizePackageAdded(
        packageName: String?,
        timestampMs: Long = System.currentTimeMillis()
    ): SecurityEvent {
        val pkg = packageName ?: "unknown.package"
        val isRemoteDesktop = RemoteDesktopPackages.isRemoteDesktop(pkg)
        val initialRisk = if (isRemoteDesktop) "CRITICAL" else "LOW"
        val metadataJson = """{"packageName":"$pkg","isRemoteDesktop":$isRemoteDesktop}"""
        return SecurityEvent(
            id = UUID.randomUUID().toString(),
            type = EventType.PACKAGE_ADDED,
            source = "PackageEventMonitor",
            timestamp = timestampMs,
            identity = pkg,
            metadata = metadataJson,
            initialRisk = initialRisk
        )
    }

    private fun sanitizeNumber(rawNumber: String?): String {
        if (rawNumber.isNullOrBlank()) return "UNKNOWN"
        val trimmed = rawNumber.removePrefix("tel:").trim()
        if (trimmed.equals("unknown", ignoreCase = true) ||
            trimmed.equals("private", ignoreCase = true) ||
            trimmed.equals("restricted", ignoreCase = true) ||
            trimmed.equals("unavailable", ignoreCase = true) ||
            trimmed.startsWith("-") ||
            trimmed in listOf("-1", "-2", "-3")
        ) {
            return "UNKNOWN"
        }
        val digitsAndPlus = trimmed.filter { it.isDigit() || it == '+' }
        val digitsOnly = digitsAndPlus.filter { it.isDigit() }
        // Legitimate phone numbers have at least 7 digits (or 3-4 for emergency codes)
        if (digitsOnly.length < 3 || (digitsOnly.length in 4..6)) {
            return "UNKNOWN"
        }
        return digitsAndPlus
    }
}

object RemoteDesktopPackages {
    const val ANYDESK = "com.anydesk.anydeskandroid"
    const val TEAMVIEWER = "com.teamviewer.teamviewer.market.mobile"
    const val RUSTDESK = "com.carriez.flutter_hbb"

    private val REMOTE_DESKTOP_SET = setOf(ANYDESK, TEAMVIEWER, RUSTDESK)

    fun isRemoteDesktop(packageName: String): Boolean = REMOTE_DESKTOP_SET.contains(packageName)
}
