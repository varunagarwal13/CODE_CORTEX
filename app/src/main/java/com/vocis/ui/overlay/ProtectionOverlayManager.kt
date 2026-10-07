package com.vocis.ui.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Manages the floating system window overlay that displays VOCIS live threat assessment
 * during active phone or VoIP calls.
 * Uses TYPE_APPLICATION_OVERLAY with FLAG_NOT_FOCUSABLE to avoid intercepting user touches
 * on background applications while providing draggable position adjustment.
 */
class ProtectionOverlayManager(private val context: Context) {

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isDisplayed = false

    private var dotView: View? = null
    private var statusText: TextView? = null
    private var scoreText: TextView? = null
    private var pillLayout: LinearLayout? = null

    /**
     * Checks if the app has permission to draw system overlays.
     */
    fun canDrawOverlay(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * Shows the floating overlay pill.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun showOverlay(threatScore: Int = 0, status: String = "Monitoring Call") {
        mainHandler.post {
            if (isDisplayed) {
                updateRisk(threatScore, status)
                return@post
            }
            if (!canDrawOverlay()) return@post

            val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 50
                y = 150
            }

            val container = FrameLayout(context).apply {
                setPadding(8, 8, 8, 8)
            }

            pillLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(32, 20, 36, 20)
                elevation = 16f

                // Shape background with rounded pill
                background = createPillBackground(threatScore)

                // Shield Icon / Status Indicator dot
                dotView = View(context).apply {
                    val dotSize = 24
                    layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                        marginEnd = 16
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(getColorForThreat(threatScore))
                    }
                }
                addView(dotView)

                // Status label
                statusText = TextView(context).apply {
                    text = "VOCIS: $status"
                    setTextColor(Color.WHITE)
                    textSize = 13f
                    paint.isFakeBoldText = true
                }
                addView(statusText)

                // Risk badge
                scoreText = TextView(context).apply {
                    text = " $threatScore%"
                    setTextColor(getColorForThreat(threatScore))
                    textSize = 13f
                    paint.isFakeBoldText = true
                    setPadding(12, 0, 0, 0)
                }
                addView(scoreText)
            }

            container.addView(pillLayout)

            // Draggable touch handling
            container.setOnTouchListener(object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f
                private var isDragging = false

                override fun onTouch(v: View?, event: MotionEvent): Boolean {
                    val params = layoutParams ?: return false
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = params.x
                            initialY = params.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            isDragging = false
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - initialTouchX).toInt()
                            val dy = (event.rawY - initialTouchY).toInt()
                            if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                                isDragging = true
                            }
                            params.x = initialX + dx
                            params.y = initialY + dy
                            try {
                                windowManager.updateViewLayout(overlayView, params)
                            } catch (e: Exception) {
                                // Ignored if view detached
                            }
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (!isDragging) {
                                try {
                                    val launchIntent = context.packageManager
                                        .getLaunchIntentForPackage(context.packageName)
                                        ?.apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                        }
                                    launchIntent?.let { context.startActivity(it) }
                                } catch (e: Exception) {
                                    // Ignore
                                }
                            }
                            return true
                        }
                    }
                    return false
                }
            })

            overlayView = container
            try {
                windowManager.addView(overlayView, layoutParams)
                isDisplayed = true
            } catch (e: Exception) {
                isDisplayed = false
                overlayView = null
            }
        }
    }

    /**
     * Updates the threat risk score and status in real-time. Thread-safe.
     */
    fun updateRisk(threatScore: Int, status: String) {
        mainHandler.post {
            if (!isDisplayed) return@post
            statusText?.text = "VOCIS: $status"
            scoreText?.apply {
                text = " $threatScore%"
                setTextColor(getColorForThreat(threatScore))
            }
            dotView?.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(getColorForThreat(threatScore))
            }
            pillLayout?.background = createPillBackground(threatScore)
        }
    }

    /**
     * Removes the overlay from the window manager.
     */
    fun hideOverlay() {
        mainHandler.post {
            if (isDisplayed && overlayView != null) {
                try {
                    windowManager.removeView(overlayView)
                } catch (e: Exception) {
                    // View might already be detached
                }
                overlayView = null
                isDisplayed = false
            }
        }
    }

    fun isShowing(): Boolean = isDisplayed

    private fun getColorForThreat(score: Int): Int {
        return when {
            score >= 70 -> Color.parseColor("#E53935") // Red alert
            score >= 40 -> Color.parseColor("#FB8C00") // Amber warning
            else -> Color.parseColor("#43A047")        // Safe green
        }
    }

    private fun createPillBackground(threatScore: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 50f
            setColor(Color.parseColor("#1C1C1E")) // Onyx dark
            setStroke(3, getColorForThreat(threatScore))
        }
    }
}
