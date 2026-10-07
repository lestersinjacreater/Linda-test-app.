package com.linda.app.features.trace

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.linda.app.R
import com.linda.app.core.ui.components.categoryLabel
import com.linda.app.core.ui.theme.LindaText
import com.linda.app.core.ui.theme.LindaTheme
import com.linda.app.core.ui.theme.Palette
import com.linda.app.core.ui.theme.SegmentState
import com.linda.app.core.ui.theme.SmallShape
import com.linda.app.core.ui.theme.Spacing
import com.linda.app.core.ui.theme.SweepTiming
import com.linda.app.core.ui.theme.rememberReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val LAYER_NAMES = intArrayOf(R.string.trace_layer_l, R.string.trace_layer_i, R.string.trace_layer_n, R.string.trace_layer_d, R.string.trace_layer_a)
private val LAYER_EXPLAINERS = intArrayOf(R.string.trace_explain_l, R.string.trace_explain_i, R.string.trace_explain_n, R.string.trace_explain_d, R.string.trace_explain_a)

/** Letter colour on a tinted segment: dark on the three pale tints, white on the two deep greens (letters are 20sp bold). */
private fun letterOnTint(index: Int): Color = if (index < 3) Color(Palette.TEXT_PRIMARY_LIGHT) else Color(Palette.WHITE)

/**
 * The Layer Trace (docs/design-system.md 7.1 and 8): five segments L I N D A that light up left to right when a
 * message has been analysed. A layer that found a risk takes the verdict's colour and shakes slightly. Tap a letter to
 * read what that layer found. With "remove animations" on, it shows the finished state straight away.
 *
 * [results] must come from [LayerTraceBuilder.build]. [onLanded] runs once the sweep has finished (used for haptics).
 */
@Composable
fun LayerTraceView(
    results: List<LayerResult>,
    level: String,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    onLanded: () -> Unit = {},
) {
    val colors = LindaTheme.colors
    val risk = colors.risk(level)
    val reduceMotion = rememberReduceMotion()
    val finished = SweepTiming.ANIMATION_MS.toFloat()
    val elapsed = remember { Animatable(0f) }
    var expanded by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(results, animate, reduceMotion) {
        if (animate && !reduceMotion) {
            elapsed.snapTo(0f)
            elapsed.animateTo(finished, tween(SweepTiming.ANIMATION_MS, easing = LinearEasing))
        } else {
            elapsed.snapTo(finished)
        }
        onLanded()
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
            results.forEachIndexed { index, result ->
                val state = SweepTiming.stateAt(index, elapsed.value, result.flagged)
                val stateWord = stringResource(
                    when (state) {
                        SegmentState.IDLE -> R.string.trace_state_idle
                        SegmentState.SCANNING -> R.string.trace_state_scanning
                        SegmentState.PASSED -> R.string.trace_state_passed
                        SegmentState.FLAGGED -> R.string.trace_state_flagged
                    },
                )
                val name = stringResource(LAYER_NAMES[index])
                val fill = when (state) {
                    SegmentState.IDLE -> Color.Transparent
                    SegmentState.SCANNING -> colors.layer(index).copy(alpha = 0.55f)
                    SegmentState.PASSED -> colors.layer(index)
                    SegmentState.FLAGGED -> risk.accent
                }
                val letterColor = when (state) {
                    SegmentState.IDLE -> colors.textSecondary
                    SegmentState.SCANNING -> colors.textPrimary
                    SegmentState.PASSED -> letterOnTint(index)
                    SegmentState.FLAGGED -> risk.onAccent
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .offset(x = SweepTiming.shakeDp(index, elapsed.value).dp)
                        .clip(SmallShape)
                        .background(fill)
                        .then(if (state == SegmentState.IDLE) Modifier.border(BorderStroke(1.dp, colors.border), SmallShape) else Modifier)
                        .clickable(role = Role.Button) { expanded = if (expanded == index) null else index }
                        .semantics { contentDescription = "$name: $stateWord" },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(result.layer.letter, style = LindaText.layerLetter, color = letterColor)
                        Spacer(Modifier.height(Spacing.xs))
                        SegmentMark(state, letterColor)
                    }
                }
            }
        }

        val open = expanded
        if (open == null) {
            Text(
                stringResource(R.string.trace_tap_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        } else {
            LayerDetails(open, results[open])
        }
    }
}

/** A small tick (passed) or "!" (flagged) under the letter, so the state is never told by colour alone. */
@Composable
private fun SegmentMark(state: SegmentState, color: Color) {
    Canvas(Modifier.size(14.dp)) {
        val w = size.width
        val stroke = Stroke(width = w * 0.16f, cap = StrokeCap.Round)
        when (state) {
            SegmentState.PASSED -> {
                drawLine(color, Offset(w * 0.12f, w * 0.55f), Offset(w * 0.4f, w * 0.82f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.4f, w * 0.82f), Offset(w * 0.9f, w * 0.2f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            SegmentState.FLAGGED -> {
                drawLine(color, Offset(w * 0.5f, w * 0.08f), Offset(w * 0.5f, w * 0.6f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawCircle(color, radius = stroke.width * 0.6f, center = Offset(w * 0.5f, w * 0.88f))
            }
            else -> Unit
        }
    }
}

@Composable
private fun LayerDetails(index: Int, result: LayerResult) {
    val colors = LindaTheme.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier
            .fillMaxWidth()
            .clip(SmallShape)
            .background(colors.surfaceRaised)
            .padding(Spacing.md),
    ) {
        Text(
            result.layer.letter + " · " + stringResource(LAYER_NAMES[index]),
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Text(stringResource(LAYER_EXPLAINERS[index]), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        Spacer(Modifier.height(Spacing.xs))
        result.lines.forEach { line ->
            Text("• " + traceLineText(line), style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
        }
    }
}

/** A finding turned into words in the chosen language (strings.xml / values-sw). */
@Composable
fun traceLineText(line: TraceLine): String = when (line.code) {
    "l_clean" -> stringResource(R.string.trace_l_clean)
    "l_disguise" -> stringResource(R.string.trace_l_disguise, line.args[0], line.args[1])
    "i_category" -> stringResource(R.string.trace_i_category, categoryLabel(line.args[0]))
    "i_words" -> stringResource(R.string.trace_i_words, line.args[0])
    "i_score" -> stringResource(R.string.trace_i_score, line.args[0])
    "i_none" -> stringResource(R.string.trace_i_none)
    "n_verified" -> stringResource(R.string.trace_n_verified)
    "n_fake_mpesa" -> stringResource(R.string.trace_n_fake_mpesa)
    "n_personal" -> stringResource(R.string.trace_n_personal)
    "n_unknown" -> stringResource(R.string.trace_n_unknown)
    "n_link" -> stringResource(R.string.trace_n_link)
    "n_blocklist" -> stringResource(R.string.trace_n_blocklist)
    "n_pasted" -> stringResource(R.string.trace_n_pasted)
    "n_none" -> stringResource(R.string.trace_n_none)
    "d_verdict" -> stringResource(
        R.string.trace_d_verdict,
        stringResource(
            when (line.args[0]) {
                "SCAM" -> R.string.level_scam
                "CAUTION" -> R.string.level_caution
                else -> R.string.level_safe
            },
        ),
    )
    "a_warned" -> stringResource(R.string.trace_a_warned)
    "a_reported" -> stringResource(R.string.trace_a_reported)
    "a_guardian" -> stringResource(R.string.trace_a_guardian)
    "a_nothing" -> stringResource(R.string.trace_a_nothing)
    else -> line.code
}

/**
 * The buzz that goes with a fresh verdict (Caution one tick, Scam two pulses). It uses the phone's own touch-feedback
 * setting, so it needs no permission and stays silent if the person turned vibration off.
 */
@Composable
fun rememberVerdictHaptic(level: String): () -> Unit {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    return remember(level, haptic) {
        {
            scope.launch {
                repeat(HapticPlan.pulses(level)) { i ->
                    if (i > 0) delay(HapticPlan.GAP_MS)
                    haptic.performHapticFeedback(if (level == "SCAM") HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
                }
            }
            Unit
        }
    }
}
