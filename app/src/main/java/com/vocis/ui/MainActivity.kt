package com.vocis.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vocis.VocisApplication
import com.vocis.core.data.preference.VocisPreferences
import com.vocis.digitalarrest.DigitalArrestActivity
import com.vocis.emergency.EmergencyAlertActivity
import com.vocis.ui.screens.BiometricShell
import com.vocis.ui.screens.OnboardingScreen
import com.vocis.ui.screens.SplashScreen
import com.vocis.ui.theme.VocisTheme

class MainActivity : ComponentActivity() {

    private var hasAllPermissions by mutableStateOf(false)

    // Standard runtime permissions
    private val standardPermissions = mutableListOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CONTACTS
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        checkAllPermissions()
    }

    private val callScreeningRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkAllPermissions()
    }

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

        checkAllPermissions()

        val app = application as? VocisApplication
        val hub = app?.interactionHub
        val arrestController = app?.digitalArrestController
        val onboardingCompleted = VocisPreferences.isOnboardingCompleted(this)

        setContent {
            VocisTheme {
                var showSplash by remember { mutableStateOf(true) }
                var currentOnboarding by remember { mutableStateOf(!onboardingCompleted) }

                if (showSplash) {
                    SplashScreen(
                        onSplashFinished = { showSplash = false }
                    )
                } else if (currentOnboarding) {
                    OnboardingScreen(
                        onRequestPermissions = {
                            requestRequiredPermissions()
                        },
                        onCompleteOnboarding = {
                            VocisPreferences.setOnboardingCompleted(this@MainActivity, true)
                            currentOnboarding = false
                            requestRequiredPermissions()
                        }
                    )
                } else {
                    BiometricShell(
                        interactionHub = hub,
                        digitalArrestController = arrestController,
                        onNavigateToDigitalArrest = {
                            val intent = Intent(this@MainActivity, DigitalArrestActivity::class.java)
                            startActivity(intent)
                        },
                        onNavigateToEmergency = {
                            val intent = Intent(this@MainActivity, EmergencyAlertActivity::class.java).apply {
                                putExtra(EmergencyAlertActivity.EXTRA_SENDER, "Security Command")
                                putExtra(EmergencyAlertActivity.EXTRA_MESSAGE, "Manual SOS triggered from VOCIS console.")
                            }
                            startActivity(intent)
                        }
                    )
                }
            }
        }
    }

    fun requestRequiredPermissions() {
        // 1. Request Android Runtime Permissions
        permissionLauncher.launch(standardPermissions)

        // 2. Request Call Screening Role on Android Q+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager != null &&
                roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
            ) {
                val roleIntent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                callScreeningRoleLauncher.launch(roleIntent)
            }
        }

        // 3. Request Overlay Permission if missing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val overlayIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(overlayIntent)
        }
    }

    fun checkAllPermissions(): Boolean {
        var granted = true
        for (perm in standardPermissions) {
            if (checkSelfPermission(perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                granted = false
                break
            }
        }
        hasAllPermissions = granted
        return granted
    }
}
