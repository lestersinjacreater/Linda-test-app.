package com.linda.app.features.onboarding

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.linda.app.R
import com.linda.app.core.util.Prefs
import com.linda.app.features.reporting.ConsentCard

/**
 * First run: ask for the permissions Linda needs, and explain how to stop the phone from putting
 * Linda to sleep. Cheap Android phones kill background apps aggressively, so each brand gets its own steps.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val permissions = buildList {
        add(Manifest.permission.RECEIVE_SMS)
        add(Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()
    val askPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.onboarding_intro), style = MaterialTheme.typography.bodyLarge)

        Text(stringResource(R.string.onboarding_permissions_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.onboarding_permissions_body), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = { askPermissions.launch(permissions) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_allow))
        }

        Text(stringResource(R.string.onboarding_battery_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(brandInstructions()), style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(
            onClick = { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.onboarding_open_battery)) }

        ConsentCard(onAnswered = { agreed -> Prefs.setReportingConsent(context, agreed) })

        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(stringResource(R.string.onboarding_done))
        }
    }
}

/** Steps for this phone's brand (Tecno, Infinix, Itel, Samsung, Xiaomi), or a generic version. */
private fun brandInstructions(): Int {
    val maker = Build.MANUFACTURER.lowercase()
    return when {
        "tecno" in maker -> R.string.battery_tecno
        "infinix" in maker -> R.string.battery_infinix
        "itel" in maker -> R.string.battery_itel
        "samsung" in maker -> R.string.battery_samsung
        "xiaomi" in maker || "redmi" in maker || "poco" in maker -> R.string.battery_xiaomi
        else -> R.string.battery_generic
    }
}
