package com.linda.app.features.demo

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.linda.app.R
import com.linda.app.core.ui.components.ListGroup
import com.linda.app.core.ui.components.ListRow
import com.linda.app.core.ui.components.Pill
import com.linda.app.core.ui.theme.LindaTheme
import com.linda.app.core.ui.theme.Spacing
import com.linda.app.core.util.PhoneNumbers
import com.linda.app.core.util.Prefs
import com.linda.app.features.calls.CallCardOverlay
import com.linda.app.features.calls.CallLabel
import com.linda.app.features.calls.CallWarnings
import com.linda.app.features.calls.VerifiedCall
import com.linda.app.features.detection.RiskLevel
import com.linda.app.features.sms.MessageProcessor
import kotlinx.coroutines.launch

/** One numbered step of the demo sheet. */
sealed interface DemoStep {
    val title: Int
    data class Sms(override val title: Int, val sample: DemoSample) : DemoStep
    data class Call(override val title: Int, val number: String, val needsScamFirst: Boolean = false) : DemoStep
    data class Help(override val title: Int) : DemoStep
}

/** What happened after a step, so Home can show it: nothing to add, a quiet result, or the call card. */
sealed interface DemoOutcome {
    data object Shown : DemoOutcome
    data class Quiet(val message: String) : DemoOutcome
    data class CallCard(val msisdn: String, val label: CallLabel) : DemoOutcome
}

private val steps: List<DemoStep> by lazy {
    val s = DemoSamples.all
    listOf(
        DemoStep.Sms(R.string.demo_step_sms, s[0]),
        DemoStep.Call(R.string.demo_step_call_scammer, DemoSamples.SCAMMER_NUMBER, needsScamFirst = true),
        DemoStep.Call(R.string.demo_step_call_care, DemoSamples.SAFARICOM_CARE_NUMBER),
        DemoStep.Call(R.string.demo_step_call_unknown, DemoSamples.UNKNOWN_NUMBER),
        DemoStep.Sms(R.string.demo_reversal, s[1]),
        DemoStep.Sms(R.string.demo_prize, s[2]),
        DemoStep.Sms(R.string.demo_new_number, s[5]),
        DemoStep.Sms(R.string.demo_payment_changed, s[6]),
        DemoStep.Sms(R.string.demo_real_mpesa, s[7]),
        DemoStep.Sms(R.string.demo_chat, s[8]),
        DemoStep.Help(R.string.demo_step_help),
    )
}

/**
 * Runs one step through the REAL path: a message goes through MessageProcessor like a real SMS (a real notification and
 * alert; demo messages are never reported), and a call goes through the same check as a ringing call.
 */
object DemoRunner {
    suspend fun run(context: Context, step: DemoStep): DemoOutcome = when (step) {
        is DemoStep.Help -> DemoOutcome.Shown
        is DemoStep.Sms -> {
            val v = MessageProcessor(context).process(step.sample.body, step.sample.sender, System.currentTimeMillis(), source = "demo", notify = true).verdict
            if (v.level == RiskLevel.SAFE) DemoOutcome.Quiet(context.getString(R.string.demo_quiet, context.getString(step.title)))
            else DemoOutcome.Shown
        }
        is DemoStep.Call -> {
            // "The number that just sent the scam" needs that scam message first, so the step works on its own too.
            if (step.needsScamFirst && CallWarnings.lookup(context, step.number).label == null) {
                val first = DemoSamples.all[0]
                MessageProcessor(context).process(first.body, first.sender, System.currentTimeMillis(), source = "demo", notify = true)
            }
            val label = CallWarnings.check(context, step.number)
            when {
                label == null -> DemoOutcome.Quiet(context.getString(R.string.demo_call_quiet))
                // With "Display over other apps" allowed, the real card is already on screen; otherwise show it in the app.
                CallCardOverlay.canShow(context) -> DemoOutcome.Shown
                else -> DemoOutcome.CallCard(step.number, label)
            }
        }
    }
}

/**
 * The demo sheet (design idea from Kelvin's prototype; docs/design-system.md 7.19), opened from the Demo chip on Home:
 * numbered steps a presenter or a judge can tap. Home runs the chosen step ([DemoRunner]) after the sheet closes, so the
 * notification or the call card is what people see. The real M-Pesa message and the chat must stay quiet: that proves
 * Linda does not cry wolf.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoSheet(onDismiss: () -> Unit, onPick: (DemoStep) -> Unit) {
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = LindaTheme.colors.background) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(stringResource(R.string.demo_title), style = MaterialTheme.typography.titleLarge, color = LindaTheme.colors.textPrimary)
            Text(stringResource(R.string.demo_sheet_help), style = MaterialTheme.typography.bodyMedium, color = LindaTheme.colors.textSecondary)
            ListGroup {
                steps.forEachIndexed { i, step ->
                    ListRow(
                        "${i + 1}   " + stringResource(step.title), divider = i > 0, chevron = true,
                        onClick = { scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss(); onPick(step) } },
                    )
                }
            }
            Text(stringResource(R.string.demo_sheet_note), style = MaterialTheme.typography.bodySmall, color = LindaTheme.colors.textSecondary,
                modifier = Modifier.padding(bottom = Spacing.lg))
        }
    }
}

/** The call card inside the app, for phones where "Display over other apps" is off: the same words as the real card. */
@Composable
fun DemoCallCard(msisdn: String, label: CallLabel, onClose: () -> Unit) {
    val context = LocalContext.current
    val colors = LindaTheme.colors
    val look = CallCardOverlay.look(label)
    val style = when (look.tone) {
        CallCardOverlay.Tone.SCAM -> colors.risk("SCAM")
        CallCardOverlay.Tone.CAUTION -> colors.risk("CAUTION")
        CallCardOverlay.Tone.SAFE -> colors.risk("SAFE")
    }
    val name = (label as? VerifiedCall)?.caller?.name(Prefs.effectiveLanguage(context))
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surface).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(stringResource(R.string.call_card_from_linda), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
            Text(PhoneNumbers.pretty(msisdn), style = MaterialTheme.typography.headlineMedium, color = colors.textPrimary)
            Pill(if (name != null) stringResource(look.pill, name) else stringResource(look.pill), style.tint, style.text)
            Text(
                when {
                    name != null -> stringResource(look.line, name)
                    look.lineArg != null -> stringResource(look.line, look.lineArg)
                    else -> stringResource(look.line)
                },
                style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary,
            )
            Text(stringResource(R.string.call_card_you_decide), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            Text(stringResource(R.string.demo_call_card_note), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            com.linda.app.core.ui.components.LindaTextButton(stringResource(R.string.call_card_close), onClick = onClose, modifier = Modifier.fillMaxWidth())
        }
    }
}
