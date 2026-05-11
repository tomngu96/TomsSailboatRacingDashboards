package com.sailboatracing.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PrimaryColor = Color(0xFF00C8FF)
val BackgroundColor = Color(0xFF0A0A0F)
val SurfaceColor = Color(0xFF14141E)
val ErrorColor = Color(0xFFFF4444)
val OnPrimaryColor = Color(0xFF000000)
val OnBackgroundColor = Color(0xFFE0E0E0)
val OnSurfaceColor = Color(0xFFE0E0E0)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryColor,
    onPrimary = OnPrimaryColor,
    background = BackgroundColor,
    onBackground = OnBackgroundColor,
    surface = SurfaceColor,
    onSurface = OnSurfaceColor,
    error = ErrorColor,
    onError = Color.White,
    secondary = Color(0xFF00C8FF),
    onSecondary = Color.Black,
    tertiary = Color(0xFFFFAB40),
    onTertiary = Color.Black,
    surfaceVariant = Color(0xFF1E1E2E),
    onSurfaceVariant = Color(0xFFBBBBCC)
)

@Composable
fun SailboatRacingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
