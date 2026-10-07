package com.vocis.ui.screens

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.vocis.core.data.preference.VocisPreferences
import com.vocis.intelligence.identity.E164Normalizer
import com.vocis.ui.components.VocisLogoBadge
import com.vocis.ui.theme.VocisBorder
import com.vocis.ui.theme.VocisCardWhite
import com.vocis.ui.theme.VocisCream
import com.vocis.ui.theme.VocisDark
import com.vocis.ui.theme.VocisGreen
import com.vocis.ui.theme.VocisGreenText
import com.vocis.ui.theme.VocisMediumGrey
import com.vocis.ui.theme.VocisRed
import kotlinx.coroutines.delay

private const val ACTION_SMS_SENT = "com.vocis.ACTION_SMS_SENT"

@Composable
fun SignInScreen(
    isEditMode: Boolean = false,
    onSignInSuccess: () -> Unit,
    onCancel: () -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val initialProfile = remember { VocisPreferences.getUserProfile(context) }
    var step by remember { mutableIntStateOf(1) } // 1: Input details, 2: Mobile OTP verification

    // Form fields
    var name by remember { mutableStateOf(initialProfile.name) }
    var email by remember { mutableStateOf(initialProfile.email) }
    var phone by remember { mutableStateOf(initialProfile.phone) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }

    // OTP state (Mobile only)
    var generatedOtp by remember { mutableStateOf("") }
    var otpInput by remember { mutableStateOf("") }
    var otpError by remember { mutableStateOf<String?>(null) }
    var showCarrierHelpDialog by remember { mutableStateOf(false) }

    var resendCooldown by remember { mutableIntStateOf(60) }

    // Broadcast receiver for carrier SMS dispatch feedback
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        Toast.makeText(context, "SMS delivered by carrier", Toast.LENGTH_SHORT).show()
                    }
                    SmsManager.RESULT_ERROR_NO_SERVICE -> {
                        Toast.makeText(context, "No cellular service. Check network.", Toast.LENGTH_LONG).show()
                    }
                    SmsManager.RESULT_ERROR_RADIO_OFF -> {
                        Toast.makeText(context, "Cellular radio off. Disable Airplane mode.", Toast.LENGTH_LONG).show()
                    }
                    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                        // Often happens with TRAI loopback restrictions or zero SMS balance
                        Toast.makeText(context, "Carrier could not deliver self-SMS. Tap 'Didn't receive code?' if needed.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        val filter = IntentFilter(ACTION_SMS_SENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }

    // Resend countdown timer for Step 2
    LaunchedEffect(step, resendCooldown) {
        if (step == 2 && resendCooldown > 0) {
            delay(1000L)
            resendCooldown -= 1
        }
    }

    fun dispatchMobileOtp() {
        val newOtp = (100000..999999).random().toString()
        generatedOtp = newOtp
        resendCooldown = 60
        otpInput = ""
        otpError = null

        val digits = phone.filter { it.isDigit() }
        val nationalNumber = when {
            digits.startsWith("91") && digits.length == 12 -> digits.substring(2)
            digits.startsWith("0") && digits.length == 11 -> digits.substring(1)
            else -> digits
        }
        val targetNumber = "+91$nationalNumber"
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }

                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val sentIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(ACTION_SMS_SENT),
                    flags
                )

                smsManager?.sendTextMessage(
                    targetNumber,
                    null,
                    "VOCIS verification code: $newOtp",
                    sentIntent,
                    null
                )
                Toast.makeText(context, "Sending SMS to $targetNumber...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "SMS permission required to send code", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "SMS sending error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        dispatchMobileOtp()
        step = 2
    }

    fun validateEmailRules(emailStr: String): String? {
        val trimmed = emailStr.trim()
        if (trimmed.isBlank()) return "Please enter your email address"
        if (trimmed.length > 254) return "Email address is too long"
        if (trimmed.contains(" ")) return "Email cannot contain spaces"

        val parts = trimmed.split("@")
        if (parts.size != 2) return "Email must contain exactly one '@' symbol"

        val username = parts[0]
        val domain = parts[1]
        if (username.length < 2) return "Email username is too short"
        if (!domain.contains(".")) return "Email domain must contain a valid extension (e.g. .com)"

        val domainParts = domain.split(".")
        val tld = domainParts.last()
        if (tld.length < 2) return "Invalid domain extension"

        val emailPattern = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$".toRegex()
        if (!emailPattern.matches(trimmed)) return "Please enter a valid email (e.g. name@gmail.com)"

        return null
    }

    fun validateStep1AndProceed() {
        var isValid = true

        if (name.trim().length < 2) {
            nameError = "Enter your full name"
            isValid = false
        } else {
            nameError = null
        }

        val emailRuleError = validateEmailRules(email)
        if (emailRuleError != null) {
            emailError = emailRuleError
            isValid = false
        } else {
            emailError = null
        }

        val digits = phone.filter { it.isDigit() }
        val nationalNumber = when {
            digits.startsWith("91") && digits.length == 12 -> digits.substring(2)
            digits.startsWith("0") && digits.length == 11 -> digits.substring(1)
            else -> digits
        }
        if (nationalNumber.length != 10) {
            phoneError = "Indian mobile number must be 10 digits"
            isValid = false
        } else if (!nationalNumber.first().let { it in '6'..'9' }) {
            phoneError = "Indian mobile number must start with 6, 7, 8, or 9"
            isValid = false
        } else {
            phoneError = null
        }

        if (isValid) {
            val permissionsToRequest = mutableListOf(Manifest.permission.SEND_SMS)
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    fun verifyOtpAndActivate() {
        if (otpInput.trim() != generatedOtp) {
            otpError = "Incorrect verification code"
        } else {
            otpError = null
            VocisPreferences.saveUserProfile(
                context = context,
                name = name.trim(),
                email = email.trim(),
                phone = phone.trim()
            )
            onSignInSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VocisCream)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            VocisLogoBadge(size = 56.dp)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (step == 1) {
                    if (isEditMode) "Edit Profile" else "Sign In"
                } else {
                    "Verify Mobile Number"
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = VocisDark,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (step == 1) {
                    "Enter your details to continue."
                } else {
                    "Enter the 6-digit code sent via SMS to $phone"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = VocisMediumGrey,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (step == 1) {
                // ── STEP 1: Details Input ───────────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = VocisCardWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Full Name
                        Column {
                            Text(
                                text = "Full Name",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = VocisDark
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = name,
                                onValueChange = {
                                    name = it
                                    if (nameError != null) nameError = null
                                },
                                placeholder = { Text("Your Name") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Person, contentDescription = null, tint = VocisGreen)
                                },
                                singleLine = true,
                                isError = nameError != null,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = VocisGreen,
                                    unfocusedBorderColor = VocisBorder,
                                    errorBorderColor = VocisRed
                                )
                            )
                            if (nameError != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = nameError!!,
                                    color = VocisRed,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Email Address (Rule-verified)
                        Column {
                            Text(
                                text = "Email Address",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = VocisDark
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = email,
                                onValueChange = {
                                    email = it
                                    if (emailError != null) emailError = null
                                },
                                placeholder = { Text("name@gmail.com") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Email, contentDescription = null, tint = VocisGreen)
                                },
                                singleLine = true,
                                isError = emailError != null,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = VocisGreen,
                                    unfocusedBorderColor = VocisBorder,
                                    errorBorderColor = VocisRed
                                )
                            )
                            if (emailError != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = emailError!!,
                                    color = VocisRed,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Mobile Number
                        Column {
                            Text(
                                text = "Mobile Number",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = VocisDark
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = phone,
                                onValueChange = {
                                    phone = it
                                    if (phoneError != null) phoneError = null
                                },
                                placeholder = { Text("+91 98765 43210") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Phone, contentDescription = null, tint = VocisGreen)
                                },
                                singleLine = true,
                                isError = phoneError != null,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Phone,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = {
                                    focusManager.clearFocus()
                                    validateStep1AndProceed()
                                }),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = VocisGreen,
                                    unfocusedBorderColor = VocisBorder,
                                    errorBorderColor = VocisRed
                                )
                            )
                            if (phoneError != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = phoneError!!,
                                    color = VocisRed,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { validateStep1AndProceed() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VocisGreen),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "Continue",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                if (isEditMode) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Cancel", color = VocisMediumGrey)
                    }
                }
            } else {
                // ── STEP 2: Single Mobile OTP Verification ──────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = VocisCardWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column {
                            Text(
                                text = "Enter 6-Digit SMS Code",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = VocisDark
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = otpInput,
                                onValueChange = {
                                    if (it.length <= 6) {
                                        otpInput = it.filter { ch -> ch.isDigit() }
                                        if (otpError != null) otpError = null
                                    }
                                },
                                placeholder = { Text("6-digit code") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Phone, contentDescription = null, tint = VocisGreen)
                                },
                                trailingIcon = {
                                    if (otpInput.length == 6) {
                                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = VocisGreen)
                                    }
                                },
                                singleLine = true,
                                isError = otpError != null,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.NumberPassword,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = {
                                    focusManager.clearFocus()
                                    verifyOtpAndActivate()
                                }),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = VocisGreen,
                                    unfocusedBorderColor = VocisBorder,
                                    errorBorderColor = VocisRed
                                )
                            )
                            if (otpError != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = otpError!!,
                                    color = VocisRed,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { step = 1 }) {
                        Text("← Change Details", color = VocisMediumGrey, fontSize = 13.sp)
                    }

                    if (resendCooldown > 0) {
                        Text(
                            text = "Resend in ${resendCooldown}s",
                            color = VocisMediumGrey,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    } else {
                        TextButton(onClick = { dispatchMobileOtp() }) {
                            Text("Resend SMS", color = VocisGreenText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Carrier Loopback Help
                TextButton(
                    onClick = { showCarrierHelpDialog = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = "Didn't receive SMS code?",
                        color = VocisMediumGrey,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { verifyOtpAndActivate() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VocisGreen),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "Verify & Continue",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                if (isEditMode) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Cancel", color = VocisMediumGrey)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Carrier SMS Loopback explanation dialog
        if (showCarrierHelpDialog) {
            AlertDialog(
                onDismissRequest = { showCarrierHelpDialog = false },
                title = {
                    Text("SMS Carrier Information", fontWeight = FontWeight.Bold, color = VocisDark)
                },
                text = {
                    Column {
                        Text(
                            text = "Telecom operators (Jio, Airtel, Vi) sometimes restrict devices from receiving automated SMS sent to their own SIM number without DLT corporate registration, or if the SIM has no SMS balance.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VocisMediumGrey,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Your verification code: $generatedOtp",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = VocisDark
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            otpInput = generatedOtp
                            showCarrierHelpDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VocisGreen)
                    ) {
                        Text("Use Code", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCarrierHelpDialog = false }) {
                        Text("Close", color = VocisMediumGrey)
                    }
                }
            )
        }
    }
}
