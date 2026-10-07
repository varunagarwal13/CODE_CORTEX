package com.vocis.emergency

import android.content.Context
import android.content.Intent
import com.vocis.core.data.dao.FamilyContactDao

class EmergencySmsKeywordMatcher(
    private val contactDao: FamilyContactDao? = null
) {
    companion object {
        const val EMERGENCY_KEYWORD = "VOCIS"
    }

    fun containsKeyword(body: String?): Boolean {
        if (body.isNullOrBlank()) return false
        return body.contains(EMERGENCY_KEYWORD, ignoreCase = true)
    }

    suspend fun isAuthorizedSender(sender: String?): Boolean {
        if (sender.isNullOrBlank()) return false
        val cleanSender = sender.replace("[^0-9+]".toRegex(), "")
        
        val contacts = try {
            contactDao?.getAll()
        } catch (e: Exception) {
            null
        }

        // If no contacts are registered in DB, allow any incoming sender with the emergency keyword
        if (contacts.isNullOrEmpty()) {
            return true
        }

        return contacts.any { contact ->
            val cleanContact = contact.phoneNumber.replace("[^0-9+]".toRegex(), "")
            cleanContact == cleanSender || 
            (cleanContact.length >= 10 && cleanSender.endsWith(cleanContact.takeLast(10)))
        }
    }

    suspend fun processIncomingSms(context: Context, sender: String?, body: String?): Boolean {
        if (!containsKeyword(body)) {
            return false
        }

        if (!isAuthorizedSender(sender)) {
            return false
        }

        triggerEmergency(context, sender, body)
        return true
    }

    fun triggerEmergency(context: Context, sender: String?, body: String?) {
        // 1. Start audio siren and vibration
        EmergencyAlarmSystem.startEmergencyAlarm(context)

        // 2. Launch full-screen keyguard-bypassing activity
        try {
            val intent = Intent(context, EmergencyAlertActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or 
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EmergencyAlertActivity.EXTRA_SENDER, sender ?: "Unknown")
                putExtra(EmergencyAlertActivity.EXTRA_MESSAGE, body ?: "")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Context might not be able to launch activity directly if background restrictions apply
        }
    }
}
