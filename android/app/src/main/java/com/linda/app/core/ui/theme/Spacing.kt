package com.linda.app.core.ui.theme

import androidx.compose.ui.unit.dp

/** The only spacing values allowed (docs/design-system.md section 5): a 4dp base. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp   // screen padding and card padding
    val xl = 24.dp   // verdict card padding
    val xxl = 32.dp  // gap between sections
    val huge = 48.dp
    val max = 64.dp

    val screenPadding = lg
    val cardPadding = lg
    val verdictPadding = xl
    val sectionGap = xxl
    val minTouchTarget = 48.dp
}
