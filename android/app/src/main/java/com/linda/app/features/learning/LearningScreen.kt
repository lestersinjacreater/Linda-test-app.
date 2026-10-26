package com.linda.app.features.learning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.linda.app.R
import com.linda.app.core.ui.components.LindaButton
import com.linda.app.core.ui.theme.Spacing

/** "What Linda learns on this phone" on its own screen, one tap from Settings: the explanation, the switch and Forget. */
@Composable
fun LearningScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        LearningSettings()
        LindaButton(stringResource(R.string.action_back), onClick = onBack, modifier = Modifier.fillMaxWidth(), secondary = true)
    }
}
