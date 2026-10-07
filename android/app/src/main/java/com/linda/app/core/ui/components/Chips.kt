package com.linda.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linda.app.R
import com.linda.app.core.ui.theme.LindaColors

/** A small coloured label for a risk level: pink SCAM, amber CAUTION. [level] is the RiskLevel name. */
@Composable
fun LevelChip(level: String, modifier: Modifier = Modifier) {
    val (label, color) = when (level) {
        "SCAM" -> R.string.level_scam to LindaColors.Pink
        "CAUTION" -> R.string.level_caution to LindaColors.Amber
        else -> R.string.level_safe to LindaColors.Cyan
    }
    Text(
        text = stringResource(label),
        color = Color(0xFF0B0F1A),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}
