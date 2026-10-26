package com.linda.app.features.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.linda.app.R
import com.linda.app.core.ui.components.LindaButton
import com.linda.app.core.ui.components.LindaLogo
import com.linda.app.core.ui.components.LindaTextButton
import com.linda.app.core.ui.theme.LindaTheme
import com.linda.app.core.ui.theme.Motion
import com.linda.app.core.ui.theme.Spacing
import com.linda.app.core.ui.theme.rememberReduceMotion
import com.linda.app.core.util.Prefs
import com.linda.app.features.permissions.SmsAccessHelp
import com.linda.app.features.reporting.ConsentCard

private const val PAGES = 3

/**
 * First run, one decision per page (docs/design-system.md 7.14): 1. allow the permissions, 2. keep Linda awake (cheap
 * Android phones kill background apps, so each brand gets its own steps), 3. reports to Safaricom's fraud team, on or
 * off. Answering page 3 finishes. Next slides in from the right and Back returns the way it came.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var page by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = page > 0) { page-- }
    val reduceMotion = rememberReduceMotion()

    Column(Modifier.fillMaxSize().padding(Spacing.xl)) {
        PageDots(page)
        Spacer(Modifier.size(Spacing.lg))
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                if (reduceMotion) {
                    fadeIn(tween(Motion.STANDARD_MS)) togetherWith fadeOut(tween(Motion.STANDARD_MS))
                } else {
                    // Forward: the new page comes from the right. Back: it comes from the left (the way it left).
                    val forward = targetState > initialState
                    (slideInHorizontally(Motion.calmSpring()) { w -> if (forward) w else -w } + fadeIn()) togetherWith
                        (slideOutHorizontally(Motion.calmSpring()) { w -> if (forward) -w else w } + fadeOut())
                }
            },
            label = "onboarding",
            modifier = Modifier.weight(1f),
        ) { shown ->
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                when (shown) {
                    0 -> PermissionsPage(onNext = { page = 1 })
                    1 -> BatteryPage(onNext = { page = 2 })
                    else -> ConsentCard(onAnswered = { agreed ->
                        Prefs.setReportingConsent(context, agreed)
                        onDone()
                    })
                }
            }
        }
    }
}

/** Page 1: what Linda is, and the one button that asks for its permissions. Already allowed: "Next". */
@Composable
private fun PermissionsPage(onNext: () -> Unit) {
    val context = LocalContext.current
    val permissions = remember {
        buildList {
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.READ_CONTACTS)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }
    // Android 15 can refuse SMS without any pop-up for apps installed from a browser: then explain how to unlock it.
    var smsRefused by remember { mutableStateOf(false) }
    val askPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        smsRefused = result[Manifest.permission.RECEIVE_SMS] == false
        if (!smsRefused) onNext()
    }
    val allowed = remember { permissions.all { granted(context, it) } }

    LindaLogo(size = 72.dp, description = stringResource(R.string.logo_description))
    Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.titleLarge, color = LindaTheme.colors.textPrimary)
    Text(stringResource(R.string.onboarding_intro), style = MaterialTheme.typography.bodyLarge, color = LindaTheme.colors.textPrimary)
    Text(stringResource(R.string.onboarding_permissions_body), style = MaterialTheme.typography.bodyLarge, color = LindaTheme.colors.textSecondary)
    if (allowed) {
        LindaButton(stringResource(R.string.onboarding_next), onClick = onNext, modifier = Modifier.fillMaxWidth())
    } else {
        LindaButton(stringResource(R.string.onboarding_allow), onClick = { askPermissions.launch(permissions) }, modifier = Modifier.fillMaxWidth())
    }
    if (smsRefused) {
        SmsAccessHelp()
        LindaTextButton(stringResource(R.string.onboarding_next), onClick = onNext, modifier = Modifier.fillMaxWidth())
    }
}

/** Page 2: this phone's own steps to keep Linda running with the screen off. */
@Composable
private fun BatteryPage(onNext: () -> Unit) {
    val context = LocalContext.current
    Text(stringResource(R.string.onboarding_battery_title), style = MaterialTheme.typography.titleLarge, color = LindaTheme.colors.textPrimary)
    Text(stringResource(brandInstructions()), style = MaterialTheme.typography.bodyLarge, color = LindaTheme.colors.textPrimary)
    LindaButton(stringResource(R.string.onboarding_next), onClick = onNext, modifier = Modifier.fillMaxWidth())
    LindaTextButton(
        stringResource(R.string.onboarding_open_battery),
        onClick = { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Three small dots: where you are in the first run. Decorative (each page has its own numbered title). */
@Composable
private fun PageDots(page: Int) {
    val colors = LindaTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
    ) {
        repeat(PAGES) { i ->
            Box(Modifier.size(8.dp).background(if (i == page) colors.primary else colors.border, CircleShape))
        }
    }
}

private fun granted(context: Context, permission: String) =
    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

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
