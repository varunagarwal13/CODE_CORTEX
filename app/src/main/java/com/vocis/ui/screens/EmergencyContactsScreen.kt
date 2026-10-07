package com.vocis.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vocis.VocisApplication
import com.vocis.core.data.dao.FamilyContactDao
import com.vocis.core.data.database.AppDatabase
import com.vocis.core.data.entity.FamilyContactEntity
import com.vocis.intelligence.identity.E164Normalizer
import com.vocis.ui.theme.VocisBorder
import com.vocis.ui.theme.VocisCardWhite
import com.vocis.ui.theme.VocisCream
import com.vocis.ui.theme.VocisDark
import com.vocis.ui.theme.VocisGreen
import com.vocis.ui.theme.VocisGreenLight
import com.vocis.ui.theme.VocisGreenText
import com.vocis.ui.theme.VocisMediumGrey
import com.vocis.ui.theme.VocisRed
import com.vocis.ui.theme.VocisRedLight
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@Composable
fun EmergencyContactsScreen(
    contactDao: FamilyContactDao? = null,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val dao = remember(context, contactDao) {
        contactDao ?: try {
            (context.applicationContext as? VocisApplication)?.appDatabase?.familyContactDao()
                ?: AppDatabase.getInstance(context).familyContactDao()
        } catch (e: Exception) {
            null
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val contactsListState by (dao?.getAllFlow() ?: flowOf(emptyList())).collectAsState(initial = null)

    var contactToEdit by remember { mutableStateOf<FamilyContactEntity?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }
    var prefillName by remember { mutableStateOf("") }
    var prefillPhone by remember { mutableStateOf("") }
    var contactToDelete by remember { mutableStateOf<FamilyContactEntity?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var showInAppContactPicker by remember { mutableStateOf(false) }

    val mainContactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val contactUri: Uri? = result.data?.data
            if (contactUri != null) {
                try {
                    context.contentResolver.query(contactUri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                            val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                            val contactId = if (idIdx != -1) cursor.getString(idIdx) else null
                            val pickedName = if (nameIdx != -1) cursor.getString(nameIdx).orEmpty() else ""
                            var pickedNumber = ""

                            if (contactId != null) {
                                context.contentResolver.query(
                                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                                    arrayOf(contactId),
                                    null
                                )?.use { pCursor ->
                                    if (pCursor.moveToFirst()) {
                                        val numIdx = pCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                        if (numIdx != -1) pickedNumber = pCursor.getString(numIdx).orEmpty()
                                    }
                                }
                            }

                            if (pickedNumber.isBlank()) {
                                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                if (numIdx != -1) pickedNumber = cursor.getString(numIdx).orEmpty()
                            }

                            if (pickedNumber.isNotBlank()) {
                                prefillName = pickedName
                                prefillPhone = pickedNumber
                                errorMessage = null
                                isAddingNew = true
                            } else {
                                errorMessage = "The selected contact does not have a valid phone number."
                            }
                        }
                    }
                } catch (e: Exception) {
                    errorMessage = "Failed to access contact details: ${e.localizedMessage}"
                }
            }
        }
    }

    androidx.activity.compose.BackHandler {
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VocisCream)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Navigation Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(VocisCardWhite)
                    .border(1.dp, VocisBorder, RoundedCornerShape(12.dp))
                    .clickable { onBack() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "← Back",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = VocisDark
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Text(
                text = "Emergency Contacts",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = VocisDark
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status messages
        if (!infoMessage.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(VocisGreenLight)
                    .border(1.dp, VocisGreen, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = infoMessage!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VocisGreenText,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (!errorMessage.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(VocisRedLight)
                    .border(1.dp, VocisRed, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VocisRed,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Subtitle Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, VocisBorder, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = VocisCardWhite),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Trusted Emergency Network",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = VocisDark
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "When high-risk extortion or deepfakes are intercepted, VOCIS sends automated security alerts to enabled contacts below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VocisMediumGrey
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Top Action Bar with Add & Contact Picker Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val contactCount = contactsListState?.size ?: 0
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Authorized Contacts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = VocisDark
                )
                Text(
                    text = "$contactCount protected",
                    style = MaterialTheme.typography.bodySmall,
                    color = VocisMediumGrey
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        errorMessage = null
                        infoMessage = null
                        showInAppContactPicker = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VocisDark),
                    border = BorderStroke(1.dp, VocisBorder)
                ) {
                    Text(
                        text = "📱 Contacts",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = {
                        prefillName = ""
                        prefillPhone = ""
                        errorMessage = null
                        infoMessage = null
                        isAddingNew = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VocisDark),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "+ Add",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Content Area with Loading, Empty, and Success states
        when {
            contactsListState == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = VocisGreen)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Loading emergency contacts...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VocisMediumGrey
                        )
                    }
                }
            }

            contactsListState!!.isEmpty() -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                        .border(1.dp, VocisBorder, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = VocisCardWhite),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "👥",
                            fontSize = 40.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No emergency contacts added yet.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = VocisDark,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Add close family members or trusted guardians to establish your safety network.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VocisMediumGrey,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    errorMessage = null
                                    infoMessage = null
                                    showInAppContactPicker = true
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = VocisDark),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                            ) {
                                Text(
                                    text = "📱 Pick from Phone Contacts",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    prefillName = ""
                                    prefillPhone = ""
                                    errorMessage = null
                                    infoMessage = null
                                    isAddingNew = true
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = VocisDark),
                                border = BorderStroke(1.dp, VocisBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                            ) {
                                Text(
                                    text = "➕ Add Contact Manually",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(
                        items = contactsListState!!,
                        key = { it.phoneNumber }
                    ) { contact ->
                        ContactItemCard(
                            contact = contact,
                            onToggleSms = { enabled ->
                                coroutineScope.launch {
                                    try {
                                        dao?.insert(contact.copy(isEmergencyAlertEnabled = enabled))
                                        infoMessage = "${contact.name} alerts ${if (enabled) "enabled" else "disabled"}."
                                    } catch (e: Exception) {
                                        errorMessage = "Failed to update emergency SMS setting: ${e.localizedMessage}"
                                    }
                                }
                            },
                            onEdit = {
                                errorMessage = null
                                infoMessage = null
                                contactToEdit = contact
                            },
                            onDelete = {
                                errorMessage = null
                                infoMessage = null
                                contactToDelete = contact
                            }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    // Add Contact Dialog Form
    if (isAddingNew) {
        ContactFormDialog(
            title = "Add Emergency Contact",
            initialName = prefillName,
            initialPhone = prefillPhone,
            initialRelationship = "Family",
            initialEmergencyAlert = true,
            confirmLabel = "Add Contact",
            onDismiss = {
                isAddingNew = false
                prefillName = ""
                prefillPhone = ""
            },
            onConfirm = { name, phone, relationship, isAlertEnabled ->
                val normalizedPhone = E164Normalizer.normalize(phone.trim())
                if (name.isBlank()) {
                    errorMessage = "Contact name cannot be blank."
                    return@ContactFormDialog
                }
                if (phone.isBlank() || normalizedPhone == "UNKNOWN") {
                    errorMessage = "Please enter a valid phone number."
                    return@ContactFormDialog
                }

                coroutineScope.launch {
                    try {
                        val newContact = FamilyContactEntity(
                            phoneNumber = normalizedPhone,
                            name = name.trim(),
                            relationship = relationship.trim(),
                            addedAt = System.currentTimeMillis(),
                            isEmergencyAlertEnabled = isAlertEnabled
                        )
                        dao?.insert(newContact)
                        infoMessage = "Added ${newContact.name} to emergency contacts."
                        isAddingNew = false
                        prefillName = ""
                        prefillPhone = ""
                    } catch (e: Exception) {
                        errorMessage = "Failed to add contact: ${e.localizedMessage}"
                    }
                }
            }
        )
    }

    // In-App Deduplicated Contact Picker Dialog
    if (showInAppContactPicker) {
        DeviceContactPickerDialog(
            onDismiss = { showInAppContactPicker = false },
            onContactSelected = { name, phone ->
                prefillName = name
                prefillPhone = phone
                showInAppContactPicker = false
                isAddingNew = true
            },
            onOpenSystemPicker = {
                showInAppContactPicker = false
                val intent = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
                mainContactPickerLauncher.launch(intent)
            }
        )
    }

    // Edit Contact Dialog Form
    contactToEdit?.let { existing ->
        ContactFormDialog(
            title = "Edit Emergency Contact",
            initialName = existing.name,
            initialPhone = existing.phoneNumber,
            initialRelationship = existing.relationship,
            initialEmergencyAlert = existing.isEmergencyAlertEnabled,
            confirmLabel = "Save Changes",
            onDismiss = { contactToEdit = null },
            onConfirm = { name, phone, relationship, isAlertEnabled ->
                val normalizedPhone = E164Normalizer.normalize(phone.trim())
                if (name.isBlank()) {
                    errorMessage = "Contact name cannot be blank."
                    return@ContactFormDialog
                }
                if (phone.isBlank() || normalizedPhone == "UNKNOWN") {
                    errorMessage = "Please enter a valid phone number."
                    return@ContactFormDialog
                }

                coroutineScope.launch {
                    try {
                        // If phone number changed (Primary Key changed), delete old record
                        if (existing.phoneNumber != normalizedPhone) {
                            dao?.delete(existing)
                        }
                        val updated = FamilyContactEntity(
                            phoneNumber = normalizedPhone,
                            name = name.trim(),
                            relationship = relationship.trim(),
                            addedAt = existing.addedAt,
                            isEmergencyAlertEnabled = isAlertEnabled
                        )
                        dao?.insert(updated)
                        infoMessage = "Updated ${updated.name}."
                        contactToEdit = null
                    } catch (e: Exception) {
                        errorMessage = "Failed to update contact: ${e.localizedMessage}"
                    }
                }
            }
        )
    }

    // Delete Confirmation Dialog
    contactToDelete?.let { contact ->
        AlertDialog(
            onDismissRequest = { contactToDelete = null },
            title = {
                Text(
                    text = "Delete Emergency Contact?",
                    fontWeight = FontWeight.Bold,
                    color = VocisDark
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove ${contact.name} (${contact.phoneNumber}) from your emergency network? They will no longer receive automated emergency SOS alerts.",
                    color = VocisMediumGrey
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                dao?.delete(contact)
                                infoMessage = "Removed ${contact.name} from emergency contacts."
                                contactToDelete = null
                            } catch (e: Exception) {
                                errorMessage = "Failed to delete contact: ${e.localizedMessage}"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VocisRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Delete",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { contactToDelete = null },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Cancel",
                        color = VocisDark
                    )
                }
            },
            containerColor = VocisCardWhite,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun ContactItemCard(
    contact: FamilyContactEntity,
    onToggleSms: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, VocisBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = VocisCardWhite),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = contact.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = VocisDark
                        )

                        if (contact.relationship.isNotBlank()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(VocisCream)
                                    .border(1.dp, VocisBorder, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = contact.relationship,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VocisMediumGrey,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = contact.phoneNumber,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = VocisDark
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Edit button
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(VocisCream)
                            .border(1.dp, VocisBorder, CircleShape)
                            .clickable { onEdit() }
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "✏️",
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Delete button
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(VocisRedLight)
                            .border(1.dp, VocisRed.copy(alpha = 0.3f), CircleShape)
                            .clickable { onDelete() }
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "🗑️",
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Receive Emergency SMS Toggle Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(VocisCream)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Receive Emergency SMS",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = VocisDark
                    )
                    Text(
                        text = if (contact.isEmergencyAlertEnabled) "Automated dispatch enabled" else "Alerts disabled for this contact",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (contact.isEmergencyAlertEnabled) VocisGreenText else VocisMediumGrey
                    )
                }

                Switch(
                    checked = contact.isEmergencyAlertEnabled,
                    onCheckedChange = onToggleSms,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = VocisGreen,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = VocisMediumGrey.copy(alpha = 0.4f)
                    )
                )
            }
        }
    }
}

@Composable
fun ContactFormDialog(
    title: String,
    initialName: String,
    initialPhone: String,
    initialRelationship: String,
    initialEmergencyAlert: Boolean,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, phone: String, relationship: String, isEmergencyAlert: Boolean) -> Unit
) {
    val context = LocalContext.current
    var name by remember(initialName) { mutableStateOf(initialName) }
    var phone by remember(initialPhone) { mutableStateOf(initialPhone) }
    var relationship by remember(initialRelationship) { mutableStateOf(initialRelationship) }
    var emergencyAlert by remember(initialEmergencyAlert) { mutableStateOf(initialEmergencyAlert) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var showDialogPicker by remember { mutableStateOf(false) }

    val dialogContactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val contactUri: Uri? = result.data?.data
            if (contactUri != null) {
                try {
                    context.contentResolver.query(contactUri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                            val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                            val contactId = if (idIdx != -1) cursor.getString(idIdx) else null
                            val pickedName = if (nameIdx != -1) cursor.getString(nameIdx).orEmpty() else ""
                            var pickedNumber = ""

                            if (contactId != null) {
                                context.contentResolver.query(
                                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                                    arrayOf(contactId),
                                    null
                                )?.use { pCursor ->
                                    if (pCursor.moveToFirst()) {
                                        val numIdx = pCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                        if (numIdx != -1) pickedNumber = pCursor.getString(numIdx).orEmpty()
                                    }
                                }
                            }

                            if (pickedNumber.isBlank()) {
                                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                if (numIdx != -1) pickedNumber = cursor.getString(numIdx).orEmpty()
                            }

                            if (pickedName.isNotBlank()) name = pickedName
                            if (pickedNumber.isNotBlank()) phone = pickedNumber
                            validationError = null
                        }
                    }
                } catch (e: Exception) {
                    validationError = "Failed to load contact: ${e.localizedMessage}"
                }
            }
        }
    }

    if (showDialogPicker) {
        DeviceContactPickerDialog(
            onDismiss = { showDialogPicker = false },
            onContactSelected = { pickedName, pickedPhone ->
                name = pickedName
                phone = pickedPhone
                validationError = null
                showDialogPicker = false
            },
            onOpenSystemPicker = {
                showDialogPicker = false
                val intent = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
                dialogContactPickerLauncher.launch(intent)
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                color = VocisDark
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                if (!validationError.isNullOrBlank()) {
                    Text(
                        text = validationError!!,
                        color = VocisRed,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                OutlinedButton(
                    onClick = {
                        showDialogPicker = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VocisDark),
                    border = BorderStroke(1.dp, VocisBorder)
                ) {
                    Text(
                        text = "📱 Pick from Device Contacts",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        validationError = null
                    },
                    label = { Text("Contact Name") },
                    placeholder = { Text("e.g. Mom, Brother, Alice") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VocisGreen,
                        unfocusedBorderColor = VocisBorder
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = {
                        phone = it
                        validationError = null
                    },
                    label = { Text("Phone Number") },
                    placeholder = { Text("e.g. +91 9876543210") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VocisGreen,
                        unfocusedBorderColor = VocisBorder
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = relationship,
                    onValueChange = { relationship = it },
                    label = { Text("Relationship") },
                    placeholder = { Text("e.g. Parent, Spouse, Child, Friend") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VocisGreen,
                        unfocusedBorderColor = VocisBorder
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Receive Emergency SMS",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = VocisDark
                    )

                    Switch(
                        checked = emergencyAlert,
                        onCheckedChange = { emergencyAlert = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = VocisGreen
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.trim().isBlank()) {
                        validationError = "Contact Name cannot be blank."
                        return@Button
                    }
                    val digits = phone.filter { it.isDigit() }
                    val nationalNumber = when {
                        digits.startsWith("91") && digits.length == 12 -> digits.substring(2)
                        digits.startsWith("0") && digits.length == 11 -> digits.substring(1)
                        else -> digits
                    }
                    if (nationalNumber.length != 10) {
                        validationError = "Indian mobile number must be exactly 10 digits."
                        return@Button
                    }
                    if (!nationalNumber.first().let { it in '6'..'9' }) {
                        validationError = "Indian mobile number must start with 6, 7, 8, or 9."
                        return@Button
                    }
                    onConfirm(name.trim(), "+91$nationalNumber", relationship, emergencyAlert)
                },
                colors = ButtonDefaults.buttonColors(containerColor = VocisDark),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = confirmLabel,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Cancel",
                    color = VocisDark
                )
            }
        },
        containerColor = VocisCardWhite,
        shape = RoundedCornerShape(16.dp)
    )
}

data class DeviceContactItem(
    val name: String,
    val phoneNumber: String
)

@Composable
fun DeviceContactPickerDialog(
    onDismiss: () -> Unit,
    onContactSelected: (name: String, phone: String) -> Unit,
    onOpenSystemPicker: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val contactsList = remember {
        val list = mutableListOf<DeviceContactItem>()
        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )
            cursor?.use { c ->
                val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (c.moveToNext()) {
                    val name = if (nameIdx != -1) c.getString(nameIdx).orEmpty().trim() else ""
                    val num = if (numIdx != -1) c.getString(numIdx).orEmpty().trim() else ""
                    if (name.isNotBlank() && num.isNotBlank()) {
                        list.add(DeviceContactItem(name, num))
                    }
                }
            }
        } catch (_: Exception) {}

        // Deduplicate contacts by name and normalized phone digits so no duplicate accounts appear
        list.distinctBy {
            val digits = it.phoneNumber.filter { ch -> ch.isDigit() || ch == '+' }
            "${it.name.lowercase().trim()}_$digits"
        }.sortedBy { it.name.lowercase() }
    }

    val filteredContacts = remember(searchQuery, contactsList) {
        if (searchQuery.isBlank()) {
            contactsList
        } else {
            contactsList.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.phoneNumber.contains(searchQuery)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.82f)
                .border(1.dp, VocisBorder, RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = VocisCardWhite),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Select Contact",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = VocisDark
                        )
                        Text(
                            text = "${filteredContacts.size} contacts found",
                            style = MaterialTheme.typography.bodySmall,
                            color = VocisMediumGrey
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(VocisCream)
                            .border(1.dp, VocisBorder, CircleShape)
                            .clickable { onDismiss() }
                            .padding(8.dp)
                    ) {
                        Text("✕", fontSize = 14.sp, color = VocisDark)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by name or number...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VocisGreen,
                        unfocusedBorderColor = VocisBorder
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (filteredContacts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🔍", fontSize = 32.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (contactsList.isEmpty()) "No device contacts found or permission needed." else "No contacts matching \"$searchQuery\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = VocisMediumGrey,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredContacts) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(VocisCream)
                                    .border(1.dp, VocisBorder, RoundedCornerShape(12.dp))
                                    .clickable {
                                        onContactSelected(item.name, item.phoneNumber)
                                        onDismiss()
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(VocisGreenLight)
                                        .border(1.dp, VocisGreen, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = item.name.firstOrNull()?.uppercase() ?: "#",
                                        fontWeight = FontWeight.Bold,
                                        color = VocisGreenText,
                                        fontSize = 16.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = VocisDark
                                    )
                                    Text(
                                        text = item.phoneNumber,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = VocisMediumGrey
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onOpenSystemPicker()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VocisDark),
                    border = BorderStroke(1.dp, VocisBorder)
                ) {
                    Text(
                        text = "📱 Open System Contacts App",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
