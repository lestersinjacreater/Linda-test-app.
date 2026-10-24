package com.linda.app.features.learning

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.linda.app.R
import com.linda.app.core.ui.components.LindaButton
import kotlinx.coroutines.launch

/**
 * Settings section "What Linda learns on this phone": what is kept, a switch, and Forget.
 * The person can always see and erase what Linda learned (privacy: nothing here ever leaves the phone).
 */
@Composable
fun LearningSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mpesaOn by remember { mutableStateOf(LearningStore.enabled(context)) }
    var note by remember { mutableStateOf<String?>(null) }
    val countFlow = remember { LearningStore.observeCount(context) }
    val remembered by countFlow.collectAsState(initial = 0)

    Text(stringResource(R.string.learning_title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.learning_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.learning_mpesa_switch), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.learning_mpesa_count, remembered),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = mpesaOn, onCheckedChange = { on ->
            mpesaOn = on
            note = null
            scope.launch {
                LearningStore.setEnabled(context, on)
                if (on) LearningStore.backfillOnce(context)
            }
        })
    }
    if (mpesaOn && !LearningStore.canReadInbox(context)) {
        Text(stringResource(R.string.learning_inbox_off), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    LindaButton(
        stringResource(R.string.learning_forget),
        onClick = { scope.launch { LearningStore.forget(context); note = context.getString(R.string.learning_forgotten) } },
        modifier = Modifier.fillMaxWidth(),
        secondary = true,
    )
    note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
}
