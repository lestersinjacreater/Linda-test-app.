package com.linda.app.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Colour tokens, named exactly as in docs/design-system.md. Components use these (or LindaTheme.colors),
// never raw hex (rule: "Name colour tokens exactly as in this file").

// 3.1 Safaricom greens
val green500 = Color(Palette.GREEN_500)
val green700 = Color(Palette.GREEN_700)
val green900 = Color(Palette.GREEN_900)
val green300 = Color(Palette.GREEN_300)
val green50 = Color(Palette.GREEN_50)

// 3.3 Risk colours
val scamRed = Color(Palette.SCAM_RED)
val scamRedDark = Color(Palette.SCAM_RED_DARK)
val cautionAmber = Color(Palette.CAUTION_LIGHT)
val cautionAmberDark = Color(Palette.CAUTION_DARK)

// 3.4 Layer tints
val layerL = Color(Palette.LAYER_L)
val layerI = Color(Palette.LAYER_I)
val layerN = Color(Palette.LAYER_N)
val layerD = Color(Palette.LAYER_D)
val layerA = Color(Palette.LAYER_A)

/**
 * How one risk level looks. [accent] colours icons, borders and segments; [text] is the shade for words
 * (checked against [tint] for 4.5:1); [tint] is the card background; [onAccent] is the letter or mark drawn
 * on top of an [accent] fill (white on the light shades, dark on the bright dark-mode shades).
 */
@Immutable
class RiskStyle(val accent: Color, val text: Color, val tint: Color, val onAccent: Color)

/** Every colour a screen may use, for the current light or dark mode. Read it with `LindaTheme.colors`. */
@Immutable
class LindaColorTokens(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color,
    val brand: Color,       // large fills and the logo: green500
    val primary: Color,     // buttons, links, active tabs: green700 (light) / green300 (dark)
    val onPrimary: Color,
    val safe: RiskStyle,
    val caution: RiskStyle,
    val scam: RiskStyle,
    val layers: List<Color>, // L, I, N, D, A tints in order
) {
    /** Tint for layer [index] (0 = L ... 4 = A). */
    fun layer(index: Int): Color = layers[index]

    /** The look for a risk level given as the RiskLevel name ("SAFE", "CAUTION" or "SCAM"). */
    fun risk(level: String): RiskStyle = when (level) {
        "SCAM" -> scam
        "CAUTION" -> caution
        else -> safe
    }
}

val LightTokens = LindaColorTokens(
    isDark = false,
    background = Color(Palette.BACKGROUND_LIGHT),
    surface = Color(Palette.SURFACE_LIGHT),
    surfaceRaised = Color(Palette.SURFACE_RAISED_LIGHT),
    textPrimary = Color(Palette.TEXT_PRIMARY_LIGHT),
    textSecondary = Color(Palette.TEXT_SECONDARY_LIGHT),
    border = Color(Palette.BORDER_LIGHT),
    brand = green500,
    primary = green700,
    onPrimary = Color(Palette.WHITE),
    // Safe: the icon is green500, but words use green700 because green500 on mint is only 2.9:1.
    safe = RiskStyle(green500, green700, green50, Color(Palette.WHITE)),
    caution = RiskStyle(cautionAmber, cautionAmber, Color(Palette.CAUTION_TINT_LIGHT), Color(Palette.WHITE)),
    scam = RiskStyle(scamRed, Color(Palette.SCAM_RED_TEXT_LIGHT), Color(Palette.SCAM_TINT_LIGHT), Color(Palette.WHITE)),
    layers = listOf(layerL, layerI, layerN, layerD, layerA),
)

val DarkTokens = LindaColorTokens(
    isDark = true,
    background = Color(Palette.BACKGROUND_DARK),
    surface = Color(Palette.SURFACE_DARK),
    surfaceRaised = Color(Palette.SURFACE_RAISED_DARK),
    textPrimary = Color(Palette.TEXT_PRIMARY_DARK),
    textSecondary = Color(Palette.TEXT_SECONDARY_DARK),
    border = Color(Palette.BORDER_DARK),
    brand = green500,
    primary = green300,
    onPrimary = Color(Palette.BACKGROUND_DARK),
    safe = RiskStyle(green300, green300, Color(Palette.SAFE_TINT_DARK), Color(Palette.BACKGROUND_DARK)),
    caution = RiskStyle(cautionAmberDark, cautionAmberDark, Color(Palette.CAUTION_TINT_DARK), Color(Palette.BACKGROUND_DARK)),
    scam = RiskStyle(scamRedDark, scamRedDark, Color(Palette.SCAM_TINT_DARK), Color(Palette.BACKGROUND_DARK)),
    layers = listOf(layerL, layerI, layerN, layerD, layerA),
)
