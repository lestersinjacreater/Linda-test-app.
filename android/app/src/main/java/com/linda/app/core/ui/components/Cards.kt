package com.linda.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.linda.app.R
import com.linda.app.core.ui.theme.FullShape
import com.linda.app.core.ui.theme.LargeShape
import com.linda.app.core.ui.theme.LindaTheme
import com.linda.app.core.ui.theme.MediumShape
import com.linda.app.core.ui.theme.Spacing
import com.linda.app.core.ui.theme.green900
import com.linda.app.core.util.RelativeTime

/**
 * The section card (docs/design-system.md 7.15): a white card with large corners and a soft shadow that answers one
 * question ("Why Linda warned you"), with an optional small mark before its title. Cards stack with 16dp between them.
 */
@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    mark: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LindaTheme.colors
    val lift = if (colors.isDark) Modifier else Modifier.shadow(
        elevation = 3.dp, shape = LargeShape, ambientColor = green900.copy(alpha = 0.08f), spotColor = green900.copy(alpha = 0.08f),
    )
    Column(
        modifier = modifier.fillMaxWidth().then(lift).clip(LargeShape).background(colors.surface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (title != null || trailing != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                mark?.invoke()
                if (title != null) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
                }
                trailing?.invoke()
            }
        }
        content()
    }
}

/** A tinted box inside a card, for the one thing the card is about (the message, the reasons, the advice). */
@Composable
fun TintBox(tint: Color, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth().clip(MediumShape).background(tint).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        content = content,
    )
}

/** A small rounded label: a risk level ("Likely scam") or a state ("Reported"). Never colour alone: always words. */
@Composable
fun Pill(text: String, background: Color, foreground: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = foreground,
        modifier = modifier.clip(FullShape).background(background).padding(horizontal = Spacing.md, vertical = 6.dp),
    )
}

/** The risk pill for a level: "Likely scam" on red tint, "Be careful" on amber tint, "Looks safe" on mint. */
@Composable
fun RiskPill(level: String, modifier: Modifier = Modifier) {
    val style = LindaTheme.colors.risk(level)
    Pill(levelLabel(level), style.tint, style.text, modifier)
}

/** "just now", "6 min ago", "3 hr ago", "2 days ago", in the app's language. */
@Composable
fun relativeTime(millis: Long, now: Long = System.currentTimeMillis()): String {
    val (unit, n) = RelativeTime.of(millis, now)
    return when (unit) {
        RelativeTime.Unit.NOW -> stringResource(R.string.time_now)
        RelativeTime.Unit.MINUTES -> pluralStringResource(R.plurals.time_minutes, n, n)
        RelativeTime.Unit.HOURS -> pluralStringResource(R.plurals.time_hours, n, n)
        RelativeTime.Unit.DAYS -> pluralStringResource(R.plurals.time_days, n, n)
    }
}
