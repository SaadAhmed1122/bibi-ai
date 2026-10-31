package io.github.saadahmed1122.bibi

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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

/** [onOpenCrashLog] is null in release builds, which hides the debug link. */
@Composable
fun HomeScreen(onOpenTerms: () -> Unit, onOpenCrashLog: (() -> Unit)?, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(Modifier.padding(padding).padding(24.dp)) {
            Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.home_body), style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = onOpenTerms) { Text(stringResource(R.string.terms_link)) }
            if (onOpenCrashLog != null) {
                TextButton(onClick = onOpenCrashLog) { Text(stringResource(R.string.crash_log_title)) }
            }
        }
    }
}

@Preview
@Composable
private fun HomeScreenDebugPreview() {
    BibiTheme { HomeScreen(onOpenTerms = {}, onOpenCrashLog = {}) }
}

@Preview
@Composable
private fun HomeScreenReleasePreview() {
    BibiTheme { HomeScreen(onOpenTerms = {}, onOpenCrashLog = null) }
}
