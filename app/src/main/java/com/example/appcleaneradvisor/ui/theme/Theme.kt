package com.example.appcleaneradvisor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF173B36),
    onPrimary = Color.White,
    secondary = Color(0xFFE85D75),
    tertiary = Color(0xFFF6C85F),
    background = Color(0xFFF7F7F2),
    surface = Color(0xFFFFFCF5),
    surfaceVariant = Color(0xFFE5E1D5),
    onSurface = Color(0xFF1C1D1A),
    onSurfaceVariant = Color(0xFF5E625C)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF6C85F),
    onPrimary = Color(0xFF1B1B18),
    secondary = Color(0xFFFFB1C0),
    tertiary = Color(0xFF9BD7CA),
    background = Color(0xFF131411),
    surface = Color(0xFF1D1E1B),
    surfaceVariant = Color(0xFF343630),
    onSurface = Color(0xFFF2F0E8),
    onSurfaceVariant = Color(0xFFC9C7BE)
)

@Composable
fun AppCleanerTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
