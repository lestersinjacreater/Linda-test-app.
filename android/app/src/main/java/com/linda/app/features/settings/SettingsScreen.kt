package com.linda.app.features.settings

import android.app.Activity
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.speech.tts.TextToSpeech
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.linda.app.BuildConfig
import com.linda.app.LindaApp
import com.linda.app.R
import com.linda.app.core.ui.components.LindaButton
import com.linda.app.core.ui.components.LindaTextField
import com.linda.app.core.ui.components.ListChoiceRow
import com.linda.app.core.ui.components.ListGroup
import com.linda.app.core.ui.components.ListRow
import com.linda.app.core.ui.components.ListRowValue
import com.linda.app.core.ui.components.ListSwitchRow
import com.linda.app.core.ui.theme.LindaTheme
import com.linda.app.core.ui.theme.Spacing
import com.linda.app.core.util.Prefs
import com.linda.app.core.util.formatDateTime
import com.linda.app.features.alerts.VoiceWarnings
import com.linda.app.features.demo.DemoOverlay
import com.linda.app.features.sync.BlocklistSyncWorker
import kotlinx.coroutines.launch

/**
 * Settings as a grouped list (docs/design-system.md 7.13): language, then protection (calls, voice, reports), then
 * family and privacy (each opens its own screen). One short line per row. Tapping the version number 7 times opens the
 * hidden developer section: server address, sync status, model version, demo mode, and the voice test.
 */
@Composable
fun SettingsScreen(onOpenDemo: () -> Unit, onOpenGuardian: () -> Unit, onOpenLearning: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as LindaApp
    val colors = LindaTheme.colors
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

    // Asked again when the person comes back from Android's "call screening app" dialog.
    var roleCheck by remember { mutableIntStateOf(0) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { roleCheck++ }
    val roleManager = if (Build.VERSION.SDK_INT >= 29) context.getSystemService(RoleManager::class.java) else null
    val screeningAvailable = roleManager?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) == true
    val screeningOn = remember(roleCheck) { screeningAvailable && roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)

        ListGroup(stringResource(R.string.settings_language_label)) {
            listOf(
                "system" to R.string.settings_language_system,
                "en" to R.string.settings_language_english,
                "sw" to R.string.settings_language_swahili,
            ).forEachIndexed { i, (value, label) ->
                ListChoiceRow(
                    stringResource(label), selected = language == value, divider = i > 0,
                    onSelect = { selectLanguage(context, value) { language = it } },
                )
            }
        }

        ListGroup(stringResource(R.string.settings_section_protection)) {
            // Calls: on, off (one tap asks Android), or not possible on this phone.
            when {
                !screeningAvailable -> ListRow(stringResource(R.string.settings_calls_title), subtitle = stringResource(R.string.settings_calls_unavailable))
                screeningOn -> ListRow(
                    stringResource(R.string.settings_calls_title), subtitle = stringResource(R.string.settings_calls_row),
                    trailing = { ListRowValue(stringResource(R.string.settings_on)) },
                )
                else -> ListRow(
                    stringResource(R.string.settings_calls_title), subtitle = stringResource(R.string.settings_calls_row),
                    onClick = { roleLauncher.launch(roleManager!!.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)) },
                    trailing = { Text(stringResource(R.string.settings_turn_on), style = MaterialTheme.typography.labelLarge, color = colors.primary) },
                )
            }
            ListSwitchRow(
                stringResource(R.string.voice_title), checked = voiceOn, divider = true,
                subtitle = stringResource(if (voiceOn && Prefs.voiceChoice(context) == null) R.string.voice_on_by_guardian else R.string.voice_row),
                onCheckedChange = { voiceOn = it; Prefs.setVoiceChoice(context, it) },
            )
            // Only say something about the Kiswahili voice when it is missing: then one tap installs it.
            if (voiceOn && swahiliVoice == false) {
                ListRow(
                    stringResource(R.string.voice_sw_missing_short), divider = true, chevron = true,
                    onClick = {
                        try {
                            context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
                        } catch (e: ActivityNotFoundException) {
                            voiceNote = context.getString(R.string.voice_install_unavailable)
                        }
                    },
                )
            }
            ListSwitchRow(
                stringResource(R.string.settings_reporting_title), checked = reporting, divider = true,
                subtitle = stringResource(R.string.settings_reporting_body),
                onCheckedChange = { reporting = it; Prefs.setReportingConsent(context, it) },
            )
        }
        voiceNote?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.primary) }

        ListGroup(stringResource(R.string.settings_section_family)) {
            ListRow(
                stringResource(R.string.settings_guardian_title), subtitle = stringResource(R.string.settings_guardian_body),
                onClick = onOpenGuardian, chevron = true,
            )
            ListRow(
                stringResource(R.string.learning_title), subtitle = stringResource(R.string.learning_row),
                onClick = onOpenLearning, chevron = true, divider = true,
            )
        }

        // Tap the version 7 times to open or close the developer section.
        Text(
            text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.align(Alignment.CenterHorizontally).clickable {
                versionTaps++
                if (versionTaps >= 7) {
                    versionTaps = 0
                    devMode = !devMode
                    Prefs.setDevMode(context, devMode)
                }
            }.padding(Spacing.md),
        )

        if (devMode) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
                Text(stringResource(R.string.settings_reports_waiting, waiting), style = MaterialTheme.typography.bodyMedium)
                Text(
                    when (swahiliVoice) {
                        null -> stringResource(R.string.voice_checking)
                        true -> stringResource(R.string.voice_sw_available)
                        false -> stringResource(R.string.voice_sw_missing)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                LindaButton(
                    stringResource(R.string.voice_test),
                    onClick = {
                        scope.launch {
                            val ok = VoiceWarnings.speakTest(context)
                            voiceNote = context.getString(if (ok) R.string.voice_test_ok else R.string.voice_test_failed)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(), secondary = true,
                )
                val overlayOn by DemoOverlay.enabled.collectAsState()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.dev_demo_overlay), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = overlayOn, onCheckedChange = { DemoOverlay.setEnabled(context, it) })
                }
                LindaButton(stringResource(R.string.dev_open_demo), onClick = onOpenDemo, modifier = Modifier.fillMaxWidth(), secondary = true)
            }
        }
    }
}

private fun selectLanguage(context: android.content.Context, value: String, onDone: (String) -> Unit) {
    Prefs.setLanguage(context, value)
    onDone(value)
    (context as? Activity)?.recreate() // reload every screen in the new language
}
