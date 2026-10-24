package com.linda.app.features.permissions

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.linda.app.R
import com.linda.app.core.ui.components.LindaButton
import com.linda.app.core.ui.components.LindaCard
import com.linda.app.core.ui.theme.LindaTheme
import com.linda.app.core.ui.theme.Spacing

/**
 * Shown wherever Linda needed SMS access and did not get it (Home, the welcome screen, Scan my inbox, Family
 * Guardian). It says, in the person's language, how to unlock it on THIS phone's Android version, and opens App info.
 */
@Composable
fun SmsAccessHelp(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = LindaTheme.colors
    val steps = SmsAccess.steps(Build.VERSION.SDK_INT)
    LindaCard(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(stringResource(R.string.sms_help_title), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        Text(stringResource(R.string.sms_help_body), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        steps.forEachIndexed { i, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("${i + 1}.", style = MaterialTheme.typography.bodyLarge, color = colors.primary)
                Text(stringResource(textFor(step)), style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            }
        }
        if (SmsAccess.Step.ALLOW_RESTRICTED in steps) {
            Text(stringResource(R.string.sms_help_no_menu), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
        LindaButton(stringResource(R.string.sms_help_button), onClick = { SmsAccess.openAppInfo(context) }, modifier = Modifier.fillMaxWidth(), secondary = true)
    }
}

private fun textFor(step: SmsAccess.Step): Int = when (step) {
    SmsAccess.Step.OPEN_APP_INFO -> R.string.sms_help_step_open
    SmsAccess.Step.ALLOW_RESTRICTED -> R.string.sms_help_step_restricted
    SmsAccess.Step.PERMISSIONS_SMS -> R.string.sms_help_step_permission
    SmsAccess.Step.COME_BACK -> R.string.sms_help_step_back
}
