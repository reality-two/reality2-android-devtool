package com.reality2.devtool.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF9575CD),  // Lighter purple for dark mode
    secondary = Color(0xFF4DD0E1),  // Lighter teal
    tertiary = SecondaryVariant,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF2C2C2C),  // Lighter surface for cards
    onPrimary = Color(0xFF000000),  // Black text on lighter primary
    onSecondary = Color(0xFF000000),
    onBackground = Color(0xFFE8E8E8),  // Brighter text
    onSurface = Color(0xFFE8E8E8),  // Brighter text
    onSurfaceVariant = Color(0xFFB0B0B0),  // Lighter gray for secondary text
    error = Color(0xFFEF5350),  // Lighter red
    errorContainer = Color(0xFF5D1F1F),  // Dark red container
    onErrorContainer = Color(0xFFFFCDD2)  // Light red text
)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    secondary = Secondary,
    tertiary = SecondaryVariant,
    background = Background,
    surface = Surface,
    onPrimary = OnPrimary,
    onSecondary = OnSecondary,
    onBackground = OnBackground,
    onSurface = OnSurface,
    error = Error
)

@Composable
fun Reality2DevToolTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
