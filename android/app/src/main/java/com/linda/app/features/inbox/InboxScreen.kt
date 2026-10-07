package com.linda.app.features.inbox

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linda.app.R
import com.linda.app.core.ui.components.LevelChip
import com.linda.app.core.util.formatDateTime
import com.linda.app.core.ui.components.LindaButton
import com.linda.app.core.ui.components.LindaCard
import com.linda.app.core.ui.theme.LindaTheme

/**
 * "Scan my inbox" (F8). Reads the last 90 days of texts ON THE PHONE, checks each one like a live message, and lists
 * what looks suspicious, grouped by sender. Needs the READ_SMS permission, asked only when the person taps Scan.
 */
@Composable
fun InboxScreen(onOpenDetail: (Long) -> Unit, onBack: () -> Unit, viewModel: InboxViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var denied by remember { mutableStateOf(false) }

    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        if (granted) viewModel.start()
    }
    fun scanClicked() {
        if (context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) { denied = false; viewModel.start() }
        else askPermission.launch(Manifest.permission.READ_SMS)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.inbox_title), style = MaterialTheme.typography.titleLarge)

        when (val s = state) {
            is InboxState.Idle, is InboxState.Failed -> {
                Text(stringResource(R.string.inbox_intro), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.inbox_privacy), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (s is InboxState.Failed) Text(stringResource(R.string.inbox_failed), color = LindaTheme.colors.textPrimary)
                if (denied) Text(stringResource(R.string.inbox_permission_denied), color = LindaTheme.colors.textPrimary)
                LindaButton(stringResource(R.string.inbox_scan_button), onClick = { scanClicked() }, modifier = Modifier.fillMaxWidth())
            }

            is InboxState.Scanning -> {
                Text(stringResource(R.string.inbox_scanning), style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(
                    progress = { if (s.total == 0) 0f else s.checked.toFloat() / s.total },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.inbox_progress, s.checked, s.total), style = MaterialTheme.typography.bodyLarge)
                Text(plural(R.plurals.inbox_found_so_far, s.flagged), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                LindaButton(stringResource(R.string.inbox_cancel), onClick = { viewModel.cancel() }, modifier = Modifier.fillMaxWidth(), secondary = true)
            }

            is InboxState.Done -> {
                Text(
                    if (s.flaggedCount == 0) stringResource(R.string.inbox_none) else plural(R.plurals.inbox_found, s.flaggedCount),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (s.flaggedCount == 0) MaterialTheme.colorScheme.primary else LindaTheme.colors.scam.text,
                )
                Text(
                    stringResource(R.string.inbox_summary, s.scanned, "%.1f".format(java.util.Locale.US, s.seconds)) +
                        if (s.stoppedEarly) " " + stringResource(R.string.inbox_stopped) else "",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                s.groups.forEach { g ->
                    LindaCard(modifier = Modifier.fillMaxWidth(), onClick = { onOpenDetail(g.newestId) }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                LevelChip(g.worst.name)
                                Text(g.sender ?: stringResource(R.string.inbox_unknown_sender), style = MaterialTheme.typography.bodyLarge)
                            }
                            Text(plural(R.plurals.inbox_group_count, g.count), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            Text(g.preview, maxLines = 2, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatDateTime(g.newestDate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                }
                if (s.flaggedCount > 0) Text(stringResource(R.string.inbox_saved_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LindaButton(stringResource(R.string.inbox_again), onClick = { scanClicked() }, modifier = Modifier.fillMaxWidth())
            }
        }
        LindaButton(stringResource(R.string.action_back), onClick = onBack, modifier = Modifier.fillMaxWidth(), secondary = true)
    }
}

/** "1 message" / "5 messages", in the app language (plurals live in strings.xml). */
@Composable
private fun plural(@androidx.annotation.PluralsRes id: Int, count: Int): String =
    LocalContext.current.resources.getQuantityString(id, count, count)
