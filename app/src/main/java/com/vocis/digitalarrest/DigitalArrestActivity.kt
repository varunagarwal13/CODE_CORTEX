package com.vocis.digitalarrest

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vocis.VocisApplication
import com.vocis.ui.screens.DigitalArrestScreen
import com.vocis.ui.theme.VocisTheme

/**
 * Dedicated Activity for Digital Arrest incident guided remediation.
 * Supports keyguard display and screen wake-up during high-threat extortion calls.
 */
class DigitalArrestActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        super.onCreate(savedInstanceState)

        configureWindowForLockscreen()

        val app = application as? VocisApplication
        val controller = app?.digitalArrestController ?: DigitalArrestController()

        val activeIncident = app?.interactionHub?.activeIncident?.value
        if (controller.state.value.incidentId == null) {
            controller.simulateTrigger(this, activeIncident)
        }

        setContent {
            VocisTheme {
                DigitalArrestScreen(
                    controller = controller,
                    onBack = { finish() }
                )
            }
        }
    }

    private fun configureWindowForLockscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
