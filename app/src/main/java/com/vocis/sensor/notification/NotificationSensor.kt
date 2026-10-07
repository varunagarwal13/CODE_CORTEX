package com.vocis.sensor.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.vocis.intelligence.hub.InteractionHub
import com.vocis.sensor.normalizer.NotificationNormalizer

class NotificationSensor : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val packageName = sbn.packageName ?: return

        // Skip our own notifications
        if (packageName == applicationContext.packageName) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()

        val event = NotificationNormalizer.normalize(
            packageName = packageName,
            title = title,
            text = text,
            bigText = bigText,
            subText = subText,
            timestampMs = sbn.postTime
        )
        InteractionHub.getInstance(applicationContext).processEventAsync(event)

        val category = sbn.notification?.category
        if (category == Notification.CATEGORY_CALL) {
            try {
                val app = applicationContext as? com.vocis.VocisApplication
                app?.protectionOverlayManager?.showOverlay(threatScore = 0, status = "Call Detected • VOCIS Shield")
            } catch (_: Exception) {}
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val category = sbn?.notification?.category
        if (category == Notification.CATEGORY_CALL) {
            try {
                val app = applicationContext as? com.vocis.VocisApplication
                app?.protectionOverlayManager?.hideOverlay()
            } catch (_: Exception) {}
        }
    }
}
