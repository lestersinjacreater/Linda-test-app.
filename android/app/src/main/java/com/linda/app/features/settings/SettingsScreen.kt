package com.linda.app.features.settings

import android.app.Activity
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import android.speech.tts.TextToSpeech
import android.content.Intent
import android.content.ActivityNotFoundException
import android.app.role.RoleManager
import android.os.Build
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.coroutines.launch
import com.linda.app.core.util.Prefs
import com.linda.app.core.util.formatDateTime
import com.linda.app.features.alerts.VoiceWarnings
import com.linda.app.features.sync.BlocklistSyncWorker
import com.linda.app.core.ui.components.LindaButton
import com.linda.app.core.ui.components.LindaTextField

/**
 * Settings: language, call warnings, anonymous reports. Tapping the version number 7 times opens the hidden
 * developer section: radar server address, sync status, model version and demo mode.
 */
@Composable
fun SettingsScreen(onOpenDemo: () -> Unit, onOpenGuardian: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as LindaApp
    var language by remember { mutableStateOf(Prefs.language(context)) }
    var reporting by remember { mutableStateOf(Prefs.reportingConsent(context)) }
    var devMode by remember { mutableStateOf(Prefs.devMode(context)) }
    var voiceOn by remember { mutableStateOf(VoiceWarnings.isEnabled(context)) }
    var voiceNote by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val swahiliVoice by produceState<Boolean?>(null) { value = VoiceWarnings.swahiliVoiceAvailable(context) }
    var versionTaps by remember { mutableIntStateOf(0) }
    var serverUrl by remember { mutableStateOf(Prefs.serverUrl(context)) }
    val waiting by app.database.reportQueueDao().observeCount().collectAsState(initial = 0)
    val blocked by app.database.blockedNumberDao().observeCount().collectAsState(initial = 0)

    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val roleManager = if (Build.VERSION.SDK_INT >= 29) context.getSystemService(RoleManager::class.java) else null
    val screeningAvailable = roleManager?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) == true
    val screeningOn = screeningAvailable && roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
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
                modifier = Modifier.fillMaxWidth().clickable { selectLanguage(context, value) { language = it } }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = language == value, onClick = { selectLanguage(context, value) { language = it } })
                Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        Text(stringResource(R.string.settings_guardian_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.settings_guardian_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LindaButton(stringResource(R.string.settings_guardian_open), onClick = onOpenGuardian, modifier = Modifier.fillMaxWidth(), secondary = true)

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.voice_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.voice_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = voiceOn, onCheckedChange = { voiceOn = it; Prefs.setVoiceChoice(context, it) })
        }
        if (voiceOn && Prefs.voiceChoice(context) == null) {
            Text(stringResource(R.string.voice_on_by_guardian), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Text(
            when (swahiliVoice) {
                null -> stringResource(R.string.voice_checking)
                true -> stringResource(R.string.voice_sw_available)
                false -> stringResource(R.string.voice_sw_missing)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (swahiliVoice == false) {
            LindaButton(stringResource(R.string.voice_install), onClick = { try { context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) } catch (e: ActivityNotFoundException) { voiceNote = context.getString(R.string.voice_install_unavailable) } }, modifier = Modifier.fillMaxWidth(), secondary = true)
        }
        LindaButton(stringResource(R.string.voice_test), onClick = { scope.launch { voiceNote = context.getString(if (VoiceWarnings.speakTest(context)) R.string.voice_test_ok else R.string.voice_test_failed) } }, modifier = Modifier.fillMaxWidth(), secondary = true)
        voiceNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        Text(stringResource(R.string.settings_calls_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.settings_calls_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when {
            !screeningAvailable -> Text(stringResource(R.string.settings_calls_unavailable), style = MaterialTheme.typography.bodyMedium)
            screeningOn -> Text(stringResource(R.string.settings_calls_on), color = MaterialTheme.colorScheme.primary)
            else -> LindaButton(stringResource(R.string.settings_calls_enable), onClick = { roleLauncher.launch(roleManager!!.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)) }, modifier = Modifier.fillMaxWidth())
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_reporting_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_reporting_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.settings_reports_waiting, waiting), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = reporting, onCheckedChange = { reporting = it; Prefs.setReportingConsent(context, it) })
        }

        // Tap the version 7 times to open or close the developer section.
        Text(
            text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 24.dp).clickable {
                versionTaps++
                if (versionTaps >= 7) {
                    versionTaps = 0
                    devMode = !devMode
                    Prefs.setDevMode(context, devMode)
                }
            },
        )

        if (devMode) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Text(stringResource(R.string.dev_title), style = MaterialTheme.typography.titleMedium)
            LindaTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text(stringResource(R.string.dev_server)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            LindaButton(stringResource(R.string.dev_save_sync), onClick = { Prefs.setServerUrl(context, serverUrl); Prefs.clearLastSync(context); BlocklistSyncWorker.syncNow(context) }, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.dev_model, app.detector.modelVersion), style = MaterialTheme.typography.bodyMedium)
            val syncedAt = Prefs.lastSyncAt(context)
            Text(
                stringResource(R.string.dev_last_sync, if (syncedAt == 0L) stringResource(R.string.dev_never) else formatDateTime(syncedAt)),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(stringResource(R.string.dev_blocked, blocked), style = MaterialTheme.typography.bodyMedium)
            LindaButton(stringResource(R.string.dev_open_demo), onClick = onOpenDemo, modifier = Modifier.fillMaxWidth(), secondary = true)
        }
    }
}

private fun selectLanguage(context: android.content.Context, value: String, onDone: (String) -> Unit) {
    Prefs.setLanguage(context, value)
    onDone(value)
    (context as? Activity)?.recreate() // reload every screen in the new language
}
