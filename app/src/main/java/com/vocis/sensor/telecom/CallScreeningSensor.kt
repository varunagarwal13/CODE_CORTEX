package com.vocis.sensor.telecom

import android.telecom.Call
import android.telecom.CallScreeningService
import com.vocis.core.domain.model.ProtectionAction
import com.vocis.intelligence.hub.InteractionHub
import com.vocis.sensor.normalizer.EventNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class CallScreeningSensor : CallScreeningService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val SCREENING_TIMEOUT_MS = 1800L // Hard budget <= 2000ms fail-open
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val handleUri = callDetails.handle?.toString()
        val presentation = if (callDetails.handlePresentation != 0) callDetails.handlePresentation else 1

        serviceScope.launch {
            var responded = false
            fun safeRespond(disallow: Boolean, reason: String) {
                if (responded) return
                responded = true
                val response = CallResponse.Builder()
                    .setDisallowCall(disallow)
                    .setRejectCall(disallow)
                    .setSilenceCall(disallow)
                    .setSkipCallLog(false)
                    .setSkipNotification(false)
                    .build()
                try {
                    respondToCall(callDetails, response)
                } catch (_: Exception) {}
            }

            try {
                val handleUri = callDetails.handle?.toString()
                val presentation = if (callDetails.handlePresentation != 0) callDetails.handlePresentation else 1
                val event = EventNormalizer.normalizeIncomingCall(handleUri, presentation)
                val hub = InteractionHub.getInstance(applicationContext)

                // Execute within strict fail-open timeout budget
                val interaction = withTimeoutOrNull(SCREENING_TIMEOUT_MS) {
                    hub.processEvent(event)
                }

                val shouldBlock = interaction?.isBlocked == true ||
                        interaction?.protectionDecision == ProtectionAction.BLOCK_CALL

                if (!shouldBlock) {
                    try {
                        val app = applicationContext as? com.vocis.VocisApplication
                        val threat = if (interaction?.riskLevel == com.vocis.core.domain.model.RiskLevel.CRITICAL) 85
                            else if (interaction?.riskLevel == com.vocis.core.domain.model.RiskLevel.HIGH) 60 else 0
                        app?.protectionOverlayManager?.showOverlay(
                            threatScore = threat,
                            status = "Screened: ${interaction?.riskLevel?.name ?: "MONITORING"}"
                        )
                    } catch (_: Exception) {}
                }

                safeRespond(shouldBlock, if (shouldBlock) "policy_blocked" else "call_allowed")
            } catch (e: Exception) {
                safeRespond(false, "screening_exception_fail_open")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
