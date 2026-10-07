package com.linda.app.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Radii from docs/design-system.md section 5. */
object Radius {
    val small = 8.dp    // inputs, chips, layer segments
    val medium = 16.dp  // cards, buttons
    val large = 24.dp   // verdict cards, bottom sheets
}

val SmallShape = RoundedCornerShape(Radius.small)
val MediumShape = RoundedCornerShape(Radius.medium)
val LargeShape = RoundedCornerShape(Radius.large)
val FullShape = RoundedCornerShape(50)

/** Material components pick their corners from here, so stock buttons, dialogs and sheets follow the radii too. */
val LindaShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.small),
    small = SmallShape,
    medium = MediumShape,
    large = LargeShape,
    extraLarge = LargeShape,
)

/**
 * The signature "shield notch": a rounded rectangle whose bottom-right corner is cut off at an angle,
 * like the point of a shield. Only for hero elements (home header, hero cards) so it stays special.
 */
class ShieldNotchShape(private val corner: Dp = Radius.medium, private val notch: Dp = Radius.large) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { corner.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2f)
        val n = with(density) { notch.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2f)
        val d = c * 2f
        val path = Path().apply {
            moveTo(c, 0f)
            lineTo(size.width - c, 0f)
            arcTo(Rect(size.width - d, 0f, size.width, d), -90f, 90f, false)   // top-right, rounded
            lineTo(size.width, size.height - n)
            lineTo(size.width - n, size.height)                                 // the cut
            lineTo(c, size.height)
            arcTo(Rect(0f, size.height - d, d, size.height), 90f, 90f, false)  // bottom-left, rounded
            lineTo(0f, c)
            arcTo(Rect(0f, 0f, d, d), 180f, 90f, false)                         // top-left, rounded
            close()
        }
        return Outline.Generic(path)
    }
}
