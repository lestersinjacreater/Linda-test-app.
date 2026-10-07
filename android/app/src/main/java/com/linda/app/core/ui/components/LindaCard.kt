package com.linda.app.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.linda.app.core.ui.theme.LindaTheme
import com.linda.app.core.ui.theme.MediumShape
import com.linda.app.core.ui.theme.Motion
import com.linda.app.core.ui.theme.Spacing
import com.linda.app.core.ui.theme.green900

/**
 * A standard card (docs/design-system.md 7.9): surface colour, medium radius, 16dp padding.
 * Light mode gets a soft green-tinted shadow; dark mode relies on the lighter surface instead.
 */
@Composable
fun LindaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(Spacing.sm),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LindaTheme.colors
    // Feedback is instant on touch-down and settles with a spring (apple-design: respond on press, not on release).
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && onClick != null) 0.97f else 1f, Motion.calmSpring(), label = "cardPress")
    val lift = if (colors.isDark) Modifier else Modifier.shadow(
        elevation = 4.dp, shape = MediumShape, ambientColor = green900.copy(alpha = 0.10f), spotColor = green900.copy(alpha = 0.10f),
    )
    Column(
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale }.then(lift)
            .then(if (onClick != null) Modifier.clip(MediumShape).clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick) else Modifier)
            .background(colors.surface, MediumShape).padding(Spacing.cardPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}
