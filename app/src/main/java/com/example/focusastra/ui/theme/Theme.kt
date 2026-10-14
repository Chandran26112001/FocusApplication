package com.example.focusastra.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

object FocusPalette {
    val background = Color(0xFF0B0B0C)
    val panel = Color(0xFF1C1C1F)
    val panelRaised = Color(0xFF29292C)
    val line = Color(0xFF303034)
    val crimson = Color(0xFFFF2857)
    val text = Color(0xFFF5F5F7)
    val muted = Color(0xFF9AA8B8)
    val subdued = Color(0xFF777E89)
}

private val FocusColors = darkColorScheme(
    primary = FocusPalette.crimson,
    onPrimary = FocusPalette.background,
    primaryContainer = FocusPalette.panelRaised,
    onPrimaryContainer = FocusPalette.text,
    secondary = FocusPalette.muted,
    onSecondary = FocusPalette.background,
    background = FocusPalette.background,
    onBackground = FocusPalette.text,
    surface = FocusPalette.background,
    onSurface = FocusPalette.text,
    surfaceContainer = FocusPalette.panel,
    surfaceContainerHigh = FocusPalette.panelRaised,
    surfaceVariant = FocusPalette.panel,
    onSurfaceVariant = FocusPalette.muted,
    outline = FocusPalette.line,
    error = FocusPalette.crimson,
)

@Composable
fun FocusAstraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = FocusColors, typography = Typography) {
        CompositionLocalProvider(LocalContentColor provides FocusPalette.text, content = content)
    }
}
