package com.linda.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.linda.app.core.ui.theme.LindaTheme
import com.linda.app.core.ui.theme.MediumShape
import com.linda.app.core.ui.theme.Spacing
import com.linda.app.core.ui.theme.green900

/**
 * A standard card (docs/design-system.md 7.9): surface colour, medium radius, 16dp padding.
 * Light mode gets a soft green-tinted shadow; dark mode relies on the lighter surface instead.
 */
@Composable
fun LindaCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = LindaTheme.colors
    val lift = if (colors.isDark) Modifier else Modifier.shadow(
        elevation = 4.dp, shape = MediumShape, ambientColor = green900.copy(alpha = 0.10f), spotColor = green900.copy(alpha = 0.10f),
    )
    Column(
        modifier = modifier.then(lift)
            .then(if (onClick != null) Modifier.clip(MediumShape).clickable(onClick = onClick) else Modifier)
            .background(colors.surface, MediumShape).padding(Spacing.cardPadding),
        content = content,
    )
}
