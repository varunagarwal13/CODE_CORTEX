package com.vocis

import android.content.Context
import android.content.SharedPreferences
import com.vocis.core.data.preference.VocisPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class VocisPreferencesTest {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        sharedPreferences = mock(SharedPreferences::class.java)
        editor = mock(SharedPreferences.Editor::class.java)

        `when`(context.getSharedPreferences("vocis_preferences", Context.MODE_PRIVATE)).thenReturn(sharedPreferences)
        `when`(sharedPreferences.edit()).thenReturn(editor)
        `when`(editor.putString(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).thenReturn(editor)
        `when`(editor.putBoolean(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(editor)
        `when`(editor.remove(org.mockito.ArgumentMatchers.anyString())).thenReturn(editor)
    }

    @Test
    fun `saveUserProfile writes user details and sets signed in flag`() {
        VocisPreferences.saveUserProfile(
            context = context,
            name = "Jane Doe",
            email = "jane@example.com",
            phone = "+91 9876543210"
        )

        verify(editor).putString("user_name", "Jane Doe")
        verify(editor).putString("user_email", "jane@example.com")
        verify(editor).putString("user_phone", "+91 9876543210")
        verify(editor).putBoolean("user_signed_in", true)
        verify(editor).apply()
    }

    @Test
    fun `getUserProfile reads all saved fields`() {
        `when`(sharedPreferences.getString("user_name", "")).thenReturn("Jane Doe")
        `when`(sharedPreferences.getString("user_email", "")).thenReturn("jane@example.com")
        `when`(sharedPreferences.getString("user_phone", "")).thenReturn("+91 9876543210")

        val profile = VocisPreferences.getUserProfile(context)

        assertEquals("Jane Doe", profile.name)
        assertEquals("jane@example.com", profile.email)
        assertEquals("+91 9876543210", profile.phone)
    }

    @Test
    fun `isUserSignedIn returns true only when signed in flag is true and name is not blank`() {
        `when`(sharedPreferences.getBoolean("user_signed_in", false)).thenReturn(true)
        `when`(sharedPreferences.getString("user_name", "")).thenReturn("Jane Doe")
        assertTrue(VocisPreferences.isUserSignedIn(context))

        `when`(sharedPreferences.getString("user_name", "")).thenReturn("")
        assertFalse(VocisPreferences.isUserSignedIn(context))

        `when`(sharedPreferences.getBoolean("user_signed_in", false)).thenReturn(false)
        `when`(sharedPreferences.getString("user_name", "")).thenReturn("Jane Doe")
        assertFalse(VocisPreferences.isUserSignedIn(context))
    }

    @Test
    fun `clearUserProfile removes user keys and marks signed out`() {
        VocisPreferences.clearUserProfile(context)

        verify(editor).remove("user_name")
        verify(editor).remove("user_email")
        verify(editor).remove("user_phone")
        verify(editor).putBoolean("user_signed_in", false)
        verify(editor).apply()
    }
}
