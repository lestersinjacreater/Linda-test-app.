package com.linda.app.features.settings

import androidx.compose.runtime.Composable
import com.linda.app.R
import com.linda.app.core.ui.components.InfoScreen

/** Placeholder. Language, voice and guardian settings arrive with F6, F11 and F12. */
@Composable
fun SettingsScreen() {
    InfoScreen(title = R.string.settings_title, message = R.string.settings_language_value)
}
