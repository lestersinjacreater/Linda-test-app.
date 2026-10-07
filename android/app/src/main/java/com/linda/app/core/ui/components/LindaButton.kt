package com.linda.app.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.linda.app.core.ui.theme.LindaTheme
import com.linda.app.core.ui.theme.MediumShape
import com.linda.app.core.ui.theme.Motion
import com.linda.app.core.ui.theme.Spacing

/**
 * The one button style (docs/design-system.md 7.9): 52dp tall, medium radius, 24dp side padding.
 * Primary is the filled deep green; [secondary] is an outline. Pressing shrinks it to 97% and darkens a primary by 8%.
 * Keep to one primary button per screen.
 */
@Composable
fun LindaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = LindaTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, Motion.calmSpring(), label = "press")
    val sized = modifier
        .heightIn(min = 52.dp)
        .graphicsLayer { scaleX = scale; scaleY = scale }
    val padding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.sm)

    if (secondary) {
        OutlinedButton(
            onClick = onClick, enabled = enabled, modifier = sized, shape = MediumShape,
            border = BorderStroke(1.dp, colors.border), contentPadding = padding, interactionSource = interaction,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
        ) { Text(text, style = MaterialTheme.typography.labelLarge) }
    } else {
        val container = if (pressed) lerp(colors.primary, Color.Black, 0.08f) else colors.primary
        Button(
            onClick = onClick, enabled = enabled, modifier = sized, shape = MediumShape,
            contentPadding = padding, interactionSource = interaction,
            colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = colors.onPrimary),
        ) { Text(text, style = MaterialTheme.typography.labelLarge) }
    }
}
