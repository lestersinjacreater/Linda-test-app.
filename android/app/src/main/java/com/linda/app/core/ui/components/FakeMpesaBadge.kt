package com.linda.app.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.linda.app.R
import com.linda.app.core.ui.theme.LindaTheme
import com.linda.app.core.ui.theme.Spacing

/**
 * For a message that imitates an M-PESA confirmation but did not come from M-PESA (docs/design-system.md 7.7):
 * a crossed-out receipt and the plain truth. It is a Scam-level element, so it uses the Scam colours.
 */
@Composable
fun FakeMpesaBadge(modifier: Modifier = Modifier) {
    val risk = LindaTheme.colors.scam
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = modifier) {
        Canvas(Modifier.size(32.dp).clearAndSetSemantics { }) {
            val w = size.width
            fun at(x: Float, y: Float) = Offset(w * x, w * y)
            // a receipt with a zig-zag bottom edge
            val receipt = Path().apply {
                moveTo(at(0.2f, 0.08f).x, at(0.2f, 0.08f).y)
                lineTo(at(0.8f, 0.08f).x, at(0.8f, 0.08f).y)
                lineTo(at(0.8f, 0.92f).x, at(0.8f, 0.92f).y)
                lineTo(at(0.7f, 0.84f).x, at(0.7f, 0.84f).y)
                lineTo(at(0.6f, 0.92f).x, at(0.6f, 0.92f).y)
                lineTo(at(0.5f, 0.84f).x, at(0.5f, 0.84f).y)
                lineTo(at(0.4f, 0.92f).x, at(0.4f, 0.92f).y)
                lineTo(at(0.3f, 0.84f).x, at(0.3f, 0.84f).y)
                lineTo(at(0.2f, 0.92f).x, at(0.2f, 0.92f).y)
                close()
            }
            drawPath(receipt, risk.accent, style = Stroke(width = w * 0.07f, join = StrokeJoin.Round))
            for (y in listOf(0.28f, 0.44f, 0.6f)) drawLine(risk.accent, at(0.32f, y), at(0.68f, y), strokeWidth = w * 0.05f, cap = StrokeCap.Round)
            // the cross-out
            drawLine(risk.accent, at(0.06f, 0.94f), at(0.94f, 0.06f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
        }
        Text(stringResource(R.string.badge_fake_mpesa), style = MaterialTheme.typography.bodyLarge, color = risk.text)
    }
}
