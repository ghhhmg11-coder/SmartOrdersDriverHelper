package com.smartorders.driverhelper.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = JeenyPurple,
    onPrimary = Color.White,
    primaryContainer = JeenyPurpleDark,
    onPrimaryContainer = JeenyPurpleLight,
    secondary = JeenyPurpleLight,
    onSecondary = Color.Black,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkCard,
    onSurfaceVariant = TextSecondary,
    error = RedInactive,
    onError = Color.White
)

@Composable
fun SmartOrdersTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
