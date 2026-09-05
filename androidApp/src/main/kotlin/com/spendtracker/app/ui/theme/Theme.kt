package com.spendtracker.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF176B52),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA8F2D3),
    onPrimaryContainer = Color(0xFF002117),
    secondary = Color(0xFF4D635A),
    background = Color(0xFFF7FBF8),
    surface = Color(0xFFF7FBF8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8CD6B8),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF00513D),
    onPrimaryContainer = Color(0xFFA8F2D3),
    secondary = Color(0xFFB4CCC0),
)

@Composable
fun SpendTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
