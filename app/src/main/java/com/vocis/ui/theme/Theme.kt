package com.vocis.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = VocisGreen,
    onPrimary = Color.White,
    primaryContainer = VocisGreenLight,
    onPrimaryContainer = VocisGreenText,
    secondary = VocisDark,
    onSecondary = Color.White,
    secondaryContainer = VocisCreamDarker,
    onSecondaryContainer = VocisDark,
    tertiary = VocisAmber,
    onTertiary = Color.White,
    background = VocisCream,
    onBackground = VocisDark,
    surface = VocisCardWhite,
    onSurface = VocisDark,
    surfaceVariant = VocisCreamDarker,
    onSurfaceVariant = VocisDarkGrey,
    outline = VocisBorder,
    outlineVariant = VocisBorderLight,
    error = VocisRed,
    onError = Color.White,
    errorContainer = VocisRedLight,
    onErrorContainer = VocisRedDark
)

@Composable
fun VocisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // The Figma specifications mandate the signature cream-and-forest-green warm identity
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = VocisTypography,
        content = content
    )
}
