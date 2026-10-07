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
import com.linda.app.core.ui.components.LevelChip
import com.linda.app.core.util.Prefs
import com.linda.app.core.util.formatDateTime
import com.linda.app.features.detection.ReasonsJson
import com.linda.app.core.ui.components.categoryLabel
import com.linda.app.features.reporting.CannotReport
import com.linda.app.features.reporting.ManualReport
import com.linda.app.features.reporting.ReportableDetection
import com.linda.app.features.reporting.ReportingService
import kotlinx.coroutines.launch

/** What opens when the user taps a warning: the message, the verdict, every reason, and what to do next (F6). */
@Composable
fun DetailScreen(detectionId: Long, onOpenRecovery: (Long) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as LindaApp
    val language = Prefs.effectiveLanguage(context)
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableStateOf(0) }
    val detection: DetectionEntity? by produceState<DetectionEntity?>(null, detectionId, refresh) {
        value = app.database.detectionDao().getById(detectionId)
    }

    val d = detection
    if (d == null) {
        Text(stringResource(R.string.detail_not_found), modifier = Modifier.padding(24.dp))
        return
    }
    val reasons = ReasonsJson.decode(d.reasonsJson)

    // The Report button: what may be reported, and exactly what would be sent (never the message text).
    val verified = app.detector.verifiedSenders
    val reportable = ReportableDetection(d.level, d.sender, d.category, d.score, d.fingerprint, d.modelVersion, d.receivedAt, d.reportedAt != null)
    val cannotReport = ManualReport.blockedReason(reportable, verified)
    var showReportDialog by remember { mutableStateOf(false) }
    var reportNote by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LevelChip(d.level)
        Text(
            text = stringResource(if (d.level == "SCAM") R.string.detail_headline_scam else R.string.detail_headline_caution),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = (d.sender ?: stringResource(R.string.sender_pasted)) + " · " + formatDateTime(d.receivedAt),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Text(d.body, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
        }

        Text(stringResource(R.string.detail_why), style = MaterialTheme.typography.titleMedium)
        reasons.forEach { reason ->
            Text("• " + ReasonsJson.text(reason, language), style = MaterialTheme.typography.bodyLarge)
        }

        if (d.markedSafe) {
            Text(stringResource(R.string.detail_marked_safe), color = MaterialTheme.colorScheme.primary)
        } else {
            Button(
                onClick = {
                    scope.launch {
                        app.database.detectionDao().markSafe(d.id)
                        d.sender?.let { app.database.senderDao().allow(AllowedSenderEntity(it)) } // stop scoring this sender
                        refresh++
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_mark_safe)) }
        }
        // Report (F6): the person reports this number to the radar so others can be warned. Needs their confirmation each time.
        if (cannotReport == null && !d.markedSafe) {
            OutlinedButton(onClick = { showReportDialog = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_report)) }
        } else if (cannotReport == CannotReport.ALREADY_REPORTED) {
            Text(stringResource(R.string.report_done), color = MaterialTheme.colorScheme.primary)
        }
        reportNote?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }

        // The way into Recovery mode for someone who already acted on the scam (F6).
        Button(
            onClick = { onOpenRecovery(d.id) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) { Text(stringResource(R.string.action_i_sent_money)) }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_back)) }
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
