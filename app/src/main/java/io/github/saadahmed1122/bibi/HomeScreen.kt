package io.github.saadahmed1122.bibi

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.saadahmed1122.bibi.core.designsystem.BibiTheme

/** [onOpenCrashLog] and [onOpenLlmDebug] are null in release builds, which hides the debug links. */
@Composable
fun HomeScreen(
    onOpenChat: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenCrashLog: (() -> Unit)?,
    onOpenLlmDebug: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(Modifier.padding(padding).padding(24.dp)) {
            Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.home_body), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onOpenChat) { Text(stringResource(R.string.home_chat)) }
            TextButton(onClick = onOpenTerms) { Text(stringResource(R.string.terms_link)) }
            if (onOpenCrashLog != null) {
                TextButton(onClick = onOpenCrashLog) { Text(stringResource(R.string.crash_log_title)) }
            }
            if (onOpenLlmDebug != null) {
                TextButton(onClick = onOpenLlmDebug) { Text(stringResource(R.string.llm_debug_title)) }
            }
        }
    }
}

@Preview
@Composable
private fun HomeScreenDebugPreview() {
    BibiTheme { HomeScreen(onOpenChat = {}, onOpenTerms = {}, onOpenCrashLog = {}, onOpenLlmDebug = {}) }
}

@Preview
@Composable
private fun HomeScreenReleasePreview() {
    BibiTheme { HomeScreen(onOpenChat = {}, onOpenTerms = {}, onOpenCrashLog = null, onOpenLlmDebug = null) }
}
