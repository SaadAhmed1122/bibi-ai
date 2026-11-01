package io.github.saadahmed1122.bibi.feature.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.saadahmed1122.bibi.core.designsystem.EmptyState
import io.github.saadahmed1122.bibi.core.designsystem.SecondaryButton
import io.github.saadahmed1122.bibi.core.designsystem.Space
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
    Scaffold(modifier = modifier.fillMaxSize(), containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            // One list: the header scrolls away, so landscape keeps room for the memories.
            LazyColumn(
                Modifier.widthIn(max = Space.readable).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Space.gutter, vertical = Space.l),
                verticalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                item(key = "header") {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                        Text(stringResource(R.string.memory_title), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.memory_intro), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                            RetentionPicker(retention, onRetention, Modifier.weight(1f))
                            SecondaryButton(stringResource(R.string.memory_export), onClick = onExport)
                        }
                        Text(stringResource(R.string.memory_keep_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextField(
                            value = state.query,
                            onValueChange = { onIntent(MemoryIntent.Query(it)) },
                            placeholder = { Text(stringResource(R.string.memory_search)) },
                            singleLine = true,
                            shape = CircleShape,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (state.items.isEmpty()) {
                    item(key = "empty") { EmptyState(stringResource(if (state.query.isBlank()) R.string.memory_empty else R.string.memory_no_match)) }
                }
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
        TextButton(onClick = { open = true }, modifier = Modifier.heightIn(min = Space.touch)) {
            Text(stringResource(R.string.memory_keep_for, stringResource(retentionLabel(retention))), style = MaterialTheme.typography.titleSmall)
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
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = Space.l, end = Space.s, top = Space.m, bottom = Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
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

@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MemoryListDarkPreview() = BibiTheme { MemoryScreen(MemoryUiState(previewItems), {}, {}) }

@Preview(fontScale = 2f)
@Composable
private fun MemoryEmptyPreview() = BibiTheme { MemoryScreen(MemoryUiState(), {}, {}) }

@Preview
@Composable
private fun MemoryEditBlockedPreview() = BibiTheme {
    MemoryScreen(MemoryUiState(previewItems, editing = 2L to "My bank PIN is 4821", blocked = NeverStore.Reason.PASSWORD), {}, {})
}
