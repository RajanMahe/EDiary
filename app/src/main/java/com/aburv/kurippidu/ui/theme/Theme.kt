package com.aburv.kurippidu.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = InkBrown,
    onPrimary = Color.White,
    // Top bars
    primaryContainer = InkBrown,
    onPrimaryContainer = Color.White,
    secondary = InkBrown.copy(alpha = 0.85f),
    onSecondary = Color.White,
    background = PaperBeige,
    onBackground = InkBrown,
    surface = PaperBeige,
    onSurface = InkBrown,
    surfaceVariant = DeskBeige,
    onSurfaceVariant = InkBrown.copy(alpha = 0.7f)
)

private val DarkColorScheme = darkColorScheme(
    // Warm amber accent of its own, instead of reusing the light brown
    primary = AmberInk,
    onPrimary = Color(0xFF3B2414),
    primaryContainer = Color(0xFF2E241D),
    onPrimaryContainer = NightText,
    secondary = AmberInk.copy(alpha = 0.85f),
    onSecondary = Color(0xFF3B2414),
    background = NightPaper,
    onBackground = NightText,
    surface = NightPaper,
    onSurface = NightText,
    surfaceVariant = NightDesk,
    onSurfaceVariant = NightText.copy(alpha = 0.7f)
)

/** True when the app's own theme (not the phone's) is dark. */
val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
fun DiaryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // System bar colours are handled by enableEdgeToEdge() in MainActivity
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography,
            content = content
        )
    }
}
