package com.vocis.emergency

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vocis.ui.theme.VocisBorder
import com.vocis.ui.theme.VocisCardWhite
import com.vocis.ui.theme.VocisDark
import com.vocis.ui.theme.VocisMediumGrey
import com.vocis.ui.theme.VocisRed
import com.vocis.ui.theme.VocisRedLight
import com.vocis.ui.theme.VocisTheme

class EmergencyAlertActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SENDER = "extra_sender"
        const val EXTRA_MESSAGE = "extra_message"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureWindowForLockscreen()
        EmergencyAlarmSystem.startEmergencyAlarm(this)

        val sender = intent?.getStringExtra(EXTRA_SENDER) ?: "Family Contact"
        val message = intent?.getStringExtra(EXTRA_MESSAGE) ?: "Emergency VOCIS keyword trigger received."

        setContent {
            VocisTheme {
                EmergencyAlertScreen(
                    sender = sender,
                    message = message,
                    onSilenceAndDismiss = {
                        EmergencyAlarmSystem.stopEmergencyAlarm(this@EmergencyAlertActivity)
                        finish()
                    }
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

    override fun onDestroy() {
        super.onDestroy()
        EmergencyAlarmSystem.stopEmergencyAlarm(this)
    }
}

@Composable
fun EmergencyAlertScreen(
    sender: String,
    message: String,
    onSilenceAndDismiss: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "beacon")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF140808)) // Deep emergency slate crimson
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Emergency Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(VocisRedLight)
                    .border(1.dp, VocisRed, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "⚠️ EMERGENCY ACTIVE",
                    style = MaterialTheme.typography.labelLarge,
                    color = VocisRed,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }
        }

        // Center Beacon & Message Section
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size((110 * pulseScale).dp)
                    .clip(CircleShape)
                    .background(VocisRed.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(VocisRed),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🚨", fontSize = 38.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Emergency SOS Triggered",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "85% Siren Volume Override Running",
                style = MaterialTheme.typography.bodyMedium,
                color = VocisRed,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, VocisBorder, RoundedCornerShape(18.dp)),
                colors = CardDefaults.cardColors(containerColor = VocisCardWhite),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Alert Source",
                        style = MaterialTheme.typography.labelSmall,
                        color = VocisMediumGrey
                    )
                    Text(
                        text = sender,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = VocisDark
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Incident Details",
                        style = MaterialTheme.typography.labelSmall,
                        color = VocisMediumGrey
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = VocisDark
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "System Status",
                        style = MaterialTheme.typography.labelSmall,
                        color = VocisMediumGrey
                    )
                    Text(
                        text = "Vibration alert active • Keyguard dismissed • Screen stay-awake active",
                        style = MaterialTheme.typography.bodySmall,
                        color = VocisDark
                    )
                }
            }
        }

        // Action Button: Silence & Dismiss
        Column(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onSilenceAndDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VocisRed),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "SILENCE & DISMISS ALARM",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
