package com.example.ayniqbooking.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCEBFF),
    onPrimaryContainer = Color(0xFF0A2540),
    secondary = Color(0xFF14B8A6),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF64748B),
    background = Color(0xFFF5F7FB),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF0F4F8),
    onSurface = Color(0xFF111827),
    onSurfaceVariant = Color(0xFF5B667A)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF64A8FF),
    onPrimary = Color(0xFF061A33),
    secondary = Color(0xFF5EEAD4),
    onSecondary = Color(0xFF042F2E),
    tertiary = Color(0xFFCBD5E1),
    background = Color(0xFF0C111B),
    surface = Color(0xFF131A25),
    surfaceVariant = Color(0xFF1C2635),
    onSurface = Color(0xFFE5E7EB),
    onSurfaceVariant = Color(0xFFB6C2D1)
)

private val AppTypography = Typography()

@Composable
fun AyniqTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = AppTypography,
        content = content
    )
}
