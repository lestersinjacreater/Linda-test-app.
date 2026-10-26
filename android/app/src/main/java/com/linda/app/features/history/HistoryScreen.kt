package com.linda.app.features.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linda.app.R
import com.linda.app.core.ui.components.LindaLogo
import com.linda.app.core.ui.components.Pill
import com.linda.app.core.ui.theme.LindaTheme
import com.linda.app.core.ui.theme.MediumShape
import com.linda.app.core.ui.theme.Spacing

/**
 * Alerts (was History, F9; docs/design-system.md 7.16): a header with this week's and today's counts, then one card
 * per warned message, newest first. Tapping a card opens its details.
 */
@Composable
fun HistoryScreen(onOpen: (Long) -> Unit, viewModel: HistoryViewModel = viewModel()) {
    val all by viewModel.items.collectAsStateWithLifecycle()
    val alerts = remember(all) { all.filter { it.level != "SAFE" } }
    val now = System.currentTimeMillis()
    val week = alerts.count { now - it.receivedAt < 7 * DAY_MS }
    val today = alerts.count { now - it.receivedAt < DAY_MS }
    val colors = LindaTheme.colors

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item {
            // The header sits on white with rounded bottom corners, so the cards below read as a list under it.
            Column(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                    .background(colors.surface)
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Box(Modifier.clip(MediumShape).background(colors.surfaceRaised).padding(Spacing.sm)) { LindaLogo(size = 36.dp) }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.alerts_title), style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                        Text(stringResource(R.string.alerts_subtitle), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                    }
                    Pill(pluralStringResource(R.plurals.alerts_this_week, week, week), colors.safe.tint, colors.safe.text)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.fillMaxWidth().clip(MediumShape).background(colors.background).padding(horizontal = Spacing.lg, vertical = Spacing.md),
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(colors.safe.accent))
                    Text(stringResource(R.string.protection_active), style = MaterialTheme.typography.labelLarge, color = colors.safe.text, modifier = Modifier.weight(1f))
                    Text(pluralStringResource(R.plurals.alerts_today, today, today), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                }
            }
        }
        if (alerts.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }
        } else {
            items(alerts, key = { it.id }) { d ->
                AlertListCard(d, onClick = { onOpen(d.id) }, modifier = Modifier.padding(horizontal = Spacing.lg))
            }
        }
    }
}

private const val DAY_MS = 24 * 60 * 60 * 1000L
