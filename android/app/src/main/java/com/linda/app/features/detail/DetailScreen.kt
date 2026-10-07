package com.linda.app.features.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.linda.app.LindaApp
import com.linda.app.R
import com.linda.app.core.data.AllowedSenderEntity
import com.linda.app.core.data.DetectionEntity
import com.linda.app.core.ui.components.LindaButton
import com.linda.app.core.ui.theme.LindaTheme
import com.linda.app.core.ui.theme.Spacing
import com.linda.app.features.alerts.VoiceWarnings
import com.linda.app.core.util.Prefs
import com.linda.app.core.util.formatDateTime
import com.linda.app.features.detection.ReasonsJson
import com.linda.app.features.guardian.GuardianPolicy
import com.linda.app.features.trace.LayerTraceBuilder
import com.linda.app.features.trace.LayerTraceView
import com.linda.app.features.trace.TraceInput
import com.linda.app.core.ui.components.categoryLabel
import com.linda.app.features.reporting.CannotReport
import com.linda.app.features.reporting.ManualReport
import com.linda.app.features.reporting.ReportableDetection
import com.linda.app.features.reporting.ReportingService
import kotlinx.coroutines.launch

/** What opens when the user taps a warning: the message, the verdict, every reason, and what to do next (F6). */
@Composable
fun DetailScreen(detectionId: Long, takeover: Boolean = false, onOpenRecovery: (Long) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as LindaApp
    val language = Prefs.effectiveLanguage(context)
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableStateOf(0) }
    // The full-screen alert only when the person opened this from a warning notification, and only for a confident scam.
    var showTakeover by remember(detectionId) { mutableStateOf(takeover) }
    val detection: DetectionEntity? by produceState<DetectionEntity?>(null, detectionId, refresh) {
        value = app.database.detectionDao().getById(detectionId)
    }

    val d = detection
    if (d == null) {
        Text(stringResource(R.string.detail_not_found), modifier = Modifier.padding(24.dp))
        return
    }
    val reasons = ReasonsJson.decode(d.reasonsJson)

    // The Layer Trace: what each of the five layers found, from what the phone saved about this message.
    val trace by produceState<List<com.linda.app.features.trace.LayerResult>?>(null, d.id, d.reportedAt) {
        val onBlocklist = d.senderMsisdn?.let { app.database.blockedNumberDao().find(it) } != null
        val lastGuardianAlert = d.sender?.takeIf { it.isNotBlank() }?.let { app.database.guardianAlertDao().lastSentAt(GuardianPolicy.senderKey(it)) }
        val guardianAlerted = lastGuardianAlert != null && lastGuardianAlert >= d.receivedAt && lastGuardianAlert - d.receivedAt < 10 * 60 * 1000L
        value = LayerTraceBuilder.build(
            TraceInput(
                level = d.level, score = d.score, category = d.category,
                reasonCodes = reasons.map { it.code }.toSet(), scamWords = LayerTraceBuilder.scamWords(reasons),
                body = d.body, senderVerified = (d.sender?.uppercase() ?: "") in app.detector.verifiedSenders,
                onBlocklist = onBlocklist, reported = d.reportedAt != null, guardianAlerted = guardianAlerted, isPasted = d.sender == null,
            ),
        )
    }

    // The Report button: what may be reported, and exactly what would be sent (never the message text).
    val verified = app.detector.verifiedSenders
    val reportable = ReportableDetection(d.level, d.sender, d.category, d.score, d.fingerprint, d.modelVersion, d.receivedAt, d.reportedAt != null)
    val cannotReport = ManualReport.blockedReason(reportable, verified)
    var showReportDialog by remember { mutableStateOf(false) }
    var reportNote by remember { mutableStateOf<String?>(null) }

    if (showTakeover && d.level == "SCAM" && !d.markedSafe) {
        ScamTakeover(
            reason = reasons.firstOrNull()?.let { ReasonsJson.text(it, language) },
            onContinue = { showTakeover = false },
            onAlreadySent = { onOpenRecovery(d.id) },
            onShown = { VoiceWarnings.speakTakeover(context, reasons.firstOrNull()) },
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        VerdictCard(
            level = d.level,
            sender = d.sender ?: stringResource(R.string.sender_pasted),
            time = formatDateTime(d.receivedAt),
            body = d.body,
            fakeMpesa = d.category == "fake_mpesa",
            trace = trace,
            reasons = reasons.map { ReasonsJson.text(it, language) },
        ) {
            // One primary action: the answer to "what do I do now?". Everything else is secondary.
            LindaButton(
                stringResource(if (d.level == "SCAM") R.string.verdict_primary_scam else R.string.verdict_primary_caution),
                onClick = onBack, modifier = Modifier.fillMaxWidth(),
            )
            LindaButton(stringResource(R.string.action_i_sent_money), onClick = { onOpenRecovery(d.id) }, secondary = true, modifier = Modifier.fillMaxWidth())
            if (d.markedSafe) {
                Text(stringResource(R.string.detail_marked_safe), style = MaterialTheme.typography.bodyMedium, color = LindaTheme.colors.primary)
            } else {
                LindaButton(
                    stringResource(R.string.action_mark_safe),
                    onClick = {
                        scope.launch {
                            app.database.detectionDao().markSafe(d.id)
                            d.sender?.let { app.database.senderDao().allow(AllowedSenderEntity(it)) } // stop scoring this sender
                            refresh++
                        }
                    },
                    secondary = true, modifier = Modifier.fillMaxWidth(),
                )
            }
            // Report (F6): the person reports this number to the radar so others can be warned. Needs their confirmation each time.
            if (cannotReport == null && !d.markedSafe) {
                LindaButton(stringResource(R.string.action_report), onClick = { showReportDialog = true }, secondary = true, modifier = Modifier.fillMaxWidth())
            } else if (cannotReport == CannotReport.ALREADY_REPORTED) {
                Text(stringResource(R.string.report_done), style = MaterialTheme.typography.bodyMedium, color = LindaTheme.colors.primary)
            }
            reportNote?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = LindaTheme.colors.primary) }
        }
    }

    if (showReportDialog) {
        val payload = ManualReport.payload(reportable, Prefs.deviceId(context), verified)
        if (payload == null) {
            showReportDialog = false
        } else {
            AlertDialog(
                onDismissRequest = { showReportDialog = false },
                title = { Text(stringResource(R.string.report_dialog_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(stringResource(R.string.report_dialog_intro), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.report_field_number, payload.sender), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.report_field_type, categoryLabel(payload.category)), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.report_field_sure, (payload.confidence * 100).toInt()), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.report_field_fingerprint, payload.fingerprint), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.report_field_device, payload.deviceId), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.report_never), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        if (Prefs.serverUrl(context).isBlank()) {
                            Text(stringResource(R.string.report_no_server), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        showReportDialog = false
                        scope.launch {
                            ReportingService.enqueueManual(context, d.id, payload)
                            reportNote = context.getString(if (Prefs.serverUrl(context).isBlank()) R.string.report_saved_waiting else R.string.report_queued)
                            refresh++
                        }
                    }) { Text(stringResource(R.string.report_send)) }
                },
                dismissButton = { OutlinedButton(onClick = { showReportDialog = false }) { Text(stringResource(R.string.report_cancel)) } },
            )
        }
    }
}
