package com.linda.app.features.detail

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.linda.app.R
import com.linda.app.core.ui.components.LindaButton
import com.linda.app.core.ui.components.RiskIcon
import com.linda.app.core.ui.theme.LindaTheme
import com.linda.app.core.ui.theme.Spacing
import com.linda.app.features.trace.rememberVerdictHaptic

/**
 * The full-screen alert for a confident scam (docs/design-system.md 7.4), shown when the person opens the warning.
 * Big octagon, the headline, the top reason, two short buzzes. The voice (if the person turned it on) reads exactly
 * this headline and reason: [onShown] is where the screen asks for that.
 */
@Composable
fun ScamTakeover(reason: String?, onContinue: () -> Unit, onAlreadySent: () -> Unit, onShown: () -> Unit) {
    val risk = LindaTheme.colors.scam
    val buzz = rememberVerdictHaptic("SCAM")
    LaunchedEffect(Unit) {
        buzz()
        onShown()
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(risk.tint)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.xl),
    ) {
        RiskIcon("SCAM", size = 64.dp)
        Spacer(Modifier.height(Spacing.xl))
        Text(
            stringResource(R.string.takeover_headline),
            style = MaterialTheme.typography.titleLarge,
            color = risk.text,
            textAlign = TextAlign.Center,
        )
        if (!reason.isNullOrBlank()) {
            Spacer(Modifier.height(Spacing.lg))
            Text(reason, style = MaterialTheme.typography.bodyLarge, color = LindaTheme.colors.textPrimary, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(Spacing.xxl))
        LindaButton(stringResource(R.string.takeover_continue), onClick = onContinue, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.md))
        // Time matters most to someone who already acted, so this is one tap from the first screen.
        LindaButton(stringResource(R.string.action_i_sent_money), onClick = onAlreadySent, secondary = true, modifier = Modifier.fillMaxWidth())
    }
}
