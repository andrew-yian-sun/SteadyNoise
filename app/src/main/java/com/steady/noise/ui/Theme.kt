package com.steady.noise.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NightColors = darkColorScheme(
    primary = Color(0xFF8ECAE6),
    onPrimary = Color(0xFF003544),
    secondary = Color(0xFFB7C9D3),
    background = Color(0xFF101418),
    surface = Color(0xFF101418),
    onBackground = Color(0xFFE1E2E8),
    onSurface = Color(0xFFE1E2E8),
)

@Composable
fun SteadyNoiseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NightColors,
        content = content,
    )
}
