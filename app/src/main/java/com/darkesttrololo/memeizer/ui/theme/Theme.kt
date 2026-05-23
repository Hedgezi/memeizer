package com.darkesttrololo.memeizer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF7A3E00),
    secondary = Color(0xFF77573A),
    background = Color(0xFFFFF8F0),
    surface = Color(0xFFFFF8F0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB86B),
    secondary = Color(0xFFE7BE98),
    background = Color(0xFF1A120D),
    surface = Color(0xFF1A120D),
)

@Composable
fun MemeizerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
