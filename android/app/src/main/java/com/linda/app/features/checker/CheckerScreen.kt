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
import com.linda.app.features.sms.MessageProcessor
import com.linda.app.features.sms.ProcessResult
import kotlinx.coroutines.launch

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
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.checker_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.checker_help), color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; result = null },
            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
            label = { Text(stringResource(R.string.checker_hint)) },
        )
        Button(onClick = { check(text) }, enabled = text.isNotBlank() && !checking, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.checker_button))
        }

        result?.let { r ->
            val v = r.verdict
            if (v.level == RiskLevel.SAFE) {
                Text(stringResource(R.string.checker_safe), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.checker_safe_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LevelChip(v.level.name)
                Text(
                    stringResource(if (v.level == RiskLevel.SCAM) R.string.detail_headline_scam else R.string.detail_headline_caution),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(stringResource(R.string.detail_why), style = MaterialTheme.typography.titleMedium)
                v.reasons.forEach { Text("• " + ReasonsJson.text(it, language), style = MaterialTheme.typography.bodyLarge) }
            }
        }
    }
}
