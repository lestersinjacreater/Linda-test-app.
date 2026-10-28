package com.linda.app.features.checker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.linda.app.core.ui.components.Pill
import com.linda.app.core.ui.components.SectionCard
import com.linda.app.core.ui.components.TintBox
import com.linda.app.core.ui.components.categoryTitle
import com.linda.app.core.ui.theme.RiskStyle
import com.linda.app.core.ui.theme.Spacing
import com.linda.app.core.util.PhoneNumbers
import com.linda.app.features.calls.CallCardOverlay
import com.linda.app.features.calls.CallWarning
import com.linda.app.features.calls.VerifiedCall
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linda.app.R
import com.linda.app.core.ui.components.LevelChip
import com.linda.app.core.util.Prefs
import com.linda.app.features.detection.ReasonsJson
import com.linda.app.features.detection.RiskLevel
import com.linda.app.features.trace.LayerTraceBuilder
import com.linda.app.features.trace.LayerTraceView
import com.linda.app.features.trace.TraceInput
import com.linda.app.features.detail.VerdictCard
import com.linda.app.features.detail.WarningReasons
import com.linda.app.core.ui.theme.LindaTheme
import com.linda.app.core.util.formatDateTime
import com.linda.app.features.trace.rememberVerdictHaptic
import com.linda.app.core.ui.components.LindaButton
import com.linda.app.core.ui.components.LindaTextField

/**
 * Ask Linda (the Check tab; design idea from Kelvin's prototype): paste a message, share one from WhatsApp or any app,
 * or type a phone number. A message is checked on the phone by the same model and rules as a real SMS. A number is
 * looked up like an incoming call: this phone's own lists first, then Safaricom's fraud list privately (contract 5.10:
 * only part of a scrambled code leaves the phone, never the number). Tap an example to try it. It is a question box
 * laid out like a chat, not a chatbot: every answer comes from the detector or the lookup, never made up.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckerScreen(sharedText: String?, state: CheckerViewModel = viewModel()) {
    val context = LocalContext.current
    val language = Prefs.effectiveLanguage(context)
    // Text and verdict live in the view model, so turning the phone or changing language keeps them.
    val text = state.text
    val result = state.result
    val checking = state.checking
    val asked = state.asked

    // Text shared from another app arrives pre-filled and is checked straight away (once: coming back to this
    // tab with the same shared text must not check it, and save it to History, a second time).
    LaunchedEffect(sharedText) {
        if (!sharedText.isNullOrBlank() && sharedText.trim() != state.asked) state.check(sharedText)
    }

    // After each answer, scroll so the question bubble is at the top and the answer right under it: on a small phone the
    // answer is otherwise below the examples and the box, out of sight (found by the virtual phone tour).
    val scroll = rememberScrollState()
    var bubbleY by remember { mutableIntStateOf(0) }
    LaunchedEffect(result, state.numberAnswer) {
        if (result != null || state.numberAnswer != null) {
            delay(150) // let the answer be laid out first
            scroll.animateScrollTo(bubbleY)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.checker_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.checker_help), color = MaterialTheme.colorScheme.onSurfaceVariant)

        // Examples to try in one tap: two scam messages and Safaricom Customer Care's real number.
        Text(stringResource(R.string.ask_try), style = MaterialTheme.typography.labelLarge, color = LindaTheme.colors.textSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            listOf(R.string.ask_example_reversal, R.string.ask_example_prize, R.string.ask_example_care).forEach { id ->
                val example = stringResource(id)
                ExampleChip(example, enabled = !checking) { state.check(example) }
            }
        }

        LindaTextField(
            value = text,
            onValueChange = { state.edit(it) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            label = { Text(stringResource(R.string.checker_hint)) },
        )
        LindaButton(
            stringResource(if (checking) R.string.ask_checking else R.string.checker_button), onClick = { state.check(text) },
            modifier = Modifier.fillMaxWidth(), enabled = text.isNotBlank() && !checking,
        )

        // The question, as the person's own bubble on the right, then Linda's answer under it.
        asked?.let { QuestionBubble(it, Modifier.onGloballyPositioned { c -> bubbleY = c.positionInParent().y.toInt() }) }

        state.numberAnswer?.let { NumberAnswerCard(it, language) }

        result?.let { r ->
            val v = r.verdict
            val body = asked.orEmpty()
            // The five layers, lit up one by one; the phone buzzes once or twice for a Caution or Scam.
            val trace = remember(r) {
                LayerTraceBuilder.build(
                    TraceInput(
                        level = v.level.name, score = v.score, category = v.category,
                        reasonCodes = v.reasons.map { it.code }.toSet(), scamWords = LayerTraceBuilder.scamWords(v.reasons),
                        body = body, senderVerified = false, onBlocklist = false, reported = false, guardianAlerted = false, isPasted = true,
                    ),
                )
            }
            val time = remember(r) { formatDateTime(System.currentTimeMillis()) }
            VerdictCard(
                level = v.level.name,
                sender = stringResource(R.string.sender_pasted),
                time = time,
                body = body,
                fakeMpesa = v.category == "fake_mpesa",
                trace = trace,
                reasons = WarningReasons.shown(v.reasons, v.category == "fake_mpesa").map { ReasonsJson.text(it, language) },
                onTraceLanded = rememberVerdictHaptic(v.level.name),
                riseKey = r,
            ) {
                // A safe message gets a short, honest note; a warning explains itself in the reasons above.
                if (v.level == RiskLevel.SAFE) {
                    // "Cannot be sure" is honest for wording alone, but not when the message matched a real M-Pesa message.
                    val matchedReal = v.reasons.any { it.code == "memory_same_as_real" }
                    val note = if (matchedReal) R.string.checker_safe_note_real_mpesa else R.string.checker_safe_note
                    Text(stringResource(note), style = MaterialTheme.typography.bodyLarge, color = LindaTheme.colors.textPrimary)
                }
            }
        }
    }
}

/** One tappable example. Long examples are cut to one line; the whole text is what gets checked. */
@Composable
private fun ExampleChip(text: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LindaTheme.colors
    Text(
        text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textPrimary,
        modifier = Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(50))
            .border(1.dp, colors.border, RoundedCornerShape(50))
            .background(colors.surface)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
    )
}

/** What the person asked, on the right like a sent chat message. */
@Composable
private fun QuestionBubble(text: String, modifier: Modifier = Modifier) {
    val colors = LindaTheme.colors
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Text(
            text,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textPrimary,
            modifier = Modifier.fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp))
                .background(colors.risk("SAFE").tint)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        )
    }
}

/**
 * Linda's answer about a number, in the same words as the card over a ringing call: a big pill, one line, and where the
 * answer came from (Safaricom's list, this phone, or "could not reach the list", so the person knows how sure it is).
 */
@Composable
private fun NumberAnswerCard(answer: NumberAnswer, language: String) {
    val colors = LindaTheme.colors
    val label = answer.check.label
    SectionCard(title = PhoneNumbers.pretty(answer.msisdn)) {
        if (label == null) {
            Pill(stringResource(R.string.ask_number_unknown_pill), colors.surfaceRaised, colors.textPrimary)
            Text(stringResource(R.string.ask_number_unknown), style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
        } else {
            val look = CallCardOverlay.look(label)
            val style = when (look.tone) {
                CallCardOverlay.Tone.SCAM -> colors.risk("SCAM")
                CallCardOverlay.Tone.CAUTION -> colors.risk("CAUTION")
                CallCardOverlay.Tone.SAFE -> colors.risk("SAFE")
                CallCardOverlay.Tone.NEUTRAL -> RiskStyle(colors.textPrimary, colors.textPrimary, colors.surfaceRaised, colors.surface)
            }
            val name = (label as? VerifiedCall)?.caller?.name(language)
            Pill(if (name != null) stringResource(look.pill, name) else stringResource(look.pill), style.tint, style.text)
            val line = when {
                name != null -> stringResource(look.line, name)
                look.lineArg != null -> stringResource(look.line, look.lineArg)
                else -> stringResource(look.line)
            }
            val category = (label as? CallWarning.ReportedNumber)?.category ?: (label as? CallWarning.SuspectedNumber)?.category
            TintBox(style.tint) {
                category?.let { Text(categoryTitle(it), style = MaterialTheme.typography.titleMedium, color = style.text) }
                Text(line, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            }
        }
        val source = when {
            answer.check.checkedLive -> R.string.ask_source_live
            answer.check.knownOffline -> R.string.ask_source_phone
            else -> R.string.ask_source_offline
        }
        Text(stringResource(source), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
    }
}
