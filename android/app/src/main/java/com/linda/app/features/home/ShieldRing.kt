package com.linda.app.features.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.linda.app.R
import com.linda.app.core.ui.components.LindaLogo
import com.linda.app.core.ui.theme.LindaTheme
import com.linda.app.core.ui.theme.Motion
import com.linda.app.core.ui.theme.rememberReduceMotion

/**
 * The home ring (docs/design-system.md 7.2): five arcs, one per layer, in the layer tints, around the Linda mark.
 * A layer that cannot work (a permission is off) is drawn faint. Once every 6 seconds it "breathes" (grows 2%),
 * unless the phone's "remove animations" setting is on.
 */
@Composable
fun ShieldRing(layers: List<LayerHealth>, modifier: Modifier = Modifier, size: Dp = 200.dp) {
    val colors = LindaTheme.colors
    val reduceMotion = rememberReduceMotion()
    val breath = rememberInfiniteTransition(label = "breath")
    val pulse by breath.animateFloat(
        initialValue = 1f, targetValue = 1.02f,
        animationSpec = infiniteRepeatable(tween(Motion.BREATH_MS / 2, easing = Motion.easeOut), RepeatMode.Reverse),
        label = "breathScale",
    )
    val scale = if (reduceMotion) 1f else pulse
    val working = layers.count { it.ok }
    val description = stringResource(R.string.home_ring_description, working)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale }.semantics { contentDescription = description },
    ) {
        Canvas(Modifier.size(size)) {
            val stroke = 16.dp.toPx()
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            val slice = 360f / layers.size
            val gap = 8f
            layers.forEachIndexed { i, health ->
                val tint = colors.layer(i)
                drawArc(
                    color = if (health.ok) tint else tint.copy(alpha = 0.25f),
                    startAngle = -90f + slice * i + gap / 2f,
                    sweepAngle = slice - gap,
                    useCenter = false,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = arc,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        LindaLogo(size = size * 0.4f)
    }
}
