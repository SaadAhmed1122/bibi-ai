package io.github.saadahmed1122.bibi

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import io.github.saadahmed1122.bibi.core.designsystem.Banner
import io.github.saadahmed1122.bibi.core.designsystem.BibiCard
import io.github.saadahmed1122.bibi.core.designsystem.BibiScreen
import io.github.saadahmed1122.bibi.core.designsystem.ListRow
import io.github.saadahmed1122.bibi.core.designsystem.SectionHeader
import io.github.saadahmed1122.bibi.core.designsystem.Space
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
    BibiScreen(title = stringResource(R.string.privacy_title), modifier = modifier, onBack = onBack) {
        Banner(stringResource(R.string.privacy_offline))
        SectionHeader(stringResource(R.string.privacy_section_stored))
        BibiCard {
            if (stored == null) {
                ListRow(stringResource(R.string.privacy_counting))
            } else {
                ListRow(stringResource(R.string.privacy_messages, stored.messages))
                ListRow(stringResource(R.string.privacy_memories, stored.memories, retentionLabel))
                ListRow(stringResource(R.string.privacy_action_log, kb(stored.actionLogBytes)))
                ListRow(stringResource(R.string.privacy_crash_log, kb(stored.crashLogBytes)))
                ListRow(stringResource(R.string.privacy_usage, stored.usageSessions))
            }
            ListRow(stringResource(R.string.privacy_open_memory), onClick = onOpenMemory)
        }
        SectionHeader(stringResource(R.string.privacy_section_delete))
        Text(stringResource(R.string.privacy_delete_explain), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(
            onClick = { confirm = true },
            shape = CircleShape,
            modifier = Modifier.heightIn(min = Space.touch),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
        ) { Text(stringResource(R.string.privacy_delete)) }
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
    PrivacyScreen(StoredData(211, 4, 2_300, 9_100, 12), "3 months", {}, {}, {})
}

@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PrivacyDarkPreview() = BibiTheme { PrivacyScreen(StoredData(211, 4, 2_300, 9_100, 12), "3 months", {}, {}, {}) }

@Preview(fontScale = 2f)
@Composable
private fun PrivacyCountingPreview() = BibiTheme { PrivacyScreen(null, "3 months", {}, {}, {}) }
