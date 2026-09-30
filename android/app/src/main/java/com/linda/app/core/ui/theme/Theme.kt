package com.linda.app.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Linda is dark only, so there is no light colour scheme.
private val LindaDarkColors = darkColorScheme(
    primary = LindaColors.Cyan,
    onPrimary = LindaColors.Background,
    secondary = LindaColors.Amber,
    onSecondary = LindaColors.Background,
    error = LindaColors.Pink,
    onError = LindaColors.Background,
    background = LindaColors.Background,
    onBackground = LindaColors.TextPrimary,
    surface = LindaColors.Card,
    onSurface = LindaColors.TextPrimary,
    surfaceVariant = LindaColors.Card,
    onSurfaceVariant = LindaColors.TextSecondary,
)

@Composable
fun LindaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LindaDarkColors,
        typography = LindaTypography,
        content = content,
    )
}
