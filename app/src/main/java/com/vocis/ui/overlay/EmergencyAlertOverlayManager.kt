package com.vocis.ui.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.vocis.emergency.EmergencyAlarmSystem

/**
 * Manages high-priority floating emergency alert banner on top of all system windows.
 * Displays immediate acoustic & linguistic alarm notification with silence action.
 */
class EmergencyAlertOverlayManager(private val context: Context) {

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var alertView: View? = null
    private var isAlertShowing = false

    fun canDrawOverlay(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * Displays a full-width floating emergency SOS alert across any active screen.
     */
    @SuppressLint("InflateParams")
    fun showEmergencyAlert(sender: String, message: String, onDismiss: () -> Unit = {}) {
        if (isAlertShowing || !canDrawOverlay()) return

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = 80
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(40, 32, 40, 32)
            elevation = 24f

            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24f
                setColor(Color.parseColor("#1A0000")) // Deep Crimson
                setStroke(4, Color.parseColor("#E53935")) // Bright Red Stroke
            }

            // Title
            val title = TextView(context).apply {
                text = "🚨 VOCIS EMERGENCY SOS TRIGGERED"
                setTextColor(Color.parseColor("#FF5252"))
                textSize = 17f
                paint.isFakeBoldText = true
                gravity = Gravity.CENTER
            }
            addView(title)

            // Sender
            val senderLabel = TextView(context).apply {
                text = "Sender: $sender"
                setTextColor(Color.WHITE)
                textSize = 14f
                setPadding(0, 12, 0, 4)
                gravity = Gravity.CENTER
            }
            addView(senderLabel)

            // Message
            val msgLabel = TextView(context).apply {
                text = message
                setTextColor(Color.LTGRAY)
                textSize = 13f
                setPadding(0, 0, 0, 20)
                gravity = Gravity.CENTER
            }
            addView(msgLabel)

            // Dismiss Button
            val dismissBtn = Button(context).apply {
                text = "DISMISS & SILENCE ALARM"
                setTextColor(Color.WHITE)
                textSize = 13f
                paint.isFakeBoldText = true
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 16f
                    setColor(Color.parseColor("#E53935"))
                }
                setPadding(32, 16, 32, 16)
                setOnClickListener {
                    EmergencyAlarmSystem.stopEmergencyAlarm(context)
                    hideAlert()
                    onDismiss()
                }
            }
            addView(dismissBtn)
        }

        alertView = layout
        try {
            windowManager.addView(alertView, params)
            isAlertShowing = true
        } catch (e: Exception) {
            isAlertShowing = false
            alertView = null
        }
    }

    fun hideAlert() {
        if (isAlertShowing && alertView != null) {
            try {
                windowManager.removeView(alertView)
            } catch (e: Exception) {
                // View may already be removed
            }
            alertView = null
            isAlertShowing = false
        }
    }

    fun isShowing(): Boolean = isAlertShowing
}
