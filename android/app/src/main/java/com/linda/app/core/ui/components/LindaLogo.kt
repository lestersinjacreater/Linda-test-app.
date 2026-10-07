package com.linda.app.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.linda.app.core.ui.theme.LindaTheme

/**
 * The Linda mark (docs/design-system.md 10): a shield cut into five segments, one per layer, in the layer tints.
 * [description] is read by screen readers; pass null when a word next to it already says "Linda".
 */
@Composable
fun LindaLogo(modifier: Modifier = Modifier, size: Dp = 72.dp, description: String? = null) {
    val tints = LindaTheme.colors.layers
    val a11y = if (description != null) Modifier.semantics { contentDescription = description } else Modifier
    Canvas(modifier.size(size).then(a11y)) {
        val w = this.size.width
        val h = this.size.height
        fun at(x: Float, y: Float) = Offset(w * x, h * y)
        val shield = Path().apply {
            val a = at(0.5f, 0.04f); val b = at(0.9f, 0.18f); val c = at(0.9f, 0.5f)
            moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y)
            val c1 = at(0.9f, 0.76f); val c2 = at(0.72f, 0.92f); val bottom = at(0.5f, 0.98f)
            cubicTo(c1.x, c1.y, c2.x, c2.y, bottom.x, bottom.y)
            val d1 = at(0.28f, 0.92f); val d2 = at(0.1f, 0.76f); val e = at(0.1f, 0.5f)
            cubicTo(d1.x, d1.y, d2.x, d2.y, e.x, e.y)
            val f = at(0.1f, 0.18f)
            lineTo(f.x, f.y); close()
        }
        val left = w * 0.1f
        val band = w * 0.8f / tints.size
        val gap = w * 0.012f
        clipPath(shield) {
            tints.forEachIndexed { i, color ->
                drawRect(color, topLeft = Offset(left + band * i + gap, 0f), size = Size(band - gap * 2, h))
            }
        }
    }
}
