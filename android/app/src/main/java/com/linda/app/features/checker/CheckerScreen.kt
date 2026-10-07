package com.linda.app.features.checker

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.linda.app.R
import com.linda.app.core.ui.components.LevelChip
import com.linda.app.core.util.Prefs
import com.linda.app.features.detection.ReasonsJson
import com.linda.app.features.detection.RiskLevel
import com.linda.app.features.trace.LayerTraceBuilder
import com.linda.app.features.trace.LayerTraceView
import com.linda.app.features.trace.TraceInput
import com.linda.app.features.detail.VerdictCard
import com.linda.app.core.ui.theme.LindaTheme
import com.linda.app.core.util.formatDateTime
import com.linda.app.features.trace.rememberVerdictHaptic
import com.linda.app.features.sms.MessageProcessor
import com.linda.app.features.sms.ProcessResult
import kotlinx.coroutines.launch
import com.linda.app.core.ui.components.LindaButton
import com.linda.app.core.ui.components.LindaTextField

/**
 * "Is this a scam?" (F7): paste a message, or share one from WhatsApp or any app into Linda.
 * Needs no SMS permission. There is no sender, so the verdict rests on the words alone.
 */
@Composable
fun CheckerScreen(sharedText: String?) {
    val context = LocalContext.current
    val language = Prefs.effectiveLanguage(context)
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<ProcessResult?>(null) }
    var checking by remember { mutableStateOf(false) }

    fun check(body: String) {
        if (body.isBlank()) return
        checking = true
        scope.launch {
            result = MessageProcessor(context).process(body, sender = null, receivedAt = System.currentTimeMillis(), source = "checker", notify = false)
            checking = false
        }
    }

    // Text shared from another app arrives pre-filled and is checked straight away.
    LaunchedEffect(sharedText) {
        if (!sharedText.isNullOrBlank()) {
            text = sharedText
            check(sharedText)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.checker_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.checker_help), color = MaterialTheme.colorScheme.onSurfaceVariant)
        LindaTextField(
            value = text,
            onValueChange = { text = it; result = null },
            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
            label = { Text(stringResource(R.string.checker_hint)) },
        )
        LindaButton(stringResource(R.string.checker_button), onClick = { check(text) }, modifier = Modifier.fillMaxWidth(), enabled = text.isNotBlank() && !checking)

        result?.let { r ->
            val v = r.verdict
            // The five layers, lit up one by one; the phone buzzes once or twice for a Caution or Scam.
            val trace = remember(r) {
                LayerTraceBuilder.build(
                    TraceInput(
                        level = v.level.name, score = v.score, category = v.category,
                        reasonCodes = v.reasons.map { it.code }.toSet(), scamWords = LayerTraceBuilder.scamWords(v.reasons),
                        body = text, senderVerified = false, onBlocklist = false, reported = false, guardianAlerted = false, isPasted = true,
                    ),
                )
            }
            val time = remember(r) { formatDateTime(System.currentTimeMillis()) }
            VerdictCard(
                level = v.level.name,
                sender = stringResource(R.string.sender_pasted),
                time = time,
                body = text,
                fakeMpesa = v.category == "fake_mpesa",
                trace = trace,
                reasons = v.reasons.map { ReasonsJson.text(it, language) },
                onTraceLanded = rememberVerdictHaptic(v.level.name),
            ) {
                // A safe message gets a short, honest note; a warning explains itself in the reasons above.
                if (v.level == RiskLevel.SAFE) {
                    Text(stringResource(R.string.checker_safe_note), style = MaterialTheme.typography.bodyLarge, color = LindaTheme.colors.textPrimary)
                }
            }
        }
    }
}
