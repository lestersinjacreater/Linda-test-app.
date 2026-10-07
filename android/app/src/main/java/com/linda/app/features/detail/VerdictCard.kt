package com.linda.app.features.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.linda.app.R
import com.linda.app.core.ui.components.FakeMpesaBadge
import com.linda.app.core.ui.components.RiskIcon
import com.linda.app.core.ui.components.levelLabel
import com.linda.app.core.ui.theme.LargeShape
import com.linda.app.core.ui.theme.LindaTheme
import com.linda.app.core.ui.theme.Motion
import com.linda.app.core.ui.theme.Spacing
import com.linda.app.core.ui.theme.rememberReduceMotion
import com.linda.app.core.ui.theme.scamRed
import com.linda.app.features.trace.LayerResult
import com.linda.app.features.trace.LayerTraceView

/**
 * The Verdict Card (docs/design-system.md 7.3): the risk icon and label, a three-line preview of the message, the
 * Layer Trace, the reasons, and the actions. Risk-tinted background with a 4dp bar in the risk colour on the left.
 * Only a Scam card gets the soft red glow.
 */
@Composable
fun VerdictCard(
    level: String,
    sender: String,
    time: String,
    body: String,
    fakeMpesa: Boolean,
    trace: List<LayerResult>?,
    reasons: List<String>,
    modifier: Modifier = Modifier,
    onTraceLanded: () -> Unit = {},
    riseKey: Any? = null,
    actions: @Composable ColumnScope.() -> Unit,
) {
    val colors = LindaTheme.colors
    val risk = colors.risk(level)
    // The card rises 16dp and fades in (docs/design-system.md section 8). With "remove animations" on it only fades, no movement.
    val reduceMotion = rememberReduceMotion()
    val arrival = remember { Animatable(0f) }
    LaunchedEffect(riseKey) {
        arrival.snapTo(0f)
        if (reduceMotion) arrival.animateTo(1f, tween(Motion.STANDARD_MS)) else arrival.animateTo(1f, Motion.calmSpring())
    }
    val riseDistance = with(LocalDensity.current) { 16.dp.toPx() }
    val glow = if (level == "SCAM") {
        Modifier.shadow(24.dp, LargeShape, ambientColor = scamRed.copy(alpha = 0.25f), spotColor = scamRed.copy(alpha = 0.25f))
    } else Modifier

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = arrival.value.coerceIn(0f, 1f)
                translationY = if (reduceMotion) 0f else (1f - arrival.value) * riseDistance
            }
            .then(glow)
            .clip(LargeShape)
            .background(risk.tint)
            .height(IntrinsicSize.Min),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(risk.accent))
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            modifier = Modifier.weight(1f).padding(Spacing.verdictPadding),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                RiskIcon(level, size = 32.dp)
                Text(levelLabel(level), style = MaterialTheme.typography.titleMedium, color = risk.text)
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text("$sender · $time", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
            if (fakeMpesa) FakeMpesaBadge()
            trace?.let { LayerTraceView(it, level, onLanded = onTraceLanded) }
            if (reasons.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(stringResource(R.string.detail_why), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    reasons.forEach { Text(it, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md), content = actions)
        }
    }
}
