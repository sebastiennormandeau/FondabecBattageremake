package com.fondabec.battage.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FondabecGreen = Color(0xFFB6D400)
private val FondabecBlack = Color(0xFF000000)
private val FondabecWhite = Color(0xFFFFFFFF)

/**
 * Objectif:
 * - Tous les Button() utilisent primary / onPrimary
 * - Clair: primary = vert Fondabec
 * - Sombre: primary = blanc
 */
private val LightColors = lightColorScheme(
    primary = FondabecGreen,
    onPrimary = FondabecBlack,
    primaryContainer = FondabecGreen,
    onPrimaryContainer = FondabecBlack
)

private val DarkColors = darkColorScheme(
    primary = FondabecWhite,
    onPrimary = FondabecBlack,
    primaryContainer = FondabecWhite,
    onPrimaryContainer = FondabecBlack
)

@Composable
fun FondabecBattageTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}
