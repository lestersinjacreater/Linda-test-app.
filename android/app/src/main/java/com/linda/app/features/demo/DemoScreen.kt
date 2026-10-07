package com.linda.app.features.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.linda.app.features.calls.CallWarning
import com.linda.app.features.calls.CallWarnings
import com.linda.app.features.detection.RiskLevel
import com.linda.app.features.sms.MessageProcessor
import kotlinx.coroutines.launch

/**
 * Demo mode: the presenter "receives" any sample instantly, with no network, through the exact same
 * pipeline as a real SMS (so the notification, history and call warning are all real). Reports are never
 * sent for demo messages.
 */
@Composable
fun DemoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.demo_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.demo_help), color = MaterialTheme.colorScheme.onSurfaceVariant)

        DemoSamples.all.forEach { sample ->
            val label = stringResource(sample.title)
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        val result = MessageProcessor(context).process(sample.body, sample.sender, System.currentTimeMillis(), source = "demo", notify = true)
                        val v = result.verdict
                        status = if (v.level == RiskLevel.SAFE) context.getString(R.string.demo_result_safe, label)
                        else context.getString(R.string.demo_result_flagged, label, v.level.name, v.category)
                    }
                },
            ) { Text(label) }
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    val warning = CallWarnings.check(context, DemoSamples.SCAMMER_NUMBER)
                    status = when (warning) {
                        is CallWarning.RecentScamMessage -> context.getString(R.string.demo_call_recent, warning.minutesAgo)
                        is CallWarning.ReportedNumber -> context.getString(R.string.demo_call_reported)
                        null -> context.getString(R.string.demo_call_none)
                    }
                }
            },
        ) { Text(stringResource(R.string.demo_call)) }

        status?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_back)) }
    }
}
