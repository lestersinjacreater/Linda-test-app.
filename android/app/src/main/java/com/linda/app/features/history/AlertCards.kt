package com.linda.app.features.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.linda.app.R
import com.linda.app.core.data.DetectionEntity
import com.linda.app.core.ui.components.Pill
import com.linda.app.core.ui.components.RiskPill
import com.linda.app.core.ui.components.categoryLabel
import com.linda.app.core.ui.components.categoryTitle
import com.linda.app.core.ui.components.relativeTime
import com.linda.app.core.ui.theme.LargeShape
import com.linda.app.core.ui.theme.LindaTheme
import com.linda.app.core.ui.theme.MediumShape
import com.linda.app.core.ui.theme.Spacing
import com.linda.app.core.ui.theme.green900
import com.linda.app.core.util.PhoneNumbers

/** Who sent it, as people write it ("0700 000 777"), or "Pasted message" for text checked by hand. */
@Composable
fun senderLabel(d: DetectionEntity): String =
    d.sender?.let { PhoneNumbers.pretty(it) } ?: stringResource(R.string.sender_pasted)

/** One alert in the Alerts list (docs/design-system.md 7.16): what kind of scam, how risky, a preview, who and when. */
@Composable
fun AlertListCard(d: DetectionEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LindaTheme.colors
    val lift = if (colors.isDark) Modifier else Modifier.shadow(3.dp, LargeShape, ambientColor = green900.copy(alpha = 0.08f), spotColor = green900.copy(alpha = 0.08f))
    Column(
        modifier = modifier.fillMaxWidth().then(lift).clip(LargeShape).background(colors.surface).clickable(onClick = onClick).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(categoryTitle(d.category), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
            RiskPill(d.level)
        }
        Text(
            "“" + d.body + "”",
            style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(
                senderLabel(d) + " · " + relativeTime(d.receivedAt),
                style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.weight(1f),
            )
            StatusPill(d)
        }
    }
}

/** A short line on Home (docs/design-system.md 7.16): a coloured edge, the kind of scam and its age, a one-line preview. */
@Composable
fun RecentAlertRow(d: DetectionEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LindaTheme.colors
    val risk = colors.risk(d.level)
    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(MediumShape).background(colors.surface).clickable(onClick = onClick),
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(risk.accent))
        Column(Modifier.weight(1f).padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                categoryLabel(d.category).uppercase() + "  ·  " + relativeTime(d.receivedAt),
                style = MaterialTheme.typography.labelMedium, color = risk.text,
            )
            Text(d.body, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** What happened since: reported to Safaricom's fraud team, marked safe by the person, or just warned. */
@Composable
fun StatusPill(d: DetectionEntity) {
    val colors = LindaTheme.colors
    when {
        d.markedSafe -> Pill(stringResource(R.string.status_marked_safe), colors.surfaceRaised, colors.textSecondary)
        d.reportedAt != null -> Pill(stringResource(R.string.status_reported), colors.safe.tint, colors.safe.text)
        else -> Pill(stringResource(R.string.status_warned), colors.surfaceRaised, colors.textPrimary)
    }
}
