package com.linda.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.linda.app.R
import com.linda.app.core.ui.theme.FullShape
import com.linda.app.core.ui.theme.LindaTheme
import com.linda.app.core.ui.theme.Spacing

/**
 * A pill for a risk level: icon + words + tint, never colour alone (docs/design-system.md 3.3 and 7.9).
 * [level] is the RiskLevel name ("SAFE", "CAUTION" or "SCAM").
 */
@Composable
fun LevelChip(level: String, modifier: Modifier = Modifier) {
    val style = LindaTheme.colors.risk(level)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = modifier
            .heightIn(min = 32.dp)
            .clip(FullShape)
            .background(style.tint)
            .padding(horizontal = Spacing.md),
    ) {
        RiskIcon(level, size = 16.dp)
        Text(levelLabel(level), color = style.text, style = MaterialTheme.typography.labelMedium)
    }
}

/** "Likely scam", "Be careful" or "Looks safe" in the app language. */
@Composable
fun levelLabel(level: String): String = stringResource(
    when (level) {
        "SCAM" -> R.string.level_scam
        "CAUTION" -> R.string.level_caution
        else -> R.string.level_safe
    },
)
