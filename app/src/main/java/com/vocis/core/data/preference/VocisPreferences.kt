package com.vocis.core.data.preference

import android.content.Context
import android.content.SharedPreferences

data class UserProfile(
    val name: String = "",
    val email: String = "",
    val phone: String = ""
)

object VocisPreferences {
    private const val PREFS_NAME = "vocis_preferences"
    private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_USER_PHONE = "user_phone"
    private const val KEY_USER_SIGNED_IN = "user_signed_in"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isOnboardingCompleted(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun setOnboardingCompleted(context: Context, completed: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }

    fun isUserSignedIn(context: Context): Boolean {
        val signedIn = getPrefs(context).getBoolean(KEY_USER_SIGNED_IN, false)
        val name = getPrefs(context).getString(KEY_USER_NAME, "") ?: ""
        return signedIn && name.isNotBlank()
    }

    fun getUserProfile(context: Context): UserProfile {
        val prefs = getPrefs(context)
        return UserProfile(
            name = prefs.getString(KEY_USER_NAME, "") ?: "",
            email = prefs.getString(KEY_USER_EMAIL, "") ?: "",
            phone = prefs.getString(KEY_USER_PHONE, "") ?: ""
        )
    }

    fun saveUserProfile(context: Context, name: String, email: String, phone: String) {
        getPrefs(context).edit()
            .putString(KEY_USER_NAME, name.trim())
            .putString(KEY_USER_EMAIL, email.trim())
            .putString(KEY_USER_PHONE, phone.trim())
            .putBoolean(KEY_USER_SIGNED_IN, true)
            .apply()
    }

    fun clearUserProfile(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_USER_PHONE)
            .putBoolean(KEY_USER_SIGNED_IN, false)
            .apply()
    }
}
