package com.eepiemi.materialbook.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Facebook's own brand blue — used as the native chrome color when
// Material You dynamic theming is off (the "original" Facebook look).
// internal (not private): ThemeTest asserts against this directly.
internal val FacebookBlue = Color(0xFF1877F2)

// The rest of each scheme is Facebook's own greys (its light and dark web palettes), so
// nothing falls back to Material's default purple: containers, switch thumbs, the
// Settings button and dialog surfaces all stay on brand.
internal val DarkColorScheme = darkColorScheme(
    primary = FacebookBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF263951),
    onPrimaryContainer = Color(0xFFDCE8FF),
    inversePrimary = FacebookBlue,
    secondary = Color.DarkGray,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF3A3B3C),
    onSecondaryContainer = Color(0xFFE4E6EB),
    tertiary = FacebookBlue,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF263951),
    onTertiaryContainer = Color(0xFFDCE8FF),
    background = Color(0xFF18191A),
    onBackground = Color.White,
    surface = Color(0xFF18191A),
    onSurface = Color(0xFFE4E6EB),
    surfaceVariant = Color(0xFF242526),
    onSurfaceVariant = Color(0xFFB0B3B8),
    surfaceTint = FacebookBlue,
    surfaceBright = Color(0xFF3A3B3C),
    surfaceDim = Color(0xFF18191A),
    surfaceContainerLowest = Color(0xFF0F1011),
    surfaceContainerLow = Color(0xFF1C1D1E),
    surfaceContainer = Color(0xFF242526),
    surfaceContainerHigh = Color(0xFF2D2E30),
    surfaceContainerHighest = Color(0xFF3A3B3C),
    outline = Color(0xFF8A8D91),
    outlineVariant = Color(0xFF3E4042)
)
internal val LightColorScheme = lightColorScheme(
    primary = FacebookBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7F0FE),
    onPrimaryContainer = Color(0xFF0A4FA8),
    inversePrimary = Color(0xFF8AB4F8),
    secondary = Color.LightGray,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFFE4E6EB),
    onSecondaryContainer = Color(0xFF050505),
    tertiary = FacebookBlue,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE7F0FE),
    onTertiaryContainer = Color(0xFF0A4FA8),
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color(0xFF050505),
    surfaceVariant = Color(0xFFF0F2F5),
    onSurfaceVariant = Color(0xFF65676B),
    surfaceTint = FacebookBlue,
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE4E6EB),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F8FA),
    surfaceContainer = Color(0xFFF0F2F5),
    surfaceContainerHigh = Color(0xFFE9EBEE),
    surfaceContainerHighest = Color(0xFFE4E6EB),
    outline = Color(0xFF8A8D91),
    outlineVariant = Color(0xFFCED0D4)
)

@Composable
fun MaterialbookTheme(
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}