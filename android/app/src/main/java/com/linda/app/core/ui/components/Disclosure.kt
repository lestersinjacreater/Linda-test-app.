package com.linda.app.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.linda.app.R
import com.linda.app.core.ui.theme.LindaTheme
import com.linda.app.core.ui.theme.Motion
import com.linda.app.core.ui.theme.SmallShape
import com.linda.app.core.ui.theme.rememberReduceMotion

/**
 * A row that shows or hides extra detail (docs/design-system.md 7.11): the title in green and a small chevron that
 * turns when it opens. The common path stays on screen; the detail is one tap away. With "remove animations" on,
 * the content only fades.
 */
@Composable
fun Disclosure(
    title: String,
    open: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LindaTheme.colors
    val reduceMotion = rememberReduceMotion()
    val turn by animateFloatAsState(if (open) 180f else 0f, Motion.calmSpring(), label = "chevron")
    val state = stringResource(if (open) R.string.disclosure_shown else R.string.disclosure_hidden)
    Column(modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(SmallShape)
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = state },
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = colors.primary, modifier = Modifier.weight(1f))
            val line = colors.primary
            // The chevron says the same as the text, so screen readers skip it.
            Canvas(Modifier.size(16.dp).graphicsLayer { rotationZ = turn }.clearAndSetSemantics { }) {
                val w = size.width
                val stroke = w * 0.14f
                drawLine(line, Offset(w * 0.2f, w * 0.38f), Offset(w * 0.5f, w * 0.68f), stroke, StrokeCap.Round)
                drawLine(line, Offset(w * 0.5f, w * 0.68f), Offset(w * 0.8f, w * 0.38f), stroke, StrokeCap.Round)
            }
        }
        AnimatedVisibility(
            visible = open,
            enter = if (reduceMotion) fadeIn(tween(Motion.STANDARD_MS)) else fadeIn() + expandVertically(Motion.calmSpring()),
            exit = if (reduceMotion) fadeOut(tween(Motion.STANDARD_MS)) else fadeOut() + shrinkVertically(Motion.calmSpring()),
        ) {
            Column(content = content)
        }
    }
}
