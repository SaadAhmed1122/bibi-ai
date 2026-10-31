package io.github.saadahmed1122.bibi.debug

import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.saadahmed1122.bibi.R
import io.github.saadahmed1122.bibi.ai.inference.api.EngineState
import io.github.saadahmed1122.bibi.ai.inference.api.ReplyEvent
import io.github.saadahmed1122.bibi.ai.inference.litert.MessengerInferenceClient
import io.github.saadahmed1122.bibi.core.designsystem.BibiTheme
import kotlinx.coroutines.launch
import java.util.Locale

private const val TIMING_TAG = "BibiTTFT"

/**
 * Debug-only LLM check (Task 5, #36): binds `:inference`, streams one reply at a
 * time, and logs S2-style timing per turn to logcat tag `BibiTTFT`.
 */
@Composable
fun LlmDebugRoute(client: MessengerInferenceClient, onBack: () -> Unit, modifier: Modifier = Modifier) {
    // Connect only: the chat ViewModel shares this client and owns the disconnect.
    LaunchedEffect(client) { client.connect() }
    val state by client.state.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    var reply by remember { mutableStateOf("") }
    var outcome by remember { mutableStateOf("") }
    var stats by remember { mutableStateOf("") }
    var generating by remember { mutableStateOf(false) }
    var turn by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LlmDebugScreen(
        state = state,
        input = input,
        onInputChange = { input = it },
        reply = reply,
        outcome = outcome,
        stats = stats,
        generating = generating,
        onSend = {
            val text = input.trim()
            if (text.isNotEmpty()) {
                input = ""
                reply = ""
                outcome = ""
                generating = true
                val n = ++turn
                scope.launch {
                    val timer = ReplyTimer(SystemClock.elapsedRealtimeNanos())
                    client.send(text).collect { event ->
                        when (event) {
                            is ReplyEvent.Token -> {
                                timer.token(SystemClock.elapsedRealtimeNanos())
                                reply += event.text
                            }
                            is ReplyEvent.Done -> outcome = "done"
                            is ReplyEvent.Stopped -> outcome = "stopped"
                            is ReplyEvent.Error -> outcome = "error: ${event.reason}"
                        }
                    }
                    generating = false
                    stats = "TTFT ${fmt(timer.ttftMs(), 0)} ms, ${fmt(timer.tokensPerSecond(), 1)} tok/s, ${timer.tokens} tokens"
                    Log.i(
                        TIMING_TAG,
                        "turn=$n ttft_ms=${fmt(timer.ttftMs(), 1)} tokens=${timer.tokens} " +
                            "tok_s=${fmt(timer.tokensPerSecond(), 2)} end=$outcome",
                    )
                }
            }
        },
        onStop = client::stop,
        onBack = onBack,
        modifier = modifier,
    )
}

private fun fmt(value: Double?, digits: Int): String = value?.let { "%.${digits}f".format(Locale.US, it) } ?: "-"

/** [outcome] and [stats] describe the last reply; empty before the first one. */
@Composable
fun LlmDebugScreen(
    state: EngineState,
    input: String,
    onInputChange: (String) -> Unit,
    reply: String,
    outcome: String,
    stats: String,
    generating: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.llm_debug_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.llm_debug_state, stateLabel(state)))
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                label = { Text(stringResource(R.string.llm_debug_message)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSend, enabled = state == EngineState.Ready && !generating) {
                    Text(stringResource(R.string.llm_debug_send))
                }
                OutlinedButton(onClick = onStop, enabled = generating) { Text(stringResource(R.string.llm_debug_stop)) }
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            }
            if (outcome.isNotEmpty()) Text(outcome, style = MaterialTheme.typography.labelLarge)
            if (stats.isNotEmpty()) Text(stats, style = MaterialTheme.typography.labelLarge)
            Text(reply, Modifier.weight(1f).verticalScroll(rememberScrollState()))
        }
    }
}

private fun stateLabel(state: EngineState): String = when (state) {
    EngineState.Unbound -> "unbound"
    EngineState.Loading -> "loading"
    EngineState.Ready -> "ready"
    EngineState.Generating -> "generating"
    is EngineState.Failed -> "failed: ${state.reason}"
}

@Preview
@Composable
private fun LlmDebugLoadingPreview() {
    BibiTheme {
        LlmDebugScreen(EngineState.Loading, "", {}, "", "", "", false, {}, {}, {})
    }
}

@Preview
@Composable
private fun LlmDebugGeneratingPreview() {
    BibiTheme {
        LlmDebugScreen(EngineState.Generating, "", {}, "Hi! I'm doing well,", "", "", true, {}, {}, {})
    }
}

@Preview
@Composable
private fun LlmDebugDonePreview() {
    BibiTheme {
        LlmDebugScreen(
            EngineState.Ready, "", {}, "Hi! I'm doing well, thanks for asking.", "done",
            "TTFT 1230 ms, 9.8 tok/s, 10 tokens", false, {}, {}, {},
        )
    }
}

@Preview
@Composable
private fun LlmDebugFailedPreview() {
    BibiTheme {
        LlmDebugScreen(EngineState.Failed("model file not found"), "", {}, "", "", "", false, {}, {}, {})
    }
}
