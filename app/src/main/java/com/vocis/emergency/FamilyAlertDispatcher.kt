package com.vocis.emergency

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import com.vocis.core.data.dao.FamilyContactDao
import com.vocis.core.data.entity.FamilyContactEntity
import com.vocis.core.domain.model.IncidentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

interface TextBeeSmsGateway {
    suspend fun sendSms(recipients: List<String>, message: String): Boolean
}

class DefaultTextBeeGateway(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val deviceId: String = System.getenv("TEXTBEE_DEVICE_ID") ?: "",
    private val apiKey: String = System.getenv("TEXTBEE_API_KEY") ?: ""
) : TextBeeSmsGateway {
    override suspend fun sendSms(recipients: List<String>, message: String): Boolean = withContext(Dispatchers.IO) {
        if (deviceId.isBlank() || apiKey.isBlank()) {
            return@withContext false
        }
        return@withContext try {
            val jsonRecipients = recipients.joinToString(separator = "\", \"", prefix = "[\"", postfix = "\"]")
            val escapedMessage = message.replace("\"", "\\\"").replace("\n", "\\n")
            val payload = """{"recipients": $jsonRecipients, "message": "$escapedMessage"}"""
            val body = payload.toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("https://api.textbee.dev/api/v1/gateway/devices/$deviceId/send-sms")
                .addHeader("x-api-key", apiKey)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }
}

interface NativeSmsSender {
    fun sendTextMessage(destination: String, text: String): Boolean
}

class DefaultNativeSmsSender(
    private val context: Context? = null
) : NativeSmsSender {
    override fun sendTextMessage(destination: String, text: String): Boolean {
        return try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && context != null) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            } ?: @Suppress("DEPRECATION") SmsManager.getDefault()
            val parts = smsManager.divideMessage(text)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(destination, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(destination, null, text, null, null)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}

class FamilyAlertDispatcher(
    private val contactDao: FamilyContactDao? = null,
    private val textBeeGateway: TextBeeSmsGateway = DefaultTextBeeGateway(),
    private val nativeSmsSender: NativeSmsSender = DefaultNativeSmsSender(),
    private val defaultEmergencyRecipients: List<String> = emptyList()
) {
    companion object {
        const val RISK_THRESHOLD = 50
        const val COOLDOWN_MS = 300_000L // 5 minutes global cooldown
    }

    private val mutex = Mutex()
    private val dispatchedInteractionIds = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    
    @Volatile
    private var lastDispatchedTimestamp: Long = 0L

    fun getLastDispatchedTime(): Long = lastDispatchedTimestamp

    fun clearSessionHistory() {
        dispatchedInteractionIds.clear()
        lastDispatchedTimestamp = 0L
    }

    suspend fun sendAlert(
        riskScore: Int,
        incidentType: IncidentType,
        callerNumber: String?,
        interactionId: String
    ): Boolean = mutex.withLock {
        val now = System.currentTimeMillis()

        // 1. Strict Gate: riskScore must be strictly > 50
        if (riskScore <= RISK_THRESHOLD) {
            return false
        }

        // 2. Idempotency Lock: 1 SMS per call/interaction session
        if (dispatchedInteractionIds.contains(interactionId)) {
            return false
        }

        // 3. 5-Minute Global Cooldown to prevent SMS flooding
        if (lastDispatchedTimestamp > 0L && (now - lastDispatchedTimestamp) < COOLDOWN_MS) {
            return false
        }

        // 4. Resolve Target Recipients
        val recipients = resolveRecipients()
        if (recipients.isEmpty()) {
            return false
        }

        val message = formatEmergencyMessage(riskScore, incidentType, callerNumber)

        // 5. Primary Path: TextBee Cloud Gateway
        val gatewaySuccess = try {
            textBeeGateway.sendSms(recipients, message)
        } catch (e: Exception) {
            false
        }

        val dispatchSuccessful = if (gatewaySuccess) {
            true
        } else {
            // 6. Fallback Path: Native Android SmsManager
            var fallbackSuccess = false
            for (number in recipients) {
                if (nativeSmsSender.sendTextMessage(number, message)) {
                    fallbackSuccess = true
                }
            }
            fallbackSuccess
        }

        if (dispatchSuccessful) {
            dispatchedInteractionIds.add(interactionId)
            lastDispatchedTimestamp = now
        }

        return dispatchSuccessful
    }

    private suspend fun resolveRecipients(): List<String> {
        val configured = try {
            contactDao?.getAll()
                ?.filter { it.isEmergencyAlertEnabled && it.phoneNumber.isNotBlank() }
                ?.map { it.phoneNumber }
        } catch (e: Exception) {
            null
        }

        return if (!configured.isNullOrEmpty()) {
            configured
        } else {
            defaultEmergencyRecipients
        }
    }

    fun formatEmergencyMessage(riskScore: Int, incidentType: IncidentType, callerNumber: String?): String {
        val callerInfo = if (!callerNumber.isNullOrBlank()) "from $callerNumber" else "from unknown caller"
        return "[VOCIS EMERGENCY ALERT] High-risk security threat detected (Risk Score: $riskScore/100, Threat: ${incidentType.name}) $callerInfo. Please verify their safety immediately. Do NOT transfer funds or authorize remote access."
    }
}
