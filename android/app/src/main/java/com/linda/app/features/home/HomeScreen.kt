package com.linda.app.features.home

import android.Manifest
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.Icon
import com.linda.app.R
import com.linda.app.core.ui.components.LindaButton
import com.linda.app.core.ui.components.LindaLogo
import com.linda.app.core.ui.components.LindaTextButton
import com.linda.app.core.ui.components.ListGroup
import com.linda.app.core.ui.components.ListRow
import com.linda.app.core.ui.components.Pill
import com.linda.app.core.ui.components.SectionCard
import com.linda.app.core.ui.components.TintBox
import com.linda.app.core.ui.theme.FullShape
import com.linda.app.core.ui.theme.LindaText
import com.linda.app.core.ui.theme.LindaTheme
import com.linda.app.core.ui.theme.Spacing
import com.linda.app.core.util.Prefs
import com.linda.app.features.demo.DemoOverlay
import com.linda.app.features.demo.TapCounter
import com.linda.app.features.demo.DemoCallCard
import com.linda.app.features.demo.DemoOutcome
import com.linda.app.features.demo.DemoRunner
import com.linda.app.features.demo.DemoSheet
import com.linda.app.features.demo.DemoStep
import kotlinx.coroutines.launch
import com.linda.app.features.history.RecentAlertRow
import com.linda.app.features.permissions.SmsAccessHelp
import com.linda.app.features.trace.Layer
import java.util.Calendar

/**
 * Home (docs/design-system.md 7.2): a greeting with the protection pill and the Settings gear; the Protection Center
 * (overall state, what is on, this month's count, one button when something needs fixing); today's scam tip; the
 * newest alerts; and two rows, "Scan my inbox" and "I have been scammed". Tap the logo 7 times for demo mode.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    onOpenInbox: () -> Unit,
    onOpenRecovery: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAlerts: () -> Unit,
    onOpenAlert: (Long) -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val caught by viewModel.scamsCaughtThisMonth.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = LindaTheme.colors

    // Re-check what is switched on whenever the person comes back to the app (they may have used Settings).
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
    val callsOn = remember(recheck) { callScreeningOn(context) }
    val reportsOn = remember(recheck) { Prefs.reportingConsent(context) }

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

    // The demo sheet and what it produced (a short note, or the call card when "Display over other apps" is off). The
    // steps run in this screen's scope, so they finish even though the sheet has already closed.
    var demoOpen by remember { mutableStateOf(false) }
    var demoCall by remember { mutableStateOf<DemoOutcome.CallCard?>(null) }
    val demoScope = rememberCoroutineScope()
    if (demoOpen) {
        DemoSheet(onDismiss = { demoOpen = false }, onPick = { step ->
            if (step is DemoStep.Help) onOpenRecovery() else demoScope.launch {
                when (val outcome = DemoRunner.run(context, step)) {
                    is DemoOutcome.Quiet -> Toast.makeText(context, outcome.message, Toast.LENGTH_LONG).show()
                    is DemoOutcome.CallCard -> demoCall = outcome
                    DemoOutcome.Shown -> Unit
                }
            }
        })
    }
    demoCall?.let { DemoCallCard(it.msisdn, it.label) { demoCall = null } }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        // ---- the greeting: logo (7 taps = demo mode), hello, the protection pill, and the gear for Settings ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .background(colors.surface)
                .padding(Spacing.lg),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).clickable {
                    if (taps.tap(System.currentTimeMillis())) {
                        val turnOn = !DemoOverlay.isOn()
                        DemoOverlay.setEnabled(context, turnOn)
                        Toast.makeText(context, if (turnOn) R.string.overlay_toast_on else R.string.overlay_toast_off, Toast.LENGTH_SHORT).show()
                    }
                },
            ) { LindaLogo(size = 52.dp, description = stringResource(R.string.logo_description)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(greeting()), style = MaterialTheme.typography.titleLarge, color = colors.primary)
                Text(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    if (problem == null) {
                        Pill("●  " + stringResource(R.string.protection_active), colors.safe.tint, colors.safe.text)
                    } else {
                        Pill("●  " + stringResource(R.string.protection_attention), colors.caution.tint, colors.caution.text)
                    }
                    // Demo mode for anyone trying Linda (hackathon): numbered steps that run the real detector.
                    Text(
                        "▶  " + stringResource(R.string.demo_chip),
                        style = MaterialTheme.typography.labelMedium, color = colors.primary,
                        modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, colors.border, RoundedCornerShape(50))
                            .clickable(role = Role.Button) { demoOpen = true }
                            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    )
                }
            }
            val settings = stringResource(R.string.nav_settings)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(48.dp).clip(CircleShape).background(colors.background)
                    .clickable(role = Role.Button, onClick = onOpenSettings)
                    .semantics { contentDescription = settings },
            ) {
                Icon(painterResource(R.drawable.ic_nav_settings), contentDescription = null, tint = colors.primary)
            }
        }

        Column(Modifier.padding(horizontal = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            // ---- Protection Center ----
            SectionCard(stringResource(R.string.protection_center), mark = { ShieldMark(colors.primary) }) {
                val tint = if (problem == null) colors.safe else colors.caution
                TintBox(tint.tint) {
                    Text(stringResource(R.string.protection_overall), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                    Text(
                        (if (problem == null) "✓  " else "!  ") +
                            stringResource(if (problem == null) R.string.protection_excellent else R.string.protection_attention),
                        style = MaterialTheme.typography.headlineLarge, color = tint.text,
                    )
                }
                if (problem == null) {
                    Text(stringResource(R.string.home_status_title), style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                } else {
                    Text(
                        stringResource(
                            when (problem) {
                                Problem.READ_MESSAGES -> R.string.home_fix_read_messages
                                Problem.CONTACTS -> R.string.home_fix_contacts
                                Problem.NOTIFICATIONS -> R.string.home_fix_notifications
                            },
                        ),
                        style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary,
                    )
                    LindaButton(stringResource(R.string.home_fix_button), onClick = { fix(problem) }, modifier = Modifier.fillMaxWidth())
                    if (problem == Problem.READ_MESSAGES && Problem.READ_MESSAGES in asked) SmsAccessHelp()
                }
                // What is on, as chips: green when on, an outline when off (a tap fixes it or opens Settings).
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    val smsOn = layers.first { it.layer == Layer.LANGUAGE }.ok && layers.first { it.layer == Layer.ALERT }.ok
                    StatusChip(stringResource(R.string.chip_sms), smsOn) { problem?.let { fix(it) } ?: onOpenSettings() }
                    StatusChip(stringResource(R.string.chip_calls), callsOn, onOpenSettings)
                    StatusChip(stringResource(R.string.chip_reports), reportsOn, onOpenSettings)
                }
                // This month, in one line: the count once there is one, otherwise the calm "watching" line.
                if (caught > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Text(caught.toString(), style = LindaText.bigNumber, color = colors.primary)
                        Text(stringResource(R.string.home_caught_this_month), style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary)
                    }
                } else {
                    Text(stringResource(R.string.home_empty), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                }
            }

            // ---- today's scam tip ----
            val tip = remember { ScamTips.forDay(System.currentTimeMillis() / DAY_MS) }
            SectionCard(stringResource(tip.title), mark = { TipMark(colors.primary) }) {
                Text(stringResource(tip.body), style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            }

            // ---- the newest alerts ----
            if (recent.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.recent_alerts), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
                    LindaTextButton(stringResource(R.string.see_all), onClick = onOpenAlerts)
                }
                recent.forEach { d -> RecentAlertRow(d, onClick = { onOpenAlert(d.id) }) }
            }

            // ---- two rows: scan the old messages, and help for someone who just lost money (always one tap away) ----
            ListGroup {
                ListRow(
                    stringResource(R.string.home_inbox_title), subtitle = stringResource(R.string.home_inbox_body),
                    onClick = onOpenInbox, chevron = true,
                )
                ListRow(
                    stringResource(R.string.home_recovery_title), subtitle = stringResource(R.string.home_recovery_body),
                    onClick = onOpenRecovery, chevron = true, divider = true,
                )
            }
            Spacer(Modifier.size(Spacing.sm))
        }
    }
}

/** A rounded chip with a tick (on) or an outline (off): "SMS protection", "Call protection", "Reports". */
@Composable
private fun StatusChip(text: String, on: Boolean, onClick: () -> Unit) {
    val colors = LindaTheme.colors
    val shape = FullShape
    val base = Modifier.heightIn(min = 40.dp).clip(shape).clickable(role = Role.Button, onClick = onClick)
    val look = if (on) base.background(colors.safe.tint) else base.border(1.dp, colors.textSecondary, shape)
    Text(
        (if (on) "✓  " else "–  ") + text,
        style = MaterialTheme.typography.labelLarge,
        color = if (on) colors.safe.text else colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = look.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    )
}

/** A small shield outline before "Protection Center". Decorative. */
@Composable
private fun ShieldMark(color: Color) {
    Canvas(Modifier.size(24.dp).clearAndSetSemantics { }) {
        val w = size.width
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.5f, w * 0.08f)
            lineTo(w * 0.88f, w * 0.22f)
            lineTo(w * 0.84f, w * 0.58f)
            quadraticTo(w * 0.72f, w * 0.82f, w * 0.5f, w * 0.94f)
            quadraticTo(w * 0.28f, w * 0.82f, w * 0.16f, w * 0.58f)
            lineTo(w * 0.12f, w * 0.22f)
            close()
        }
        drawPath(p, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.1f))
    }
}

/** An "i" in a circle before the tip's title. Decorative. */
@Composable
private fun TipMark(color: Color) {
    Canvas(Modifier.size(24.dp).clearAndSetSemantics { }) {
        val w = size.width
        drawCircle(color, radius = w / 2, style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.09f))
        drawCircle(color, radius = w * 0.06f, center = Offset(w / 2, w * 0.3f))
        drawLine(color, Offset(w / 2, w * 0.45f), Offset(w / 2, w * 0.72f), w * 0.1f, StrokeCap.Round)
    }
}

/** "Good morning / afternoon / evening" by the phone's clock. */
private fun greeting(): Int = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> R.string.greet_morning
    in 12..16 -> R.string.greet_afternoon
    else -> R.string.greet_evening
}

private const val DAY_MS = 24 * 60 * 60 * 1000L

private fun granted(context: Context, permission: String) =
    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

/** True when Linda holds Android's call-screening role (Android 10+). */
private fun callScreeningOn(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 29) return false
    val roles = context.getSystemService(RoleManager::class.java) ?: return false
    return roles.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && roles.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
}

private fun openAppSettings(context: Context, problem: Problem) {
    val intent = if (problem == Problem.NOTIFICATIONS) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    }
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
