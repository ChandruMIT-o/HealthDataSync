// src\main\java\com\samsung\health\hrdatatransfer\presentation\theme\Theme.kt
package com.samsung.health.hrdatatransfer.presentation.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

// Wear OS Material 3 typically assumes a black background by default.
// We define the key accent colors here.
val AppColorScheme = ColorScheme(
    primary = Color(0xFF96be25),       // Samsung Green
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF2E3B1F),
    onPrimaryContainer = Color(0xFF96be25),

    secondary = Color(0xFF03DAC5),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF004F48),
    onSecondaryContainer = Color(0xFF03DAC5),

    tertiary = Color(0xFFCF6679),      // Used for Error/Stop in some contexts
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF4F252D),
    onTertiaryContainer = Color(0xFFCF6679),

    background = Color.Black,
    onBackground = Color.White,

    // "Surface" is implied as the background in Wear, but we define containers
    surfaceContainer = Color(0xFF202124),
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFDADCE0),

    error = Color(0xFFCF6679),
    onError = Color.Black,

    outline = Color(0xFF5F6368),
    outlineVariant = Color(0xFF444746)
)

@Composable
fun HealthDataTransferTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        content = content
    )
}