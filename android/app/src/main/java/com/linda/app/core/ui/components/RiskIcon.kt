package com.linda.app.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.linda.app.core.ui.theme.LindaTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * The three risk icons from docs/design-system.md 3.3: shield with a tick (safe), triangle with "!" (caution),
 * octagon with "!" (scam). Colour is never the only signal, so a label always goes next to this icon, which is
 * why it is hidden from screen readers (the label is what they read).
 */
@Composable
fun RiskIcon(level: String, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val style = LindaTheme.colors.risk(level)
    Canvas(modifier.size(size).clearAndSetSemantics { }) {
        when (level) {
            "SCAM" -> drawOctagon(style.accent, style.onAccent)
            "CAUTION" -> drawTriangle(style.accent, style.onAccent)
            else -> drawShield(style.accent, style.onAccent)
        }
    }
}

private fun DrawScope.at(x: Float, y: Float) = Offset(size.width * x, size.height * y)

private fun DrawScope.drawExclamation(color: Color, top: Float, bottom: Float, dotY: Float) {
    val w = size.width * 0.09f
    drawLine(color, at(0.5f, top), at(0.5f, bottom), strokeWidth = w, cap = StrokeCap.Round)
    drawCircle(color, radius = w * 0.65f, center = at(0.5f, dotY))
}

private fun DrawScope.drawOctagon(fill: Color, mark: Color) {
    val path = Path()
    for (i in 0 until 8) {
        val angle = Math.toRadians(22.5 + 45.0 * i).toFloat()
        val p = at(0.5f + 0.46f * cos(angle), 0.5f + 0.46f * sin(angle))
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    drawPath(path, fill)
    drawPath(path, fill, style = Stroke(width = size.width * 0.06f, join = StrokeJoin.Round))  // softens the corners
    drawExclamation(mark, 0.26f, 0.55f, 0.71f)
}

private fun DrawScope.drawTriangle(fill: Color, mark: Color) {
    val path = Path().apply {
        val top = at(0.5f, 0.12f); val right = at(0.92f, 0.86f); val left = at(0.08f, 0.86f)
        moveTo(top.x, top.y); lineTo(right.x, right.y); lineTo(left.x, left.y); close()
    }
    drawPath(path, fill)
    drawPath(path, fill, style = Stroke(width = size.width * 0.09f, join = StrokeJoin.Round))
    drawExclamation(mark, 0.4f, 0.6f, 0.74f)
}

private fun DrawScope.drawShield(fill: Color, mark: Color) {
    val path = Path().apply {
        val a = at(0.5f, 0.06f); val b = at(0.88f, 0.2f); val c = at(0.88f, 0.5f)
        moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y)
        val c1 = at(0.88f, 0.74f); val c2 = at(0.7f, 0.9f); val bottom = at(0.5f, 0.96f)
        cubicTo(c1.x, c1.y, c2.x, c2.y, bottom.x, bottom.y)
        val d1 = at(0.3f, 0.9f); val d2 = at(0.12f, 0.74f); val e = at(0.12f, 0.5f)
        cubicTo(d1.x, d1.y, d2.x, d2.y, e.x, e.y)
        val f = at(0.12f, 0.2f)
        lineTo(f.x, f.y); close()
    }
    drawPath(path, fill)
    val tick = Path().apply {
        val p1 = at(0.32f, 0.5f); val p2 = at(0.45f, 0.63f); val p3 = at(0.69f, 0.38f)
        moveTo(p1.x, p1.y); lineTo(p2.x, p2.y); lineTo(p3.x, p3.y)
    }
    drawPath(tick, mark, style = Stroke(width = size.width * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
