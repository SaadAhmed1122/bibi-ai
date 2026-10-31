package io.github.saadahmed1122.bibi.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.saadahmed1122.bibi.R
import io.github.saadahmed1122.bibi.core.tools.ActionRunner
import io.github.saadahmed1122.bibi.core.tools.ConfirmationRequest
import io.github.saadahmed1122.bibi.core.tools.PermissionEngine
import io.github.saadahmed1122.bibi.core.tools.RunResult
import io.github.saadahmed1122.bibi.core.tools.ToolAuditLog
import io.github.saadahmed1122.bibi.core.tools.ToolCall
import io.github.saadahmed1122.bibi.core.tools.UserResponse
import kotlinx.coroutines.delay

/** Debug-only check of the five actions (Task 4, #75): each button goes through [ActionRunner], like chat will. */
private val CALLS = listOf(
    "Open Settings" to ToolCall.OpenApp("settings"),
    "Alarm 07:30" to ToolCall.SetAlarm(7, 30),
    "Timer 10 s" to ToolCall.StartTimer(10),
    "Torch on" to ToolCall.Flashlight(true),
    "Torch off" to ToolCall.Flashlight(false),
    "Dial 0123 456 789" to ToolCall.Dial("0123 456 789"),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActionsDebugRoute(actions: ActionRunner, log: ToolAuditLog, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var result by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<ConfirmationRequest?>(null) }
    var logText by remember { mutableStateOf(log.read()) }

    fun show(r: RunResult) {
        pending = (r as? RunResult.NeedsConfirmation)?.request
        result = r.toString()
        logText = log.read()
    }

    // Silence is never consent: the card goes away when its time runs out.
    LaunchedEffect(pending) {
        if (pending != null) {
            delay(PermissionEngine.DEFAULT_TIMEOUT_MS)
            actions.expire()
            pending = null
            result = "timed out"
            logText = log.read()
        }
    }

    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.actions_debug_title), style = MaterialTheme.typography.headlineSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CALLS.forEach { (label, call) -> OutlinedButton(onClick = { show(actions.submit(call)) }) { Text(label) } }
            }
            pending?.let { req ->
                Text(req.summary, style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { show(actions.respond(req, UserResponse.CONFIRM)) }) { Text("Confirm") }
                    OutlinedButton(onClick = { show(actions.respond(req, UserResponse.CANCEL)) }) { Text("Cancel") }
                }
            }
            if (result.isNotEmpty()) Text(result, style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            Text(logText, style = MaterialTheme.typography.bodySmall)
        }
    }
}
