package com.vocis.webrtc.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.vocis.webrtc.discovery.PeerDiscoveryService

/**
 * Foreground service (specialUse on API 34+) ensuring background reachability
 * and peer discovery for VOCIS encrypted peer-to-peer VoIP calls.
 * Acquires WiFi multicast lock and advertises mDNS reachability.
 */
class AvailabilityService : Service() {

    private var multicastLock: WifiManager.MulticastLock? = null
    private var peerDiscovery: PeerDiscoveryService? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        try {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("VocisMulticastLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
            peerDiscovery = PeerDiscoveryService(this).apply {
                val deviceName = Build.MODEL ?: "VOCIS-Device"
                registerService(deviceName)
                startDiscovery()
            }
            Log.i(TAG, "AvailabilityService initialised with Wi-Fi multicast lock and mDNS")
        } catch (e: Exception) {
            Log.w(TAG, "Multicast / mDNS discovery note: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildForegroundNotification()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            return START_STICKY
        } catch (t: Throwable) {
            Log.w(TAG, "Foreground promotion note: ${t.message}")
            return START_STICKY
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VOCIS Peer Reachability",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps this device discoverable for encrypted VOCIS peer calls"
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VOCIS Reachable")
            .setContentText("Listening for encrypted peer connections")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        try {
            peerDiscovery?.stopDiscovery()
            peerDiscovery?.unregisterService()
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cleanup note: ${e.message}")
        }
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "AvailabilityService"
        const val CHANNEL_ID = "vocis_availability_channel"
        const val NOTIFICATION_ID = 2003

        fun start(context: Context) {
            runCatching {
                val intent = Intent(context, AvailabilityService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure { Log.w(TAG, "start refused: ${it.message}") }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, AvailabilityService::class.java))
            }
        }
    }
}
