package com.vocis.startup

import com.vocis.VocisApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VocisApplicationStartupTest {

    @Test
    fun `test notification channel constants are properly configured`() {
        assertNotNull(VocisApplication.CHANNEL_PROTECTION)
        assertNotNull(VocisApplication.CHANNEL_EMERGENCY)

        assertTrue(VocisApplication.CHANNEL_PROTECTION.isNotEmpty())
        assertTrue(VocisApplication.CHANNEL_EMERGENCY.isNotEmpty())

        assertNotEquals(
            "Protection and emergency channels must be segregated",
            VocisApplication.CHANNEL_PROTECTION,
            VocisApplication.CHANNEL_EMERGENCY
        )
    }

    @Test
    fun `test application class inherits from android app Application`() {
        val appClass = VocisApplication::class.java
        assertTrue(
            "VocisApplication must extend android.app.Application",
            android.app.Application::class.java.isAssignableFrom(appClass)
        )
    }
}
