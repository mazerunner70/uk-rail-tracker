package com.ukrailtracker.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NeonColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = NeonBackground,
    secondary = NeonMagenta,
    onSecondary = NeonOnBackground,
    background = NeonBackground,
    onBackground = NeonOnBackground,
    surface = NeonSurface,
    onSurface = NeonOnBackground,
    outline = NeonOutline,
)

@Composable
fun UkRailTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NeonColorScheme,
        typography = Typography,
        content = content,
    )
}
