package com.linda.app.features.recovery

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.linda.app.LindaApp
import com.linda.app.R
import com.linda.app.core.data.DetectionEntity
import com.linda.app.core.util.formatDateTime

/** What a button next to a step does. Linda only OPENS the phone's dialer or messages app; the person presses call or send. */
private data class StepAction(val label: String, val open: (Context) -> Unit)

private fun openDialer(number: String): (Context) -> Unit = { ctx -> launch(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)))) }
private fun openMessages(number: String): (Context) -> Unit = { ctx -> launch(ctx, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number"))) }
private fun launch(ctx: Context, intent: Intent) {
    try { ctx.startActivity(intent) } catch (e: ActivityNotFoundException) { /* no app for it: the number is still written in the step */ }
}

/**
 * Recovery mode (F10): "I've been scammed, what now?". Works with no network and no server. [detectionId] is the warning
 * the person is reacting to (so the scammer's number, date and message are filled in), or -1 when opened from Home.
 */
@Composable
fun RecoveryScreen(detectionId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as LindaApp
    val clipboard = LocalClipboardManager.current
    val detection: DetectionEntity? by produceState<DetectionEntity?>(null, detectionId) {
        value = if (detectionId >= 0) app.database.detectionDao().getById(detectionId) else null
    }

    var amountText by remember { mutableStateOf("") }
    var timing by remember { mutableStateOf(Timing.JUST_NOW) }
    var paidTo by remember { mutableStateOf("") }
    var paidToEdited by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var viaBank by remember { mutableStateOf(false) }
    var includeMessage by remember { mutableStateOf(false) }
    var copiedLabel by remember { mutableStateOf<String?>(null) }
    val done = remember { mutableStateMapOf<StepId, Boolean>() }

    // The scammer usually is the person the money went to, so start with their number (still editable).
    val scammer = detection?.senderMsisdn ?: detection?.sender
    LaunchedEffect(scammer) { if (!paidToEdited && paidTo.isEmpty() && scammer != null) paidTo = scammer }

    val amount = amountText.filter { it.isDigit() }.take(9).toIntOrNull()
    val cleanedCode = ReportText.cleanTransactionCode(code)
    val steps = RecoveryPlan.steps(RecoveryInput(timing, amount, viaBank))
    val facts = ReportFacts(
        scammerNumber = scammer, amountKes = amount, paidToNumber = paidTo.ifBlank { null }, transactionCode = cleanedCode,
        scamReceivedAt = detection?.let { formatDateTime(it.receivedAt) }, scamCategory = detection?.category,
        scamMessage = if (includeMessage) detection?.body else null,
    )

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.recovery_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(if (RecoveryPlan.isUrgent(timing)) R.string.recovery_intro_urgent else R.string.recovery_intro_late),
            style = MaterialTheme.typography.bodyLarge,
            color = if (RecoveryPlan.isUrgent(timing)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ---- a few optional questions: they only make the steps and the report text exact ----
        Card(
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.recovery_form_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.recovery_form_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.recovery_when_title), style = MaterialTheme.typography.titleSmall)
                listOf(
                    Timing.JUST_NOW to R.string.recovery_when_now,
                    Timing.WITHIN_24H to R.string.recovery_when_24h,
                    Timing.OVER_24H to R.string.recovery_when_over,
                ).forEach { (t, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = timing == t, onClick = { timing = t })
                        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                OutlinedTextField(
                    value = amountText, onValueChange = { amountText = it.filter { c -> c.isDigit() }.take(9) },
                    label = { Text(stringResource(R.string.recovery_amount)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = paidTo, onValueChange = { paidTo = it; paidToEdited = true },
                    label = { Text(stringResource(R.string.recovery_paid_to)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = code, onValueChange = { code = it.take(12) },
                    label = { Text(stringResource(R.string.recovery_code)) }, singleLine = true,
                    isError = code.isNotBlank() && cleanedCode == null,
                    supportingText = { if (code.isNotBlank() && cleanedCode == null) Text(stringResource(R.string.recovery_code_invalid)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = viaBank, onCheckedChange = { viaBank = it })
                    Text(stringResource(R.string.recovery_bank), modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                }
                if (detection != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = includeMessage, onCheckedChange = { includeMessage = it })
                        Text(stringResource(R.string.recovery_include_message), modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        // ---- the checklist ----
        Text(stringResource(R.string.recovery_steps_title), style = MaterialTheme.typography.titleLarge)
        steps.forEachIndexed { index, step ->
            val (title, body) = stepWords(step)
            Card(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}. $title", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Checkbox(checked = done[step] == true, onCheckedChange = { done[step] = it })
                    }
                    Text(body, style = MaterialTheme.typography.bodyLarge)
                    stepActions(step).forEach { action ->
                        OutlinedButton(onClick = { action.open(context) }, modifier = Modifier.fillMaxWidth()) { Text(action.label) }
                    }
                }
            }
        }
        Text(stringResource(R.string.recovery_action_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // ---- the pre-filled report text, in both languages ----
        Text(stringResource(R.string.recovery_report_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        listOf("en" to R.string.recovery_report_title_en, "sw" to R.string.recovery_report_title_sw).forEach { (lang, title) ->
            val text = ReportText.build(lang, facts)
            Card(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { clipboard.setText(AnnotatedString(text)); copiedLabel = lang }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.recovery_copy))
                    }
                    if (copiedLabel == lang) Text(stringResource(R.string.recovery_copied), color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Text(stringResource(R.string.recovery_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_back)) }
    }
}

/** Title and words for each step. Every number comes from [RecoveryConfig], never typed into a string. */
@Composable
private fun stepWords(step: StepId): Pair<String, String> = with(RecoveryConfig) {
    when (step) {
        StepId.STOP -> stringResource(R.string.step_stop_title) to stringResource(R.string.step_stop_body)
        StepId.EVIDENCE -> stringResource(R.string.step_evidence_title) to stringResource(R.string.step_evidence_body)
        StepId.REVERSAL -> stringResource(R.string.step_reversal_title) to stringResource(R.string.step_reversal_body, REVERSAL_SHORT_CODE, REVERSAL_WINDOW_HOURS)
        StepId.CALL_CARE -> stringResource(R.string.step_care_title) to stringResource(R.string.step_care_body, CUSTOMER_CARE_PREPAID, CUSTOMER_CARE_POSTPAID)
        StepId.CALL_CARE_LATE -> stringResource(R.string.step_care_late_title) to
            stringResource(R.string.step_care_late_body, REVERSAL_WINDOW_HOURS, CUSTOMER_CARE_PREPAID, CUSTOMER_CARE_POSTPAID)
        StepId.REPORT_SCAM -> stringResource(R.string.step_report_title) to stringResource(R.string.step_report_body, FRAUD_REPORT_SHORT_CODE)
        StepId.BANK -> stringResource(R.string.step_bank_title) to stringResource(R.string.step_bank_body)
        StepId.POLICE -> stringResource(R.string.step_police_title) to stringResource(R.string.step_police_body, DCI_HOTLINE_DISPLAY, POLICE_EMERGENCY, POLICE_EMERGENCY_ALT)
        StepId.POLICE_OPTIONAL -> stringResource(R.string.step_police_optional_title) to stringResource(R.string.step_police_optional_body, DCI_HOTLINE_DISPLAY)
        StepId.BEWARE_RECOVERY -> stringResource(R.string.step_beware_title) to stringResource(R.string.step_beware_body)
    }
}

/** The buttons under a step. */
@Composable
private fun stepActions(step: StepId): List<StepAction> = with(RecoveryConfig) {
    when (step) {
        StepId.REVERSAL -> listOf(StepAction(stringResource(R.string.recovery_open_sms, REVERSAL_SHORT_CODE), openMessages(REVERSAL_SHORT_CODE)))
        StepId.REPORT_SCAM -> listOf(StepAction(stringResource(R.string.recovery_open_sms, FRAUD_REPORT_SHORT_CODE), openMessages(FRAUD_REPORT_SHORT_CODE)))
        StepId.CALL_CARE, StepId.CALL_CARE_LATE -> listOf(
            StepAction(stringResource(R.string.recovery_call, CUSTOMER_CARE_PREPAID), openDialer(CUSTOMER_CARE_PREPAID)),
            StepAction(stringResource(R.string.recovery_call, CUSTOMER_CARE_POSTPAID), openDialer(CUSTOMER_CARE_POSTPAID)),
        )
        StepId.POLICE, StepId.POLICE_OPTIONAL -> listOf(StepAction(stringResource(R.string.recovery_call, DCI_HOTLINE_DISPLAY), openDialer(DCI_HOTLINE_DIAL)))
        else -> emptyList()
    }
}
