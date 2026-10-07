package com.linda.app.features.guardian

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.linda.app.LindaApp
import com.linda.app.R
import com.linda.app.core.util.PhoneNumbers
import com.linda.app.core.util.Prefs
import com.linda.app.core.util.formatDateTime
import kotlinx.coroutines.launch

/**
 * Family Guardian setup (F11). The protected person decides everything here: who the guardian is, what name the alert
 * uses, and whether it is on. It is OFF until they turn it on, they can see every alert that was sent, and they can
 * switch it off at any time.
 */
@Composable
fun GuardianScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as LindaApp
    val dao = app.database.guardianAlertDao()
    val scope = rememberCoroutineScope()
    val alerts by dao.observeRecent(10).collectAsState(initial = emptyList())

    var number by remember { mutableStateOf(Prefs.guardianNumber(context)) }
    var name by remember { mutableStateOf(Prefs.protectedName(context)) }
    var enabled by remember { mutableStateOf(Prefs.guardianEnabled(context)) }
    var note by remember { mutableStateOf<String?>(null) }
    var permissionGranted by remember { mutableStateOf(GuardianService.hasSmsPermission(context)) }

    val fieldsOk = PhoneNumbers.toMsisdn(number) != null && name.isNotBlank()

    fun turnOn() {
        enabled = true
        Prefs.setGuardianEnabled(context, true)
        note = null
    }

    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        if (granted) turnOn() else note = context.getString(R.string.guardian_permission_needed)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.guardian_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.guardian_intro), style = MaterialTheme.typography.bodyLarge)

        Card(
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.guardian_what_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.guardian_what_body), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.guardian_rules), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        OutlinedTextField(
            value = number, onValueChange = { number = it; Prefs.setGuardianNumber(context, it) },
            label = { Text(stringResource(R.string.guardian_number_label)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(GuardianRules.MAX_NAME_LENGTH); Prefs.setProtectedName(context, name) },
            label = { Text(stringResource(R.string.guardian_name_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.guardian_switch), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(
                checked = enabled,
                onCheckedChange = { wantOn ->
                    if (!wantOn) {
                        enabled = false
                        Prefs.setGuardianEnabled(context, false)
                        note = null
                    } else if (!fieldsOk) {
                        note = context.getString(R.string.guardian_fill_in)
                    } else if (!GuardianService.hasSmsPermission(context)) {
                        askPermission.launch(Manifest.permission.SEND_SMS) // asked only now, when the person chose to turn it on
                    } else {
                        turnOn()
                    }
                },
            )
        }
        Text(
            stringResource(if (enabled) R.string.guardian_is_on else R.string.guardian_is_off),
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (enabled && !GuardianService.hasSmsPermission(context)) {
            Text(stringResource(R.string.guardian_permission_removed), color = MaterialTheme.colorScheme.error)
        }
        note?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        OutlinedButton(
            enabled = fieldsOk && permissionGranted,
            onClick = {
                scope.launch {
                    note = context.getString(if (GuardianService.sendTest(context)) R.string.guardian_test_ok else R.string.guardian_test_failed)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.guardian_test_button)) }

        Text(stringResource(R.string.guardian_log_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        if (alerts.isEmpty()) {
            Text(stringResource(R.string.guardian_log_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            alerts.forEach { a ->
                Text(
                    stringResource(R.string.guardian_log_row, formatDateTime(a.sentAt), categoryLabel(a.category), stringResource(if (a.status == "sent") R.string.guardian_status_sent else R.string.guardian_status_failed)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (Prefs.devMode(context)) {
            OutlinedButton(onClick = { scope.launch { dao.clearAll() } }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.guardian_reset_limits))
            }
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_back)) }
    }
}

/** A scam type in plain words, in the app language. */
@Composable
private fun categoryLabel(category: String): String = stringResource(
    when (category) {
        "test" -> R.string.cat_test
        "fake_mpesa" -> R.string.cat_fake_mpesa
        "sent_by_mistake" -> R.string.cat_sent_by_mistake
        "prize" -> R.string.cat_prize
        "fuliza_upgrade" -> R.string.cat_fuliza_upgrade
        "kra_refund" -> R.string.cat_kra_refund
        "job_fee" -> R.string.cat_job_fee
        "loan_fee" -> R.string.cat_loan_fee
        "pin_request" -> R.string.cat_pin_request
        "phishing_link" -> R.string.cat_phishing_link
        else -> R.string.cat_other
    },
)
