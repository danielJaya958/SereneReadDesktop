package com.sereneread.project.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F6F52),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD2E8D4),
    onPrimaryContainer = Color(0xFF0D2010),
    background = Color(0xFFFAF7F0),
    surface = Color(0xFFFAF7F0),
    surfaceVariant = Color(0xFFEDE8DC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9CCAC),
    onPrimary = Color(0xFF173620),
    primaryContainer = Color(0xFF2E4D33),
    onPrimaryContainer = Color(0xFFD2E8D4),
    background = Color(0xFF1A1C19),
    surface = Color(0xFF1A1C19),
    surfaceVariant = Color(0xFF2B2E29),
)

@Composable
fun SereneReadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
