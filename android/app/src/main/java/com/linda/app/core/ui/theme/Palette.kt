package com.linda.app.core.ui.theme

/**
 * Every colour in Linda as a plain number (0xAARRGGBB), taken from docs/design-system.md section 3.
 *
 * Kept free of Compose on purpose so a plain unit test can check the contrast rules (section 11).
 * Screens never use these directly: they read the named tokens in Color.kt through the theme.
 */
object Palette {
    // 3.1 Safaricom greens
    const val GREEN_500 = 0xFF00A651L
    const val GREEN_700 = 0xFF007A3DL
    const val GREEN_900 = 0xFF00602FL
    const val GREEN_300 = 0xFF2DBE6CL
    const val GREEN_50 = 0xFFE8F6EEL

    // 3.2 Neutrals (light, dark)
    const val BACKGROUND_LIGHT = 0xFFF6F8F7L
    const val BACKGROUND_DARK = 0xFF08130DL
    const val SURFACE_LIGHT = 0xFFFFFFFFL
    const val SURFACE_DARK = 0xFF11211AL
    const val SURFACE_RAISED_LIGHT = 0xFFEEF2F0L
    const val SURFACE_RAISED_DARK = 0xFF1A2E24L
    const val TEXT_PRIMARY_LIGHT = 0xFF0F1A14L
    const val TEXT_PRIMARY_DARK = 0xFFF2F7F4L
    const val TEXT_SECONDARY_LIGHT = 0xFF5B6B62L
    const val TEXT_SECONDARY_DARK = 0xFF9DB0A5L
    const val BORDER_LIGHT = 0xFFDCE4DFL
    const val BORDER_DARK = 0xFF2A4035L

    // 3.3 Risk colours: the colour of the icon and the border, and the tint behind the card.
    const val SAFE_DARK = 0xFF2DBE6CL
    const val SAFE_TINT_DARK = 0xFF0F2A1CL
    const val CAUTION_LIGHT = 0xFF8A5A00L
    const val CAUTION_TINT_LIGHT = 0xFFFFF4D6L
    const val CAUTION_DARK = 0xFFFFC940L
    const val CAUTION_TINT_DARK = 0xFF2E2610L
    const val SCAM_RED = 0xFFE4002BL
    const val SCAM_TINT_LIGHT = 0xFFFDE8ECL
    const val SCAM_RED_DARK = 0xFFFF5C77L
    const val SCAM_TINT_DARK = 0xFF33121AL

    /**
     * Text shade for the light Scam tint. #E4002B on #FDE8EC measures only 4.1:1, under the 4.5:1 rule
     * (section 11), so label text uses this slightly deeper red. The icon and border keep SCAM_RED.
     */
    const val SCAM_RED_TEXT_LIGHT = 0xFFD0002AL

    // 3.4 Layer tints (L, I, N, D, A)
    const val LAYER_L = 0xFFB7E4C7L
    const val LAYER_I = 0xFF74C69DL
    const val LAYER_N = 0xFF2DBE6CL
    const val LAYER_D = 0xFF00A651L
    const val LAYER_A = 0xFF007A3DL

    const val WHITE = 0xFFFFFFFFL
}
