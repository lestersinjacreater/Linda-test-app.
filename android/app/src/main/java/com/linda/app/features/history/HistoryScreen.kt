package com.linda.app.features.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linda.app.R
import com.linda.app.core.ui.components.LevelChip
import com.linda.app.core.util.formatDateTime

/** Searchable list of flagged messages with a level filter (F9). Tapping one opens its detail screen. */
@Composable
fun HistoryScreen(onOpen: (Long) -> Unit, viewModel: HistoryViewModel = viewModel()) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val level by viewModel.levelFilter.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.history_title), style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.query.value = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.history_search)) },
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = level == "ALL", onClick = { viewModel.levelFilter.value = "ALL" }, label = { Text(stringResource(R.string.filter_all)) })
            FilterChip(selected = level == "SCAM", onClick = { viewModel.levelFilter.value = "SCAM" }, label = { Text(stringResource(R.string.level_scam)) })
            FilterChip(selected = level == "CAUTION", onClick = { viewModel.levelFilter.value = "CAUTION" }, label = { Text(stringResource(R.string.level_caution)) })
        }
        if (items.isEmpty()) {
            Text(
                stringResource(R.string.history_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items, key = { it.id }) { d ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(d.id) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                LevelChip(d.level)
                                Text(
                                    text = d.sender ?: stringResource(R.string.sender_pasted),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                            Text(d.body, maxLines = 2, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatDateTime(d.receivedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
