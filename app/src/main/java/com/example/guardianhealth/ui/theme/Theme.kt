package com.example.guardianhealth.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = TextOnPrimary,
    primaryContainer = GreenContainer,
    onPrimaryContainer = GreenPrimary,
    secondary = GreenLight,
    onSecondary = TextOnPrimary,
    secondaryContainer = GreenSoft,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceDim,
    onSurfaceVariant = TextSecondary,
    outline = BorderLight,
    outlineVariant = CardBorder,
    error = AlertRed,
    onError = TextOnPrimary
)

private val DarkColorScheme = darkColorScheme(
    primary = GreenLight,
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005048),
    secondary = GreenSoft,
    background = Color(0xFF0F1419),
    onBackground = Color(0xFFE5E7EB),
    surface = Color(0xFF1A1F2E),
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF2A2F3E),
    onSurfaceVariant = Color(0xFF9CA3AF),
    outline = Color(0xFF374151),
    outlineVariant = Color(0xFF374151),
    error = AlertRed
)

@Composable
fun GuardianHealthTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
