package com.linda.app.core.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.linda.app.core.ui.theme.LindaTheme
import com.linda.app.core.ui.theme.SmallShape
import com.linda.app.core.ui.theme.green500

/**
 * The one text field (docs/design-system.md 7.9): small radius, raised fill, a 1dp border that becomes a 2dp
 * Safaricom-green border when focused. Material keeps its own minimum height (56dp) for the touch target.
 */
@Composable
fun LindaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val colors = LindaTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        singleLine = singleLine,
        isError = isError,
        supportingText = supportingText,
        keyboardOptions = keyboardOptions,
        shape = SmallShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceRaised,
            unfocusedContainerColor = colors.surfaceRaised,
            errorContainerColor = colors.surfaceRaised,
            focusedBorderColor = green500,
            unfocusedBorderColor = colors.border,
            focusedLabelColor = colors.primary,
        ),
    )
}
