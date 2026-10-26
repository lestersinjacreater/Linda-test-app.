package com.linda.app.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.linda.app.core.ui.theme.LindaTheme
import com.linda.app.core.ui.theme.MediumShape
import com.linda.app.core.ui.theme.Spacing
import com.linda.app.core.ui.theme.green900

/**
 * A grouped list (docs/design-system.md 7.13), like a phone's own Settings: rows on one card with thin lines between
 * them, and an optional small heading above. One idea per row: a title and at most one short line under it.
 */
@Composable
fun ListGroup(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = LindaTheme.colors
    val lift = if (colors.isDark) Modifier else Modifier.shadow(
        elevation = 4.dp, shape = MediumShape, ambientColor = green900.copy(alpha = 0.10f), spotColor = green900.copy(alpha = 0.10f),
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        title?.let {
            Text(it, style = MaterialTheme.typography.labelLarge, color = colors.textSecondary, modifier = Modifier.padding(start = Spacing.lg))
        }
        Column(Modifier.fillMaxWidth().then(lift).clip(MediumShape).background(colors.surface, MediumShape), content = content)
    }
}

/**
 * One row of a [ListGroup]. [divider]: a thin line above it (every row but the first). [onClick] makes the whole row
 * a button; [chevron] adds the "opens another screen" mark; [trailing] is shown on the right (a short status word).
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    divider: Boolean = false,
    onClick: (() -> Unit)? = null,
    chevron: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    RowFrame(divider, modifier.then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)) {
        RowTexts(title, subtitle, Modifier.weight(1f))
        trailing?.invoke()
        if (chevron) Chevron()
    }
}

/** A row with a switch. The whole row flips it, not just the small switch (bigger target, one action). */
@Composable
fun ListSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    divider: Boolean = false,
) {
    RowFrame(divider, modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)) {
        RowTexts(title, subtitle, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A row that is one choice of several (a language). The whole row selects it. */
@Composable
fun ListChoiceRow(title: String, selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier, divider: Boolean = false) {
    RowFrame(divider, modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)) {
        RadioButton(selected = selected, onClick = null)
        RowTexts(title, null, Modifier.weight(1f).padding(start = Spacing.md))
    }
}

/** The short status word on the right of a row, e.g. "On". */
@Composable
fun ListRowValue(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = LindaTheme.colors.textSecondary, modifier = Modifier.padding(start = Spacing.md))
}

@Composable
private fun RowFrame(divider: Boolean, modifier: Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        if (divider) HorizontalDivider(color = LindaTheme.colors.border, modifier = Modifier.padding(start = Spacing.lg))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = Spacing.lg, vertical = Spacing.md),
            content = content,
        )
    }
}

@Composable
private fun RowTexts(title: String, subtitle: String?, modifier: Modifier) {
    val colors = LindaTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary) }
    }
}

/** ">" on the right: this row opens another screen. Decorative, so screen readers skip it. */
@Composable
private fun Chevron() {
    val line = LindaTheme.colors.textSecondary
    Canvas(Modifier.padding(start = Spacing.md).size(16.dp).clearAndSetSemantics { }) {
        val w = size.width
        val stroke = w * 0.12f
        drawLine(line, Offset(w * 0.38f, w * 0.2f), Offset(w * 0.68f, w * 0.5f), stroke, StrokeCap.Round)
        drawLine(line, Offset(w * 0.68f, w * 0.5f), Offset(w * 0.38f, w * 0.8f), stroke, StrokeCap.Round)
    }
}
