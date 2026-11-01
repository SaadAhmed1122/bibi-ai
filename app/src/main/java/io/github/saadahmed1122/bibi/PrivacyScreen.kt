package io.github.saadahmed1122.bibi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.saadahmed1122.bibi.core.designsystem.BibiTheme

/**
 * The Privacy dashboard (SPEC-privacy.md): what Bibi keeps, for how long, and deleting it all.
 * [stored] is null while it is being counted.
 */
@Composable
fun PrivacyScreen(
    stored: StoredData?,
    retentionLabel: String,
    onOpenMemory: () -> Unit,
    onDeleteEverything: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirm by remember { mutableStateOf(false) }
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.privacy_offline), style = MaterialTheme.typography.bodyLarge)
            HorizontalDivider()
            Text(stringResource(R.string.privacy_stored), style = MaterialTheme.typography.titleMedium)
            if (stored == null) {
                Text(stringResource(R.string.privacy_counting))
            } else {
                Text(stringResource(R.string.privacy_messages, stored.messages))
                Text(stringResource(R.string.privacy_memories, stored.memories, retentionLabel))
                Text(stringResource(R.string.privacy_action_log, kb(stored.actionLogBytes)))
                Text(stringResource(R.string.privacy_crash_log, kb(stored.crashLogBytes)))
            }
            TextButton(onClick = onOpenMemory) { Text(stringResource(R.string.privacy_open_memory)) }
            HorizontalDivider()
            Text(stringResource(R.string.privacy_delete_explain), style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = { confirm = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.privacy_delete)) }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.privacy_delete_title)) },
            text = { Text(stringResource(R.string.privacy_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirm = false; onDeleteEverything() }) { Text(stringResource(R.string.privacy_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.privacy_cancel)) } },
        )
    }
}

private fun kb(bytes: Long): Long = (bytes + 1023) / 1024

@Preview
@Composable
private fun PrivacyPreview() = BibiTheme {
    PrivacyScreen(StoredData(211, 4, 2_300, 9_100), "3 months", {}, {}, {})
}

@Preview
@Composable
private fun PrivacyCountingPreview() = BibiTheme { PrivacyScreen(null, "3 months", {}, {}, {}) }
