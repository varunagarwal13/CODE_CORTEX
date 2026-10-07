package com.vocis.ui

import com.vocis.ui.overlay.ProtectionOverlayManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.junit.runner.RunWith

/**
 * Tests ProtectionOverlayManager init and idempotent hide.
 * Requires Robolectric for Looper.getMainLooper() and WindowManager stubs.
 */
@RunWith(RobolectricTestRunner::class)
class ProtectionOverlayManagerTest {

    private lateinit var overlayManager: ProtectionOverlayManager

    @Before
    fun setUp() {
        overlayManager = ProtectionOverlayManager(RuntimeEnvironment.getApplication())
    }

    @Test
    fun `test overlay manager initializes with not showing state`() {
        assertNotNull(overlayManager)
        assertFalse("Overlay must not be showing initially", overlayManager.isShowing())
    }

    @Test
    fun `test hideOverlay is idempotent when not displayed`() {
        overlayManager.hideOverlay()
        assertFalse(overlayManager.isShowing())
    }

    @Test
    fun `test updateRisk does not crash when overlay is not displayed`() {
        overlayManager.updateRisk(85, "High Threat Level")
        assertFalse(overlayManager.isShowing())
    }
}
