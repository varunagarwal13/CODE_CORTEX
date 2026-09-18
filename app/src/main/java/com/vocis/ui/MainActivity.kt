package com.vocis.ui

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.vocis.VocisApplication
import com.vocis.digitalarrest.DigitalArrestActivity
import com.vocis.emergency.EmergencyAlertActivity
import com.vocis.ui.screens.BiometricShell
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
        val app = application as? VocisApplication
        app?.interactionHub?.loadRealCallLogs(this)
    }

    private val callScreeningRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkAllPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Set light status and navigation bars for pristine modern appearance
        val window = this.window
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        val granted = checkAllPermissions()
        if (!granted) {
            requestRequiredPermissions()
        }

        val app = application as? VocisApplication
        val hub = app?.interactionHub
        val arrestController = app?.digitalArrestController

        // Load real call logs from device if permission is available
        hub?.loadRealCallLogs(this)

        setContent {
            VocisTheme {
                var showSplash by remember { mutableStateOf(false) }

                if (showSplash) {
                    SplashScreen(
                        onSplashFinished = { showSplash = false }
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

    override fun onResume() {
        super.onResume()
        val app = application as? VocisApplication
        app?.interactionHub?.loadRealCallLogs(this)
    }

    fun requestRequiredPermissions() {
        // Request Android Runtime Permissions gently
        permissionLauncher.launch(standardPermissions)

        // Request Call Screening Role on Android Q+ if available
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager != null &&
                roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
            ) {
                try {
                    val roleIntent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                    callScreeningRoleLauncher.launch(roleIntent)
                } catch (_: Exception) {}
            }
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
