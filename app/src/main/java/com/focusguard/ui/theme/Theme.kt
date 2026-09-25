package com.focusguard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Verde-petróleo = foco/calma; âmbar = o alerta de tempo.
private val Light = lightColorScheme(
    primary = Color(0xFF0F4C47),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE8E3),
    onPrimaryContainer = Color(0xFF062A27),
    secondary = Color(0xFF4A635F),
    tertiary = Color(0xFFB36B00),
    tertiaryContainer = Color(0xFFFFE2B8),
    onTertiaryContainer = Color(0xFF3A2200),
    background = Color(0xFFF6F8F7),
    surface = Color(0xFFF6F8F7),
    surfaceContainer = Color(0xFFE9EFED),
    surfaceContainerHigh = Color(0xFFE2E9E7),
    surfaceContainerLowest = Color.White,
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7FD3C8),
    onPrimary = Color(0xFF00201D),
    primaryContainer = Color(0xFF16403B),
    onPrimaryContainer = Color(0xFFCDE8E3),
    secondary = Color(0xFFB1CCC7),
    tertiary = Color(0xFFFFB547),
    tertiaryContainer = Color(0xFF4F3300),
    onTertiaryContainer = Color(0xFFFFE2B8),
    background = Color(0xFF0E1514),
    surface = Color(0xFF0E1514),
    surfaceContainer = Color(0xFF18211F),
    surfaceContainerHigh = Color(0xFF1F2A28),
    surfaceContainerLowest = Color(0xFF141C1B),
)

@Composable
fun FocusGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content,
    )
}
