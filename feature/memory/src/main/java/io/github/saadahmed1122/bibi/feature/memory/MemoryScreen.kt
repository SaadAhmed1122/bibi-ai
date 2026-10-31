package io.github.saadahmed1122.bibi.feature.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.saadahmed1122.bibi.core.designsystem.BibiTheme
import io.github.saadahmed1122.bibi.domain.model.MemoryItem
import io.github.saadahmed1122.bibi.domain.usecase.NeverStore
import io.github.saadahmed1122.bibi.domain.usecase.Retention

/** [onExport] opens the system "save as" picker for `bibimem.json`; the caller writes the file. */
@Composable
fun MemoryRoute(
    viewModel: MemoryViewModel,
    retention: Retention,
    onRetention: (Retention) -> Unit,
    onExport: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MemoryScreen(state, viewModel::onIntent, onBack, modifier, retention, onRetention, onExport)
}

/** What Bibi remembers (SPEC-memory.md): search, pin, edit, delete, how long to keep, export. */
@Composable
fun MemoryScreen(
    state: MemoryUiState,
    onIntent: (MemoryIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    retention: Retention = Retention.DEFAULT,
    onRetention: (Retention) -> Unit = {},
    onExport: () -> Unit = {},
) {
    var confirmDelete by remember { mutableStateOf<MemoryItem?>(null) }
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            Modifier.padding(padding).consumeWindowInsets(padding).imePadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.memory_back)) }
            Text(stringResource(R.string.memory_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.memory_intro), style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RetentionPicker(retention, onRetention, Modifier.weight(1f))
                TextButton(onClick = onExport) { Text(stringResource(R.string.memory_export)) }
            }
            Text(stringResource(R.string.memory_keep_note), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = state.query,
                onValueChange = { onIntent(MemoryIntent.Query(it)) },
                label = { Text(stringResource(R.string.memory_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.items.isEmpty()) {
                Text(
                    stringResource(if (state.query.isBlank()) R.string.memory_empty else R.string.memory_no_match),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.items, key = { it.id }) { item ->
                    val draft = state.editing?.takeIf { it.first == item.id }?.second
                    MemoryCard(item, draft, state.blocked.takeIf { draft != null }, onIntent, onDelete = { confirmDelete = item })
                }
            }
        }
    }
    confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.memory_delete_title)) },
            text = { Text(item.text) },
            confirmButton = {
                TextButton(onClick = { onIntent(MemoryIntent.Delete(item.id)); confirmDelete = null }) {
                    Text(stringResource(R.string.memory_delete))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.memory_cancel)) } },
        )
    }
}

@Composable
private fun RetentionPicker(retention: Retention, onRetention: (Retention) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        TextButton(onClick = { open = true }) {
            Text(stringResource(R.string.memory_keep_for, stringResource(retentionLabel(retention))))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Retention.entries.forEach { r ->
                DropdownMenuItem(text = { Text(stringResource(retentionLabel(r))) }, onClick = { open = false; onRetention(r) })
            }
        }
    }
}

private fun retentionLabel(r: Retention): Int = when (r) {
    Retention.WEEK -> R.string.memory_keep_week
    Retention.MONTH -> R.string.memory_keep_month
    Retention.THREE_MONTHS -> R.string.memory_keep_three_months
    Retention.YEAR -> R.string.memory_keep_year
    Retention.FOREVER -> R.string.memory_keep_forever
}

@Composable
private fun MemoryCard(
    item: MemoryItem,
    draft: String?,
    blocked: NeverStore.Reason?,
    onIntent: (MemoryIntent) -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val kind = stringResource(if (item.kind == MemoryItem.Kind.FACT) R.string.memory_kind_fact else R.string.memory_kind_summary)
            val label = if (item.pinned) stringResource(R.string.memory_pinned_label, kind) else kind
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            if (draft == null) {
                Text(item.text, style = MaterialTheme.typography.bodyLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onIntent(MemoryIntent.TogglePin(item)) }) {
                        Text(stringResource(if (item.pinned) R.string.memory_unpin else R.string.memory_pin))
                    }
                    TextButton(onClick = { onIntent(MemoryIntent.StartEdit(item)) }) { Text(stringResource(R.string.memory_edit)) }
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.memory_delete)) }
                }
            } else {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { onIntent(MemoryIntent.EditText(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    isError = blocked != null,
                    supportingText = blocked?.let { { Text(stringResource(R.string.memory_blocked)) } },
                )
                Row {
                    TextButton(onClick = { onIntent(MemoryIntent.SaveEdit) }) { Text(stringResource(R.string.memory_save)) }
                    TextButton(onClick = { onIntent(MemoryIntent.CancelEdit) }) { Text(stringResource(R.string.memory_cancel)) }
                }
            }
        }
    }
}

private val previewItems = listOf(
    MemoryItem(1, MemoryItem.Kind.FACT, "My sister is Sara", pinned = true, createdAt = 0, updatedAt = 0),
    MemoryItem(2, MemoryItem.Kind.FACT, "The gym opens at 6", pinned = false, createdAt = 0, updatedAt = 0),
    MemoryItem(3, MemoryItem.Kind.SUMMARY, "We talked about a weekend trip to Lahore.", pinned = false, createdAt = 0, updatedAt = 0),
)

@Preview
@Composable
private fun MemoryListPreview() = BibiTheme { MemoryScreen(MemoryUiState(previewItems), {}, {}) }

@Preview
@Composable
private fun MemoryEmptyPreview() = BibiTheme { MemoryScreen(MemoryUiState(), {}, {}) }

@Preview
@Composable
private fun MemoryEditBlockedPreview() = BibiTheme {
    MemoryScreen(MemoryUiState(previewItems, editing = 2L to "My bank PIN is 4821", blocked = NeverStore.Reason.PASSWORD), {}, {})
}
