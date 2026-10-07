package com.linda.app.features.reporting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.linda.app.R
import com.linda.app.core.util.Prefs

/**
 * The consent screen (root CLAUDE.md, android/CLAUDE.integration.md): says in English AND Swahili exactly what a
 * report contains and what is never sent. Reporting stays OFF until the user taps Agree.
 */
@Composable
fun ConsentCard(onAnswered: (agreed: Boolean) -> Unit) {
    val context = LocalContext.current
    val english = remember { Prefs.contextFor(context, "en") }
    val swahili = remember { Prefs.contextFor(context, "sw") }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.consent_title), style = MaterialTheme.typography.titleMedium)
            Text(english.getString(R.string.consent_body), style = MaterialTheme.typography.bodyMedium)
            Text(swahili.getString(R.string.consent_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { onAnswered(true) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.consent_agree)) }
            OutlinedButton(onClick = { onAnswered(false) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.consent_decline)) }
        }
    }
}
