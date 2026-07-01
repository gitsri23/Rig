package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = CyberTeal,
    secondary = CyberViolet,
    tertiary = CyberCoral,
    background = CyberBackground,
    surface = CyberSurface,
    surfaceVariant = CyberSurfaceVariant,
    onBackground = CyberTextPrimary,
    onSurface = CyberTextPrimary,
    onSurfaceVariant = CyberTextSecondary
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
