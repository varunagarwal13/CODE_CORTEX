package com.vocis.ui

import androidx.compose.ui.graphics.Color
import com.vocis.ui.theme.VocisAmber
import com.vocis.ui.theme.VocisBorder
import com.vocis.ui.theme.VocisCardWhite
import com.vocis.ui.theme.VocisCream
import com.vocis.ui.theme.VocisDark
import com.vocis.ui.theme.VocisGreen
import com.vocis.ui.theme.VocisGreenDark
import com.vocis.ui.theme.VocisGreenLight
import com.vocis.ui.theme.VocisLightGrey
import com.vocis.ui.theme.VocisMediumGrey
import com.vocis.ui.theme.VocisRed
import com.vocis.ui.theme.VocisTypography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeAndTokensTest {

    @Test
    fun `test warm cream figma background tokens are correct`() {
        // Figma #FAF7F2
        assertEquals(Color(0xFFFAF7F2), VocisCream)
        // Figma Card White #FFFFFF
        assertEquals(Color(0xFFFFFFFF), VocisCardWhite)
        // Figma Border #E8E4DC
        assertEquals(Color(0xFFE8E4DC), VocisBorder)
    }

    @Test
    fun `test forest green protection banner tokens are correct`() {
        // Figma Forest Green #2E6F40
        assertEquals(Color(0xFF2E6F40), VocisGreen)
        // Darker green #1E4D2B
        assertEquals(Color(0xFF1E4D2B), VocisGreenDark)
        // Light green #E8F5E9
        assertEquals(Color(0xFFE8F5E9), VocisGreenLight)
    }

    @Test
    fun `test onyx dark text and threat badge tokens`() {
        // Onyx #1C1C1E
        assertEquals(Color(0xFF1C1C1E), VocisDark)
        // Red threat #D32F2F
        assertEquals(Color(0xFFD32F2F), VocisRed)
        // Amber warning #F57C00
        assertEquals(Color(0xFFF57C00), VocisAmber)
        // Neutral greys
        assertEquals(Color(0xFF707074), VocisMediumGrey)
        assertEquals(Color(0xFF8E8E93), VocisLightGrey)
    }

    @Test
    fun `test typography tokens contain standard hierarchy`() {
        assertNotNull(VocisTypography.headlineLarge)
        assertNotNull(VocisTypography.titleLarge)
        assertNotNull(VocisTypography.bodyLarge)
        assertNotNull(VocisTypography.labelSmall)

        assertTrue(VocisTypography.headlineLarge.fontSize.value > VocisTypography.titleLarge.fontSize.value)
        assertTrue(VocisTypography.titleLarge.fontSize.value > VocisTypography.bodyLarge.fontSize.value)
    }
}
