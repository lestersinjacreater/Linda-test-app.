package com.linda.app.core.ui.theme

import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks the accessibility rule in docs/design-system.md section 11: text contrast at least 4.5:1
 * (3:1 only for 18sp bold and larger), for every text/background pair the app uses, in light and dark.
 */
class PaletteContrastTest {

    /** WCAG contrast ratio between two 0xAARRGGBB colours. */
    private fun contrast(a: Long, b: Long): Double {
        val x = luminance(a)
        val y = luminance(b)
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }

    private fun luminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun assertText(name: String, text: Long, background: Long, minimum: Double = 4.5) {
        val ratio = contrast(text, background)
        assertTrue("$name is only ${"%.2f".format(ratio)}:1 (needs $minimum:1)", ratio >= minimum)
    }

    @Test
    fun bodyAndHelperText_passInBothModes() = with(Palette) {
        for ((text, backgrounds) in listOf(
            TEXT_PRIMARY_LIGHT to listOf(BACKGROUND_LIGHT, SURFACE_LIGHT, SURFACE_RAISED_LIGHT),
            TEXT_SECONDARY_LIGHT to listOf(BACKGROUND_LIGHT, SURFACE_LIGHT, SURFACE_RAISED_LIGHT),
            TEXT_PRIMARY_DARK to listOf(BACKGROUND_DARK, SURFACE_DARK, SURFACE_RAISED_DARK),
            TEXT_SECONDARY_DARK to listOf(BACKGROUND_DARK, SURFACE_DARK, SURFACE_RAISED_DARK),
        )) {
            for (bg in backgrounds) assertText("text ${text.toString(16)} on ${bg.toString(16)}", text, bg)
        }
    }

    @Test
    fun primaryButtonsAndLinks_pass() = with(Palette) {
        assertText("white on green700 (primary button)", WHITE, GREEN_700)
        assertText("green700 link on light background", GREEN_700, BACKGROUND_LIGHT)
        assertText("green700 link on light surface", GREEN_700, SURFACE_LIGHT)
        assertText("dark text on green300 (dark primary button)", BACKGROUND_DARK, GREEN_300)
        assertText("green300 link on dark background", GREEN_300, BACKGROUND_DARK)
        assertText("green300 link on dark surface", GREEN_300, SURFACE_DARK)
    }

    @Test
    fun riskLabels_passOnTheirTint() = with(Palette) {
        assertText("light Safe text (green700) on mint", GREEN_700, GREEN_50)
        assertText("light Caution on its tint", CAUTION_LIGHT, CAUTION_TINT_LIGHT)
        assertText("light Scam text on its tint", SCAM_RED_TEXT_LIGHT, SCAM_TINT_LIGHT)
        assertText("dark Safe on its tint", SAFE_DARK, SAFE_TINT_DARK)
        assertText("dark Caution on its tint", CAUTION_DARK, CAUTION_TINT_DARK)
        assertText("dark Scam on its tint", SCAM_RED_DARK, SCAM_TINT_DARK)
    }

    @Test
    fun specLiteralColoursThatFail_areNotUsedForText() = with(Palette) {
        // These two pairs are written in the design file but miss 4.5:1, which is why the text shades above exist.
        assertTrue(contrast(GREEN_500, GREEN_50) < 4.5)
        assertTrue(contrast(SCAM_RED, SCAM_TINT_LIGHT) < 4.5)
    }

    @Test
    fun marksOnFlaggedAndLayerFills_pass() = with(Palette) {
        // Mark drawn on a risk-coloured fill (icons, flagged layer segments).
        assertText("white on light Caution fill", WHITE, CAUTION_LIGHT)
        assertText("white on light Scam fill", WHITE, SCAM_RED)
        assertText("dark on dark Caution fill", BACKGROUND_DARK, CAUTION_DARK)
        assertText("dark on dark Scam fill", BACKGROUND_DARK, SCAM_RED_DARK)
        assertText("dark on dark Safe fill", BACKGROUND_DARK, SAFE_DARK)
        // Layer letters are 20sp bold: dark on the three pale tints, white on the two deep ones (3:1 allowed for large bold text).
        assertText("letter L", TEXT_PRIMARY_LIGHT, LAYER_L)
        assertText("letter I", TEXT_PRIMARY_LIGHT, LAYER_I)
        assertText("letter N", TEXT_PRIMARY_LIGHT, LAYER_N)
        assertText("letter D (large bold)", WHITE, LAYER_D, minimum = 3.0)
        assertText("letter A", WHITE, LAYER_A)
    }

    @Test
    fun bodyText_passesOnEveryRiskTint() = with(Palette) {
        // The verdict card writes the message and reasons in the normal text colours on top of the risk tint.
        for (tint in listOf(GREEN_50, CAUTION_TINT_LIGHT, SCAM_TINT_LIGHT)) {
            assertText("primary text on light tint ${tint.toString(16)}", TEXT_PRIMARY_LIGHT, tint)
            assertText("secondary text on light tint ${tint.toString(16)}", TEXT_SECONDARY_LIGHT, tint)
        }
        for (tint in listOf(SAFE_TINT_DARK, CAUTION_TINT_DARK, SCAM_TINT_DARK)) {
            assertText("primary text on dark tint ${tint.toString(16)}", TEXT_PRIMARY_DARK, tint)
            assertText("secondary text on dark tint ${tint.toString(16)}", TEXT_SECONDARY_DARK, tint)
        }
    }
}
