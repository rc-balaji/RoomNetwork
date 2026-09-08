package com.laconfianza.roommapper.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RoomMapperDarkColors = darkColorScheme(
    primary = Color(0xFF9ED8FF),
    onPrimary = Color(0xFF00344F),
    primaryContainer = Color(0xFF004B6F),
    onPrimaryContainer = Color(0xFFC9E8FF),
    secondary = Color(0xFFB5CCDF),
    onSecondary = Color(0xFF20333F),
    secondaryContainer = Color(0xFF354A59),
    onSecondaryContainer = Color(0xFFD1E7F5),
    tertiary = Color(0xFFC8C2FF),
    background = Color(0xFF0C141B),
    onBackground = Color(0xFFE2EAF0),
    surface = Color(0xFF121D25),
    onSurface = Color(0xFFE2EAF0),
    surfaceVariant = Color(0xFF3E4850),
    onSurfaceVariant = Color(0xFFC1C9D0),
    outline = Color(0xFF89939B),
    error = Color(0xFFFFB4AB)
)

private val RoomMapperLightColors = lightColorScheme(
    primary = Color(0xFF00618D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC9E8FF),
    onPrimaryContainer = Color(0xFF001E2E),
    secondary = Color(0xFF4B626F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCEE6F4),
    onSecondaryContainer = Color(0xFF061E28),
    tertiary = Color(0xFF5A5790),
    background = Color(0xFFF7FAFC),
    onBackground = Color(0xFF171D21),
    surface = Color.White,
    onSurface = Color(0xFF171D21),
    surfaceVariant = Color(0xFFDCE5EA),
    onSurfaceVariant = Color(0xFF414A50),
    outline = Color(0xFF717A80),
    error = Color(0xFFBA1A1A)
)

@Composable
fun RoomMapperTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) RoomMapperDarkColors else RoomMapperLightColors,
        typography = Typography(),
        content = content
    )
}
