package com.linda.app.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalLindaColors = staticCompositionLocalOf { LightTokens }

/** `LindaTheme.colors.scam.accent`, `LindaTheme.colors.layer(2)`, ... the colours Material has no slot for. */
object LindaTheme {
    val colors: LindaColorTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalLindaColors.current
}

/** Maps our tokens onto Material's slots so stock components (text fields, dialogs, switches) match the design. */
private fun materialScheme(t: LindaColorTokens) = if (t.isDark) {
    darkColorScheme(
        primary = t.primary, onPrimary = t.onPrimary,
        primaryContainer = green900, onPrimaryContainer = green50,
        secondary = t.primary, onSecondary = t.onPrimary,
        error = t.scam.accent, onError = t.scam.onAccent,
        background = t.background, onBackground = t.textPrimary,
        surface = t.surface, onSurface = t.textPrimary,
        surfaceVariant = t.surfaceRaised, onSurfaceVariant = t.textSecondary,
        surfaceContainerLowest = t.background, surfaceContainerLow = t.surface, surfaceContainer = t.surface,
        surfaceContainerHigh = t.surfaceRaised, surfaceContainerHighest = t.surfaceRaised,
        outline = t.border, outlineVariant = t.border,
    )
} else {
    lightColorScheme(
        primary = t.primary, onPrimary = t.onPrimary,
        primaryContainer = green50, onPrimaryContainer = green900,
        secondary = t.primary, onSecondary = t.onPrimary,
        error = t.scam.text, onError = t.scam.onAccent,
        background = t.background, onBackground = t.textPrimary,
        surface = t.surface, onSurface = t.textPrimary,
        surfaceVariant = t.surfaceRaised, onSurfaceVariant = t.textSecondary,
        surfaceContainerLowest = t.surface, surfaceContainerLow = t.surface, surfaceContainer = t.surface,
        surfaceContainerHigh = t.surfaceRaised, surfaceContainerHighest = t.surfaceRaised,
        outline = t.border, outlineVariant = t.border,
    )
}

/** Light is the default look; dark follows the phone's system setting (docs/design-system.md section 3.2). */
@Composable
fun LindaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val tokens = if (darkTheme) DarkTokens else LightTokens
    CompositionLocalProvider(LocalLindaColors provides tokens) {
        MaterialTheme(
            colorScheme = materialScheme(tokens),
            typography = LindaTypography,
            shapes = LindaShapes,
            content = content,
        )
    }
}
