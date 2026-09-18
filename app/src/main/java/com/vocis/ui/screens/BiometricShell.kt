package com.vocis.ui.screens

import androidx.activity.compose.BackHandler

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vocis.core.data.entity.InteractionEntity
import com.vocis.digitalarrest.DigitalArrestController
import com.vocis.intelligence.hub.InteractionHub
import com.vocis.ui.theme.VocisBorder
import com.vocis.ui.theme.VocisCardWhite
import com.vocis.ui.theme.VocisCream
import com.vocis.ui.theme.VocisDark
import com.vocis.ui.theme.VocisGreen
import com.vocis.ui.theme.VocisGreenLight
import com.vocis.ui.theme.VocisMediumGrey

sealed class VocisTabItem(val index: Int, val label: String, val icon: ImageVector) {
    object Dashboard : VocisTabItem(0, "Shield", Icons.Rounded.Home)
    object Calls : VocisTabItem(1, "Calls", Icons.Rounded.Call)
    object Voices : VocisTabItem(2, "Voices", Icons.Rounded.Person)
    object Tools : VocisTabItem(3, "Tools", Icons.Rounded.Build)
    object Settings : VocisTabItem(4, "Settings", Icons.Rounded.Settings)
}

/**
 * Main application shell orchestrating the Figma 12-screen workflow:
 * - Tab 0: Shield Dashboard (Figma Screen 04)
 * - Tab 1: Calls Screening History (Figma Screen 05)
 * - Tab 2: Voice Biometric Profiles (Figma Screen 06)
 * - Tab 3: Safety Tools Console (Figma Screens 07 & 13)
 * - Tab 4: Settings (Figma Settings)
 * - Deep Dive: Forensic Report Screen (Figma Screen 12 Telemetry)
 * - Deep Dive: Digital Arrest 10-Phase Drill (Figma Screen 11)
 * - Deep Dive: Sealed Evidence Vault (Figma Screen 12)
 * - Modal: Onboarding & Permissions Tour (Figma Screens 02 & 03)
 */
@Composable
fun BiometricShell(
    interactionHub: InteractionHub? = null,
    digitalArrestController: DigitalArrestController? = null,
    onNavigateToDigitalArrest: () -> Unit = {},
    onNavigateToEmergency: () -> Unit = {},
    onSelectCallDetail: (InteractionEntity) -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedInteraction by remember { mutableStateOf<InteractionEntity?>(null) }
    var showDigitalArrest by remember { mutableStateOf(false) }
    var showEvidenceVault by remember { mutableStateOf(false) }
    var showOnboardingTour by remember { mutableStateOf(false) }

    // 1. Onboarding Tour Modal
    if (showOnboardingTour) {
        BackHandler { showOnboardingTour = false }
        OnboardingScreen(
            onCompleteOnboarding = { showOnboardingTour = false },
            onRequestPermissions = {}
        )
        return
    }

    // 2. Forensic Evidence Vault Screen (Screen 12)
    if (showEvidenceVault) {
        BackHandler { showEvidenceVault = false }
        ForensicEvidenceScreen(
            onBack = { showEvidenceVault = false }
        )
        return
    }

    // 3. Digital Arrest 10-Phase Defense Screen (Screen 11)
    if (showDigitalArrest) {
        BackHandler { showDigitalArrest = false }
        DigitalArrestScreen(
            controller = digitalArrestController,
            onBack = { showDigitalArrest = false },
            onNavigateToEvidenceVault = {
                showDigitalArrest = false
                showEvidenceVault = true
            }
        )
        return
    }

    // 4. Full Forensic Report Screen (when any call/activity row is tapped)
    if (selectedInteraction != null) {
        BackHandler { selectedInteraction = null }
        ReportScreen(
            interaction = selectedInteraction!!,
            onBack = { selectedInteraction = null },
            onNavigateToDigitalArrest = {
                selectedInteraction = null
                showDigitalArrest = true
            },
            onNavigateToEvidenceVault = {
                selectedInteraction = null
                showEvidenceVault = true
            }
        )
        return
    }

    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    // 5. Main 5-Tab Shell with Bottom Navigation
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = VocisCream,
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .border(1.dp, VocisBorder, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                color = VocisCardWhite,
                shadowElevation = 12.dp
            ) {
                NavigationBar(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = VocisCardWhite,
                    tonalElevation = 0.dp
                ) {
                    val tabs = listOf(
                        VocisTabItem.Dashboard,
                        VocisTabItem.Calls,
                        VocisTabItem.Voices,
                        VocisTabItem.Tools,
                        VocisTabItem.Settings
                    )

                    tabs.forEach { tab ->
                        val isSelected = selectedTab == tab.index
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { selectedTab = tab.index },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label,
                                    modifier = Modifier.size(24.dp),
                                    tint = if (isSelected) VocisDark else VocisMediumGrey
                                )
                            },
                            label = {
                                Text(
                                    text = tab.label,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) VocisDark else VocisMediumGrey
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = VocisGreenLight,
                                selectedIconColor = VocisDark,
                                unselectedIconColor = VocisMediumGrey
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> DashboardScreen(
                    interactionHub = interactionHub,
                    onNavigateToDigitalArrest = { showDigitalArrest = true },
                    onNavigateToEmergency = onNavigateToEmergency,
                    onSelectInteraction = { interaction ->
                        selectedInteraction = interaction
                        onSelectCallDetail(interaction)
                    },
                    onViewAllCalls = { selectedTab = 1 },
                    onProfileClick = { selectedTab = 4 }
                )
                1 -> CallsScreen(
                    interactionHub = interactionHub,
                    onSelectCall = { interaction ->
                        selectedInteraction = interaction
                        onSelectCallDetail(interaction)
                    }
                )
                2 -> VoicesScreen(
                    onEnrollNewProfile = {}
                )
                3 -> SafetyToolsScreen(
                    onNavigateToDigitalArrest = { showDigitalArrest = true },
                    onNavigateToEmergency = onNavigateToEmergency,
                    onNavigateToEvidenceVault = { showEvidenceVault = true }
                )
                4 -> SettingsScreen(
                    onRevisitOnboarding = { showOnboardingTour = true }
                )
            }
        }
    }
}
