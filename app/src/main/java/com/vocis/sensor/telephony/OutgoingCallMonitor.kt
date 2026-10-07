package com.vocis.sensor.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vocis.intelligence.hub.InteractionHub
import com.vocis.sensor.normalizer.EventNormalizer

class OutgoingCallMonitor : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != Intent.ACTION_NEW_OUTGOING_CALL) return
        val destinationNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
        val event = EventNormalizer.normalizeOutgoingCall(destinationNumber)
        InteractionHub.getInstance(context).processEventAsync(event)
    }
}
