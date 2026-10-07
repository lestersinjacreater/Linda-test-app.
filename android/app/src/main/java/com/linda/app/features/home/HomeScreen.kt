package com.linda.app.features.home

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linda.app.R
import com.linda.app.core.ui.components.LindaButton
import com.linda.app.core.ui.components.LindaCard
import com.linda.app.core.ui.theme.LindaText
import com.linda.app.core.ui.theme.LindaTheme
import com.linda.app.core.ui.theme.ShieldNotchShape
import com.linda.app.core.ui.theme.Spacing
import com.linda.app.core.ui.theme.green900
import com.linda.app.features.demo.DemoOverlay
import com.linda.app.features.demo.TapCounter

/**
 * Home (docs/design-system.md 7.2): the five-arc shield ring, "LINDA is protecting you", and the month's count.
 * If a permission is off, the matching arc dims and one button fixes it.
 */
@Composable
fun HomeScreen(onOpenInbox: () -> Unit, onOpenRecovery: () -> Unit, viewModel: HomeViewModel = viewModel()) {
    val caught by viewModel.scamsCaughtThisMonth.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = LindaTheme.colors

    // Re-check the permissions whenever the person comes back to the app (they may have used Settings).
    var recheck by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) recheck++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val layers = remember(recheck) {
        ProtectionStatus.layers(
            canReceiveSms = granted(context, Manifest.permission.RECEIVE_SMS),
            canReadContacts = granted(context, Manifest.permission.READ_CONTACTS),
            notificationsOn = context.getSystemService(NotificationManager::class.java).areNotificationsEnabled(),
        )
    }
    val problem = ProtectionStatus.firstProblem(layers)

    val taps = remember { TapCounter() }
    var asked by remember { mutableStateOf(setOf<Problem>()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { recheck++ }
    fun fix(p: Problem) {
        val permission = when (p) {
            Problem.READ_MESSAGES -> Manifest.permission.RECEIVE_SMS
            Problem.CONTACTS -> Manifest.permission.READ_CONTACTS
            Problem.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null
        }
        // First try the normal pop-up. If it was already shown (or does not exist on this phone), go to the app's settings.
        if (permission != null && p !in asked) {
            asked = asked + p
            ask.launch(permission)
        } else {
            openAppSettings(context, p)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        // The hero card has the shield notch: its bottom-right corner is cut like the point of a shield.
        val hero = ShieldNotchShape()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (colors.isDark) Modifier else Modifier.shadow(4.dp, hero, ambientColor = green900.copy(alpha = 0.10f), spotColor = green900.copy(alpha = 0.10f)))
                .background(colors.surface, hero)
                .padding(Spacing.xl),
        ) {
            ShieldRing(layers, onLogoTap = {
                if (taps.tap(System.currentTimeMillis())) {
                    val turnOn = !DemoOverlay.isOn()
                    DemoOverlay.setEnabled(context, turnOn)
                    Toast.makeText(context, if (turnOn) R.string.overlay_toast_on else R.string.overlay_toast_off, Toast.LENGTH_SHORT).show()
                }
            })
            Spacer(Modifier.height(Spacing.lg))
            if (problem == null) {
                Text(
                    stringResource(R.string.home_status_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    stringResource(R.string.home_partial_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    stringResource(
                        when (problem) {
                            Problem.READ_MESSAGES -> R.string.home_fix_read_messages
                            Problem.CONTACTS -> R.string.home_fix_contacts
                            Problem.NOTIFICATIONS -> R.string.home_fix_notifications
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Spacing.lg))
                LindaButton(stringResource(R.string.home_fix_button), onClick = { fix(problem) })
            }
            Spacer(Modifier.height(Spacing.xl))
            Text(caught.toString(), style = LindaText.bigNumber, color = colors.primary)
            Text(
                stringResource(R.string.home_caught_this_month),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            if (caught == 0) {
                Spacer(Modifier.height(Spacing.xs))
                Text(stringResource(R.string.home_empty), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, textAlign = TextAlign.Center)
            }
        }

        LindaCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenInbox) {
            Text(stringResource(R.string.home_inbox_title), style = MaterialTheme.typography.titleMedium, color = colors.primary)
            Text(stringResource(R.string.home_inbox_body), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        }
        // Always one tap away: someone who just lost money should not have to hunt for help.
        LindaCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenRecovery) {
            Text(stringResource(R.string.home_recovery_title), style = MaterialTheme.typography.titleMedium, color = colors.primary)
            Text(stringResource(R.string.home_recovery_body), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        }
    }
}

private fun granted(context: Context, permission: String) =
    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

private fun openAppSettings(context: Context, problem: Problem) {
    val intent = if (problem == Problem.NOTIFICATIONS) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    }
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
