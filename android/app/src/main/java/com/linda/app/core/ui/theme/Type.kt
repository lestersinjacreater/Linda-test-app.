package com.linda.app.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.linda.app.R

// Fonts are bundled in res/font (docs/design-system.md section 4): nothing is downloaded at run time.
val Poppins = FontFamily(
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
)

/**
 * The size table from section 4, mapped onto Material's names so stock components pick it up:
 * Display = headlineLarge, Title = titleLarge, Heading = titleMedium, Body Large = bodyLarge,
 * Body = bodyMedium, Label = labelMedium, Caption = bodySmall. labelLarge is the 16sp button text.
 * Swahili runs about 30% longer: never fix text widths.
 */
val LindaTypography = Typography(
    headlineLarge = TextStyle(fontFamily = Poppins, fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontFamily = Poppins, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontFamily = Poppins, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontFamily = Inter, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontFamily = Inter, fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontFamily = Inter, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontFamily = Inter, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontFamily = Inter, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = Inter, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = Inter, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

/** The two styles Material has no slot for. */
object LindaText {
    /** Amounts, counts and scores: Inter with tabular figures so digits line up. */
    val number = TextStyle(fontFamily = Inter, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")

    /** The big counter on the home screen ("12 scams stopped"). */
    val bigNumber = TextStyle(fontFamily = Inter, fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")

    /** The layer letters L I N D A: Poppins Bold, +8% letter spacing. Always 18sp or larger so white-on-green still passes contrast. */
    val layerLetter = TextStyle(fontFamily = Poppins, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em)

    /** The LINDA wordmark. */
    val wordmark = TextStyle(fontFamily = Poppins, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em)
}
