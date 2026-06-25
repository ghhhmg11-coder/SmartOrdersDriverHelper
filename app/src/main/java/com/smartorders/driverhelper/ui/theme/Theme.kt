package com.smartorders.driverhelper.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val GreenPrimary = Color(0xFF1B8A5A)
val GreenDark = Color(0xFF145F3E)
val GreenLight = Color(0xFF4CAF50)
val RedAlert = Color(0xFFD32F2F)
val GoldAccent = Color(0xFFFFC107)
val BackgroundDark = Color(0xFF121212)
val SurfaceDark = Color(0xFF1E1E1E)
val CardDark = Color(0xFF2A2A2A)
val OnSurface = Color(0xFFE0E0E0)

private val DarkColorScheme = darkColorScheme(
    primary = GreenPrimary,
    onPrimary = Color.White,
    primaryContainer = GreenDark,
    secondary = GoldAccent,
    onSecondary = Color.Black,
    background = BackgroundDark,
    onBackground = OnSurface,
    surface = SurfaceDark,
    onSurface = OnSurface,
    surfaceVariant = CardDark,
    error = RedAlert
)

@Composable
fun SmartOrdersTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content
    )
}
