package com.linda.app.features.demo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linda.app.R
import com.linda.app.core.ui.theme.LindaTheme
import com.linda.app.core.ui.theme.Motion
import com.linda.app.core.ui.theme.Radius
import com.linda.app.core.ui.theme.rememberReduceMotion
import com.linda.app.core.ui.theme.Spacing
import com.linda.app.features.trace.LayerTraceView

/**
 * The demo overlay (docs/design-system.md 7.8): a translucent panel at the bottom of the screen that shows the Layer
 * Trace live for each message Linda analyses, with every layer's raw output underneath in a monospaced font.
 * It is what the judges watch. It can be folded up to a one-line bar, or hidden with Hide.
 */
@Composable
fun DemoOverlayPanel(snapshot: DemoSnapshot?, onHide: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LindaTheme.colors
    var folded by remember { mutableStateOf(false) }
    val reduceMotion = rememberReduceMotion()
    val shape = RoundedCornerShape(topStart = Radius.large, topEnd = Radius.large)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface.copy(alpha = 0.94f))
            .border(1.dp, colors.border, shape)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(stringResource(R.string.overlay_title), style = MaterialTheme.typography.titleSmall, color = colors.primary, modifier = Modifier.weight(1f))
            OverlayAction(stringResource(if (folded) R.string.overlay_unfold else R.string.overlay_fold)) { folded = !folded }
            OverlayAction(stringResource(R.string.overlay_hide), onClick = onHide)
        }
        AnimatedVisibility(
            visible = !folded,
            enter = if (reduceMotion) EnterTransition.None else expandVertically(Motion.calmSpring()) + fadeIn(),
            exit = if (reduceMotion) ExitTransition.None else shrinkVertically(Motion.calmSpring()) + fadeOut(),
        ) {
            Column {
            if (snapshot == null) {
                Text(stringResource(R.string.overlay_waiting), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                ) {
                    // A new message replays the sweep, even if its result looks the same as the last one.
                    LayerTraceView(snapshot.trace, snapshot.level, replayKey = snapshot.sequence)
                    DemoOverlayText.lines(snapshot).forEach { (letter, text) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Text(letter, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = colors.primary, modifier = Modifier.width(16.dp))
                            Text(text, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp, color = colors.textPrimary)
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun OverlayAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = LindaTheme.colors.textPrimary,
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.small))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
    )
}
