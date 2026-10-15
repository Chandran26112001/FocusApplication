package com.example.focusastra.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

data class FocusColors(
    val background: Color, val panel: Color, val panelRaised: Color, val line: Color,
    val crimson: Color, val text: Color, val muted: Color, val subdued: Color,
)

private val dark = FocusColors(
    Color(0xFF0B0B0C), Color(0xFF1C1C1F), Color(0xFF29292C), Color(0xFF303034),
    Color(0xFFFF2857), Color(0xFFF5F5F7), Color(0xFF9AA8B8), Color(0xFF777E89),
)
private val light = FocusColors(
    Color(0xFFF6F6F7), Color(0xFFFFFFFF), Color(0xFFE9E9EC), Color(0xFFDADAE0),
    Color(0xFFE91F4D), Color(0xFF111216), Color(0xFF59606B), Color(0xFF6B727D),
)

fun focusColors(context: Context): FocusColors =
    if (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) dark else light

private fun scheme(colors: FocusColors, darkMode: Boolean) = if (darkMode) darkColorScheme(
    primary = colors.crimson, onPrimary = colors.background, primaryContainer = colors.panelRaised,
    onPrimaryContainer = colors.text, secondary = colors.muted, onSecondary = colors.background,
    tertiary = colors.subdued, background = colors.background, onBackground = colors.text,
    surface = colors.background, onSurface = colors.text, surfaceContainer = colors.panel,
    surfaceContainerHigh = colors.panelRaised, surfaceVariant = colors.panel,
    onSurfaceVariant = colors.muted, outline = colors.line, error = colors.crimson,
) else lightColorScheme(
    primary = colors.crimson, onPrimary = colors.background, primaryContainer = colors.panelRaised,
    onPrimaryContainer = colors.text, secondary = colors.muted, onSecondary = colors.background,
    tertiary = colors.subdued, background = colors.background, onBackground = colors.text,
    surface = colors.background, onSurface = colors.text, surfaceContainer = colors.panel,
    surfaceContainerHigh = colors.panelRaised, surfaceVariant = colors.panel,
    onSurfaceVariant = colors.muted, outline = colors.line, error = colors.crimson,
)

private val darkScheme = scheme(dark, true)
private val lightScheme = scheme(light, false)

object FocusPalette {
    val background: Color @Composable get() = MaterialTheme.colorScheme.background
    val panel: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainer
    val panelRaised: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
    val line: Color @Composable get() = MaterialTheme.colorScheme.outline
    val crimson: Color @Composable get() = MaterialTheme.colorScheme.primary
    val text: Color @Composable get() = MaterialTheme.colorScheme.onBackground
    val muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val subdued: Color @Composable get() = MaterialTheme.colorScheme.tertiary
}

@Composable
fun FocusAstraTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkScheme else lightScheme
    MaterialTheme(colorScheme = colors, typography = Typography) {
        CompositionLocalProvider(LocalContentColor provides colors.onBackground, content = content)
    }
}
