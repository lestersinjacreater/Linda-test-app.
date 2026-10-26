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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.linda.app.LindaApp
import com.linda.app.R
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
import com.linda.app.features.trace.TraceInput
import com.linda.app.core.ui.components.categoryLabel
import com.linda.app.core.ui.components.categoryTitle
import com.linda.app.features.reporting.CannotReport
import com.linda.app.features.reporting.ManualReport
import com.linda.app.features.reporting.ReportableDetection
import com.linda.app.features.reporting.ReportingService
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.shadow
import com.linda.app.core.ui.components.Disclosure
import com.linda.app.core.ui.components.LindaTextButton
import com.linda.app.core.ui.theme.MediumShape
import kotlinx.coroutines.delay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.linda.app.core.ui.components.SectionCard
import com.linda.app.core.ui.components.TintBox
import com.linda.app.core.ui.components.RiskPill
import com.linda.app.core.ui.components.RiskIcon
import com.linda.app.core.ui.components.FakeMpesaBadge
import com.linda.app.core.ui.components.relativeTime
import com.linda.app.features.history.senderLabel
import com.linda.app.features.demo.DemoOverlay
import com.linda.app.features.trace.LayerTraceView
import kotlinx.coroutines.launch

/** What opens when the user taps a warning: the message, the verdict, every reason, and what to do next (F6). */
@Composable
fun DetailScreen(detectionId: Long, takeover: Boolean = false, openReport: Boolean = false, onOpenRecovery: (Long) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as LindaApp
    val language = Prefs.effectiveLanguage(context)
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableStateOf(0) }
    // The full-screen alert only when the person opened this from a warning notification, and only for a confident scam.
    // rememberSaveable, not remember: once dismissed, the stop screen must stay dismissed when Android rebuilds the
    // screen (rotation, a language change), or it pops back over the warning the person already read.
    var showTakeover by rememberSaveable(detectionId) { mutableStateOf(takeover) }
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
        val onBlocklist = d.senderMsisdn?.let { app.database.blockedNumberDao().find(com.linda.app.core.util.PhoneHash.of(it)) } != null
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
    // Opened from the notification's "Report this number": the sheet is already up, still showing what will be sent.
    var showReportDialog by rememberSaveable(detectionId) { mutableStateOf(openReport && cannotReport == null && !d.markedSafe) }
    var reportNote by remember { mutableStateOf<String?>(null) }
    var showCodes by rememberSaveable(detectionId) { mutableStateOf(false) }

    if (showTakeover && d.level == "SCAM" && !d.markedSafe) {
        ScamTakeover(
            reason = reasons.firstOrNull()?.let { ReasonsJson.text(it, language) },
            onContinue = { showTakeover = false },
            onAlreadySent = { onOpenRecovery(d.id) },
            onShown = { VoiceWarnings.speakTakeover(context, reasons.firstOrNull()) },
        )
        return
    }

    // Undo for "Mark as safe": a short bar at the bottom, instead of an "Are you sure?" dialog.
    var undo by remember { mutableStateOf<SafeMark?>(null) }
    LaunchedEffect(undo) {
        if (undo != null) {
            delay(UNDO_MS)
            undo = null
        }
    }
    val fakeMpesa = d.category == "fake_mpesa"

    val colors = LindaTheme.colors
    val shownReasons = WarningReasons.shown(reasons, fakeMpesa).map { ReasonsJson.text(it, language) }
    // The five layers, one tap away: open from the start in demo mode, so judges see them work.
    var traceOpen by rememberSaveable(detectionId) { mutableStateOf(DemoOverlay.isOn()) }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        // ---- top bar: close, and what this screen is ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        ) {
            CloseButton(stringResource(R.string.action_close), onBack)
            Text(stringResource(R.string.detail_title), style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
        }

        Box(Modifier.weight(1f)) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screenPadding),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                // 1. Overview: what kind of scam, how risky, from whom, when.
                SectionCard(stringResource(R.string.detail_overview), trailing = { RiskPill(d.level) }) {
                    OverviewRow(stringResource(R.string.detail_type), categoryTitle(d.category))
                    OverviewRow(stringResource(R.string.detail_from), senderLabel(d))
                    OverviewRow(stringResource(R.string.detail_source), stringResource(sourceLabel(d.source)))
                    OverviewRow(stringResource(R.string.detail_received), relativeTime(d.receivedAt) + " \u00B7 " + formatDateTime(d.receivedAt))
                }
                // 2. The message, exactly as it arrived. It stays on this phone.
                SectionCard(stringResource(R.string.detail_message)) {
                    TintBox(colors.surfaceRaised) {
                        Text(stringResource(R.string.detail_message_note), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                        Text(d.body, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                    }
                }
                // 3. Why Linda warned: at most three plain reasons (WarningReasons).
                SectionCard(stringResource(R.string.detail_why), mark = { RiskIcon(d.level, size = 24.dp) }) {
                    if (fakeMpesa) FakeMpesaBadge()
                    if (shownReasons.isNotEmpty()) {
                        TintBox(colors.risk(d.level).tint) {
                            shownReasons.forEach { Text(it, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary) }
                        }
                    }
                }
                // 4. What to do: one piece of plain advice for this kind of scam, and help if money already went.
                SectionCard(stringResource(R.string.detail_what_to_do)) {
                    TintBox(colors.safe.tint) {
                        Text(stringResource(adviceFor(d.category)), style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                    }
                    LindaTextButton(stringResource(R.string.action_i_sent_money), onClick = { onOpenRecovery(d.id) }, modifier = Modifier.fillMaxWidth())
                }
                // 5. How Linda decided: the Layer Trace, folded away.
                trace?.let { results ->
                    SectionCard {
                        Disclosure(stringResource(R.string.detail_how_decided), open = traceOpen, onToggle = { traceOpen = !traceOpen }) {
                            LayerTraceView(results, d.level)
                        }
                    }
                }
                reportNote?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.primary) }
                // Room for the Undo bar, so it never covers the last line.
                if (undo != null) Spacer(Modifier.height(72.dp))
            }

            undo?.let { mark ->
                UndoBar(
                    text = stringResource(R.string.marked_safe_bar),
                    onUndo = {
                        undo = null
                        scope.launch {
                            MarkAsSafe.undo(context, mark)
                            refresh++
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(Spacing.screenPadding),
                )
            }
        }

        // ---- the two actions, always in reach, and what a report sends ----
        Column(
            modifier = Modifier.fillMaxWidth().background(colors.surface).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(stringResource(R.string.report_promise), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                if (d.markedSafe) {
                    Text(stringResource(R.string.detail_marked_safe), style = MaterialTheme.typography.bodyMedium, color = colors.primary, modifier = Modifier.weight(1f))
                } else {
                    LindaButton(
                        stringResource(R.string.action_mark_safe),
                        onClick = {
                            scope.launch {
                                undo = MarkAsSafe.apply(context, d)
                                refresh++
                            }
                        },
                        secondary = true, modifier = Modifier.weight(1f),
                    )
                }
                // Report (F6): needs the person's confirmation each time, and shows what is sent first.
                if (cannotReport == null && !d.markedSafe) {
                    LindaButton(stringResource(R.string.action_report), onClick = { showReportDialog = true }, modifier = Modifier.weight(1f))
                } else if (cannotReport == CannotReport.ALREADY_REPORTED) {
                    Text(stringResource(R.string.report_done), style = MaterialTheme.typography.bodyMedium, color = colors.primary, modifier = Modifier.weight(1f))
                }
            }
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
                        Text(stringResource(R.string.report_never), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        // Still everything that is sent, but the codes are one tap away: they mean nothing to most people.
                        Disclosure(
                            stringResource(if (showCodes) R.string.report_details_hide else R.string.report_details_show),
                            open = showCodes, onToggle = { showCodes = !showCodes },
                        ) {
                            Text(stringResource(R.string.report_field_sure, (payload.confidence * 100).toInt()), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.report_field_fingerprint, payload.fingerprint), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.report_field_device, payload.deviceId), style = MaterialTheme.typography.bodyMedium)
                        }
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

private const val UNDO_MS = 8_000L

/** A short bar that floats at the bottom with one Undo button (docs/design-system.md 7.12). */
@Composable
private fun UndoBar(text: String, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LindaTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, MediumShape)
            .background(colors.surfaceRaised, MediumShape)
            .padding(start = Spacing.lg, end = Spacing.sm),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, modifier = Modifier.weight(1f))
        LindaTextButton(stringResource(R.string.action_undo), onClick = onUndo)
    }
}

/** One line of the Overview card: a grey label on the left, the value on the right. */
@Composable
private fun OverviewRow(label: String, value: String) {
    val colors = LindaTheme.colors
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

/** The "x" that closes the details, 48dp to tap. */
@Composable
private fun CloseButton(description: String, onClick: () -> Unit) {
    val line = LindaTheme.colors.textPrimary
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description },
    ) {
        Canvas(Modifier.size(18.dp)) {
            val w = size.width
            drawLine(line, Offset(0f, 0f), Offset(w, w), w * 0.13f, StrokeCap.Round)
            drawLine(line, Offset(w, 0f), Offset(0f, w), w * 0.13f, StrokeCap.Round)
        }
    }
}

/** Where the message came from, in words. */
private fun sourceLabel(source: String): Int = when (source) {
    "sms" -> R.string.source_sms
    "checker" -> R.string.source_checker
    "inbox" -> R.string.source_inbox
    "demo" -> R.string.source_demo
    else -> R.string.source_sms
}

/** One piece of plain advice per kind of scam (English and Kiswahili in strings.xml). */
private fun adviceFor(category: String): Int = when (category) {
    "fake_mpesa" -> R.string.advice_fake_mpesa
    "sent_by_mistake" -> R.string.advice_sent_by_mistake
    "prize" -> R.string.advice_prize
    "fuliza_upgrade" -> R.string.advice_fuliza_upgrade
    "kra_refund" -> R.string.advice_kra_refund
    "job_fee" -> R.string.advice_job_fee
    "loan_fee" -> R.string.advice_loan_fee
    "pin_request" -> R.string.advice_pin_request
    "phishing_link" -> R.string.advice_phishing_link
    else -> R.string.advice_other
}
