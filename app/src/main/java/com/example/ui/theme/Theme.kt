package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = SamsungBlue,
    onPrimary = Color.White,
    secondary = LightBlue,
    onSecondary = Color.White,
    background = PureBlack,
    onBackground = LightText,
    surface = DarkGray,
    onSurface = LightText,
    surfaceVariant = SoftGray,
    onSurfaceVariant = LightText,
    outline = BorderGray
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
