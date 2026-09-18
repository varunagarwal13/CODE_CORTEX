package com.vocis.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
    var contactToDelete by remember { mutableStateOf<FamilyContactEntity?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    androidx.activity.compose.BackHandler {
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VocisCream)
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

        // Top Action Bar with Add Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val contactCount = contactsListState?.size ?: 0
            Text(
                text = "Authorized Contacts ($contactCount)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = VocisDark
            )

            Button(
                onClick = {
                    errorMessage = null
                    infoMessage = null
                    isAddingNew = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = VocisDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "+ Add Contact",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
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
                        Button(
                            onClick = {
                                errorMessage = null
                                infoMessage = null
                                isAddingNew = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VocisGreen),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "+ Add Emergency Contact",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
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
            initialName = "",
            initialPhone = "",
            initialRelationship = "Family",
            initialEmergencyAlert = true,
            confirmLabel = "Add Contact",
            onDismiss = { isAddingNew = false },
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
                    } catch (e: Exception) {
                        errorMessage = "Failed to add contact: ${e.localizedMessage}"
                    }
                }
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
    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(initialPhone) }
    var relationship by remember { mutableStateOf(initialRelationship) }
    var emergencyAlert by remember { mutableStateOf(initialEmergencyAlert) }
    var validationError by remember { mutableStateOf<String?>(null) }

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
            Column {
                if (!validationError.isNullOrBlank()) {
                    Text(
                        text = validationError!!,
                        color = VocisRed,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

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
                    if (phone.trim().isBlank()) {
                        validationError = "Phone Number cannot be blank."
                        return@Button
                    }
                    onConfirm(name, phone, relationship, emergencyAlert)
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
