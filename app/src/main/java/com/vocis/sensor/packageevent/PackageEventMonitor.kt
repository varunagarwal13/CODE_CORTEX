package com.vocis.sensor.packageevent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vocis.intelligence.hub.InteractionHub
import com.vocis.sensor.normalizer.EventNormalizer

class PackageEventMonitor : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != Intent.ACTION_PACKAGE_ADDED) return
        val packageName = intent.data?.schemeSpecificPart
        val event = EventNormalizer.normalizePackageAdded(packageName)
        InteractionHub.getInstance(context).processEventAsync(event)
    }
}
