package com.kaizen.archive.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val KaizenColors = lightColorScheme(
    primary = Color(0xFF7352A6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF2C174D),
    secondary = Color(0xFF655A73),
    background = Color(0xFFFAF8FC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF0EBF6),
    onSurface = Color(0xFF211B27),
    onSurfaceVariant = Color(0xFF655D6C),
    error = Color(0xFFB3261E),
)

@Composable
fun KaizenArchiveTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = KaizenColors, content = content)
}
