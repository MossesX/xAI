package com.brett.xai.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class XColors(val code: Color, val codeText: Color, val codeHeader: Color, val accent2: Color, val muted: Color, val userBubble: Color)

val LocalX = staticCompositionLocalOf {
    XColors(Color(0xFF07090B), Color(0xFFD7E0E8), Color(0xFF14181D), Color(0xFFFBBF24), Color(0xFF8A95A3), Color(0xFF1B2530))
}

private val Dark = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF00201C),
    primaryContainer = Color(0xFF123B36),
    onPrimaryContainer = Color(0xFFB9F5EA),
    secondary = Color(0xFFFBBF24),
    onSecondary = Color(0xFF2A1D00),
    background = Color(0xFF0D0F12),
    onBackground = Color(0xFFE6EAEF),
    surface = Color(0xFF0D0F12),
    onSurface = Color(0xFFE6EAEF),
    surfaceVariant = Color(0xFF1A1F26),
    onSurfaceVariant = Color(0xFFA9B4C1),
    surfaceContainer = Color(0xFF14181D),
    surfaceContainerHigh = Color(0xFF1A1F26),
    surfaceContainerHighest = Color(0xFF222832),
    surfaceContainerLow = Color(0xFF111418),
    outline = Color(0xFF3A4350),
    outlineVariant = Color(0xFF262D36),
    error = Color(0xFFFF8A80),
)

private val Light = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCF3EC),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFFB45309),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF14181D),
    surface = Color(0xFFF7F8FA),
    onSurface = Color(0xFF14181D),
    surfaceVariant = Color(0xFFE8ECF0),
    onSurfaceVariant = Color(0xFF4A5563),
    surfaceContainer = Color(0xFFEEF1F4),
    surfaceContainerHigh = Color(0xFFE8ECF0),
    surfaceContainerHighest = Color(0xFFE1E6EB),
    surfaceContainerLow = Color(0xFFF2F4F6),
    outline = Color(0xFFB8C1CC),
    outlineVariant = Color(0xFFD9DFE5),
)

@Composable
fun XaiTheme(theme: String, content: @Composable () -> Unit) {
    val dark = when (theme) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
    val x = if (dark) XColors(Color(0xFF07090B), Color(0xFFD7E0E8), Color(0xFF14181D), Color(0xFFFBBF24), Color(0xFF8A95A3), Color(0xFF1B2530))
    else XColors(Color(0xFF14181D), Color(0xFFE6EAEF), Color(0xFF222832), Color(0xFFB45309), Color(0xFF6B7684), Color(0xFFDDEFEC))
    androidx.compose.runtime.CompositionLocalProvider(LocalX provides x) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
    }
}
