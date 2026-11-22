package com.voicerpg.engine.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = LogosGold,
    secondary = FrostCyan,
    tertiary = FireOrange,
    background = RetroBlack,
    surface = RetroPanel,
    onPrimary = RetroBlack,
    onSecondary = RetroBlack,
    onBackground = LogosGlow,
    onSurface = LogosGlow
)

@Composable
fun VoiceRPGTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = RetroTypography,
        content = content
    )
}
