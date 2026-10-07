package com.linda.app.features.settings

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.linda.app.BuildConfig
import com.linda.app.LindaApp
import com.linda.app.R
import com.linda.app.core.util.Prefs

/** Settings. Language now; consent for reports (6c) and the hidden developer screen (6d) are added by their features. */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var language by remember { mutableStateOf(Prefs.language(context)) }
    var reporting by remember { mutableStateOf(Prefs.reportingConsent(context)) }
    val waiting by (context.applicationContext as LindaApp).database.reportQueueDao().observeCount().collectAsState(initial = 0)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.settings_language_label), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        listOf(
            "system" to R.string.settings_language_system,
            "en" to R.string.settings_language_english,
            "sw" to R.string.settings_language_swahili,
        ).forEach { (value, label) ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { select(context, value) { language = it } }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = language == value, onClick = { select(context, value) { language = it } })
                Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_reporting_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_reporting_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.settings_reports_waiting, waiting), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = reporting, onCheckedChange = { reporting = it; Prefs.setReportingConsent(context, it) })
        }
        Text(
            text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 24.dp),
        )
    }
}

private fun select(context: android.content.Context, value: String, onDone: (String) -> Unit) {
    Prefs.setLanguage(context, value)
    onDone(value)
    (context as? Activity)?.recreate() // reload every screen in the new language
}
