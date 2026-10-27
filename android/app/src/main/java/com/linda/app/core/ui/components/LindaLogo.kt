package com.linda.app.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.linda.app.core.ui.theme.Palette

/**
 * The Linda mark (docs/design-system.md 10; after Kelvin's design): a Safaricom-green rounded square with a white shield
 * and a tick, "you are protected". The same drawing as the launcher icon (res/drawable/ic_launcher_foreground.xml) and
 * the dashboard's mark. [description] is read by screen readers; pass null when a word next to it already says "Linda".
 */
@Composable
fun LindaLogo(modifier: Modifier = Modifier, size: Dp = 72.dp, description: String? = null) {
    val a11y = if (description != null) Modifier.semantics { contentDescription = description } else Modifier
    Canvas(modifier.size(size).then(a11y)) {
        val w = this.size.width
        fun at(x: Float, y: Float) = Offset(w * x, w * y)
        drawRoundRect(Color(Palette.GREEN_700), cornerRadius = CornerRadius(w * 0.24f))
        val shield = Path().apply {
            val top = at(0.5f, 0.2f)
            moveTo(top.x, top.y)
            at(0.74f, 0.29f).let { lineTo(it.x, it.y) }
            at(0.74f, 0.5f).let { lineTo(it.x, it.y) }
            val c1 = at(0.74f, 0.66f); val c2 = at(0.63f, 0.76f); val bottom = at(0.5f, 0.81f)
            cubicTo(c1.x, c1.y, c2.x, c2.y, bottom.x, bottom.y)
            val d1 = at(0.37f, 0.76f); val d2 = at(0.26f, 0.66f); val left = at(0.26f, 0.5f)
            cubicTo(d1.x, d1.y, d2.x, d2.y, left.x, left.y)
            at(0.26f, 0.29f).let { lineTo(it.x, it.y) }
            close()
        }
        val white = Color(Palette.WHITE)
        drawPath(shield, white, style = Stroke(width = w * 0.06f, join = StrokeJoin.Round))
        val tick = Path().apply {
            at(0.39f, 0.5f).let { moveTo(it.x, it.y) }
            at(0.47f, 0.58f).let { lineTo(it.x, it.y) }
            at(0.62f, 0.42f).let { lineTo(it.x, it.y) }
        }
        drawPath(tick, white, style = Stroke(width = w * 0.065f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
