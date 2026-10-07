package com.vocis.sensor.telephony

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CallLog
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.vocis.VocisApplication
import com.vocis.sensor.normalizer.EventNormalizer
import com.vocis.vcd.service.LiveVerificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver monitoring Android telephony phone state transitions.
 * Directly referenced from VOCIS ProtectionController and TelephonyStateMonitor.
 */
class TelephonyStateMonitor : BroadcastReceiver() {

    companion object {
        var lastIncomingNumber: String? = null
        var lastCallerName: String? = null
        var lastCallStartTimeMs: Long = 0L

        fun queryLatestCallLog(context: Context, minDateMs: Long = 0L): Pair<String, String?>? {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
                return null
            }
            return try {
                val selection = if (minDateMs > 0) "${CallLog.Calls.DATE} >= ?" else null
                val selectionArgs = if (minDateMs > 0) arrayOf(minDateMs.toString()) else null
                val cursor = context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER_PRESENTATION),
                    selection,
                    selectionArgs,
                    "${CallLog.Calls.DATE} DESC LIMIT 1"
                )
                cursor?.use { c ->
                    if (c.moveToFirst()) {
                        val numIdx = c.getColumnIndex(CallLog.Calls.NUMBER)
                        val nameIdx = c.getColumnIndex(CallLog.Calls.CACHED_NAME)
                        val presIdx = c.getColumnIndex(CallLog.Calls.NUMBER_PRESENTATION)
                        val presentation = if (presIdx >= 0) c.getInt(presIdx) else CallLog.Calls.PRESENTATION_ALLOWED
                        val num = if (numIdx >= 0) c.getString(numIdx) else null
                        val name = if (nameIdx >= 0) c.getString(nameIdx) else null

                        val isRestricted = presentation == CallLog.Calls.PRESENTATION_RESTRICTED ||
                                presentation == CallLog.Calls.PRESENTATION_UNKNOWN ||
                                presentation == CallLog.Calls.PRESENTATION_PAYPHONE ||
                                num.isNullOrBlank() ||
                                num.startsWith("-") ||
                                num in listOf("-1", "-2", "-3", "UNKNOWN", "Unknown", "Private", "Restricted", "Unavailable") ||
                                num.filter { it.isDigit() }.length < 7

                        if (isRestricted || num.isNullOrBlank()) {
                            null
                        } else {
                            Pair(num.trim(), name)
                        }
                    } else null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        val app = try { VocisApplication.instance } catch (_: Exception) { null }

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                lastCallStartTimeMs = System.currentTimeMillis()
                val cleanNumber = incomingNumber?.takeIf {
                    !it.startsWith("-") && it.filter { c -> c.isDigit() }.length >= 7
                }
                lastIncomingNumber = cleanNumber
                lastCallerName = null

                if (!cleanNumber.isNullOrBlank()) {
                    val event = EventNormalizer.normalizeIncomingCall(cleanNumber)
                    app?.interactionHub?.processEventAsync(event)

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val identity = app?.callerIdentityResolver?.resolve(cleanNumber)
                            if (identity != null && !identity.displayName.isNullOrBlank()) {
                                lastCallerName = identity.displayName
                            }
                        } catch (_: Exception) {}
                    }
                }

                // Show in-call protection HUD overlay
                app?.protectionOverlayManager?.showOverlay(
                    threatScore = 0,
                    status = "Screening Call..."
                )
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (lastCallStartTimeMs == 0L) lastCallStartTimeMs = System.currentTimeMillis()
                val callerNum = incomingNumber?.takeIf {
                    !it.startsWith("-") && it.filter { c -> c.isDigit() }.length >= 7
                } ?: lastIncomingNumber

                // Call answered: start live voice verification service and update overlay
                LiveVerificationService.start(context, callerNum, lastCallerName)
                app?.protectionOverlayManager?.showOverlay(
                    threatScore = 0,
                    status = "Active Call • VCD Screening"
                )
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                val callStart = lastCallStartTimeMs

                // Reset session state immediately so the next call starts clean
                lastIncomingNumber = null
                lastCallerName = null
                lastCallStartTimeMs = 0L

                LiveVerificationService.stop(context)
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        app?.attackContextEngine?.onCallEnded()
                    } catch (_: Exception) {}

                    // Delay to allow Android Telecom time to write the call record to the system provider
                    kotlinx.coroutines.delay(1000)
                    app?.interactionHub?.loadRealCallLogs(context)
                }
                app?.protectionOverlayManager?.hideOverlay()
            }
        }
    }
}
