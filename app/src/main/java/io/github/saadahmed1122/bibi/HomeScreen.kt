package io.github.saadahmed1122.bibi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.saadahmed1122.bibi.companion.overlay.FloatingState
import io.github.saadahmed1122.bibi.core.designsystem.BibiTheme

/** [onOpenCrashLog] and [onOpenLlmDebug] are null in release builds, which hides the debug links. */
@Composable
fun HomeScreen(
    onOpenChat: () -> Unit,
    onOpenModel: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenCrashLog: (() -> Unit)?,
    onOpenLlmDebug: (() -> Unit)?,
    floating: FloatingState,
    onFloatingSwitch: (Boolean) -> Unit,
    onCalmSwitch: (Boolean) -> Unit,
    onExplainerContinue: () -> Unit,
    onExplainerDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(Modifier.padding(padding).padding(24.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.home_body), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onOpenChat) { Text(stringResource(R.string.home_chat)) }
            SwitchRow(R.string.home_floating, R.string.home_floating_hint, floating.enabled, onFloatingSwitch)
            SwitchRow(R.string.home_calm, R.string.home_calm_hint, floating.calm, onCalmSwitch)
            TextButton(onClick = onOpenModel) { Text(stringResource(R.string.home_model)) }
            TextButton(onClick = onOpenTerms) { Text(stringResource(R.string.terms_link)) }
            if (onOpenCrashLog != null) {
                TextButton(onClick = onOpenCrashLog) { Text(stringResource(R.string.crash_log_title)) }
            }
            if (onOpenLlmDebug != null) {
                TextButton(onClick = onOpenLlmDebug) { Text(stringResource(R.string.llm_debug_title)) }
            }
        }
    }
    if (floating.showExplainer) {
        AlertDialog(
            onDismissRequest = onExplainerDismiss,
            title = { Text(stringResource(R.string.floating_explainer_title)) },
            text = { Text(stringResource(R.string.floating_explainer_body)) },
            confirmButton = { TextButton(onClick = onExplainerContinue) { Text(stringResource(R.string.floating_explainer_open)) } },
            dismissButton = { TextButton(onClick = onExplainerDismiss) { Text(stringResource(R.string.floating_explainer_not_now)) } },
        )
    }
}

/** A labelled switch; the whole row is one toggle for TalkBack. */
@Composable
private fun SwitchRow(title: Int, hint: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(hint), style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun HomePreview(floating: FloatingState, debug: Boolean = true) {
    BibiTheme {
        HomeScreen(
            onOpenChat = {}, onOpenModel = {}, onOpenTerms = {},
            onOpenCrashLog = if (debug) ({}) else null, onOpenLlmDebug = if (debug) ({}) else null,
            floating = floating, onFloatingSwitch = {}, onCalmSwitch = {}, onExplainerContinue = {}, onExplainerDismiss = {},
        )
    }
}

@Preview
@Composable
private fun HomeScreenDebugPreview() = HomePreview(FloatingState(enabled = true, calm = false))

@Preview
@Composable
private fun HomeScreenReleasePreview() = HomePreview(FloatingState(enabled = false, calm = false), debug = false)

@Preview
@Composable
private fun HomeScreenExplainerPreview() = HomePreview(FloatingState(enabled = false, calm = false, showExplainer = true))
