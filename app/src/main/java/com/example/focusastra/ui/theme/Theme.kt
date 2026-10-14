package com.example.focusastra.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF23695B), onPrimary = Color.White,
    primaryContainer = Color(0xFFC9EDDF), onPrimaryContainer = Color(0xFF082F27),
    secondary = Color(0xFF53665D), secondaryContainer = Color(0xFFDDECE2),
    background = Color(0xFFF7F8F3), surface = Color(0xFFF7F8F3),
    surfaceContainer = Color(0xFFEDF1E9), surfaceVariant = Color(0xFFE1E8DF),
    onBackground = Color(0xFF202721), onSurface = Color(0xFF202721),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF97D4BB), onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF185040), secondaryContainer = Color(0xFF2B4036),
    background = Color(0xFF101813), surface = Color(0xFF101813),
    surfaceContainer = Color(0xFF1D2922), surfaceVariant = Color(0xFF35453A),
)

@Composable
fun FocusAstraTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, typography = Typography, content = content)
}
