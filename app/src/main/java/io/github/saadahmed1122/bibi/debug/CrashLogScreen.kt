package io.github.saadahmed1122.bibi.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.saadahmed1122.bibi.R
import io.github.saadahmed1122.bibi.core.common.crash.CrashLogWriter
import io.github.saadahmed1122.bibi.core.designsystem.BibiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Debug-only: loads the log off the main thread and wires the buttons. */
@Composable
fun CrashLogRoute(writer: CrashLogWriter, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var log by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(writer) { log = withContext(Dispatchers.IO) { writer.read() } }
    CrashLogScreen(
        log = log,
        onCrashNow = { throw RuntimeException("Crash now (debug test crash)") },
        onClear = {
            scope.launch {
                withContext(Dispatchers.IO) { writer.clear() }
                log = ""
            }
        },
        onBack = onBack,
        modifier = modifier,
    )
}

/** [log] is null while loading. The text is selectable so it can be copied. */
@Composable
fun CrashLogScreen(
    log: String?,
    onCrashNow: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.crash_log_title), style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCrashNow) { Text(stringResource(R.string.crash_log_crash_now)) }
                OutlinedButton(onClick = onClear) { Text(stringResource(R.string.crash_log_clear)) }
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            }
            val text = when {
                log == null -> stringResource(R.string.loading)
                log.isEmpty() -> stringResource(R.string.crash_log_empty)
                else -> log
            }
            SelectionContainer(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Preview
@Composable
private fun CrashLogScreenWithLogPreview() {
    BibiTheme {
        CrashLogScreen(
            log = "=== 2026-10-31T10:15:30Z | process=io.github.saadahmed1122.bibi | thread=main | version=0.1.0-p1\n" +
                "java.lang.RuntimeException: Crash now\n\tat io.github.saadahmed1122.bibi.debug.CrashLogRoute",
            onCrashNow = {},
            onClear = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun CrashLogScreenEmptyPreview() {
    BibiTheme { CrashLogScreen(log = "", onCrashNow = {}, onClear = {}, onBack = {}) }
}

@Preview
@Composable
private fun CrashLogScreenLoadingPreview() {
    BibiTheme { CrashLogScreen(log = null, onCrashNow = {}, onClear = {}, onBack = {}) }
}
