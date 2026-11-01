package io.github.saadahmed1122.bibi.feature.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.saadahmed1122.bibi.core.designsystem.BibiMood
import io.github.saadahmed1122.bibi.core.designsystem.BibiTheme

/** [onOpenApp] opens the app's chat: for the model import and for the first microphone permission. */
@Composable
fun ChatCardRoute(viewModel: ChatViewModel, onOpenModel: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier, onOpenApp: () -> Unit = onOpenModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mood by viewModel.mood.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // The card can't show the permission dialog (no activity): the first time, the app's chat asks.
    val onIntent: (ChatIntent) -> Unit = { intent ->
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (intent == ChatIntent.MicDown && !granted) onOpenApp() else viewModel.onIntent(intent)
    }
    ChatCard(state, onIntent, onOpenModel, onClose, modifier, mood)
}

/** The compact chat shown over other apps when floating Bibi is tapped (SPEC-overlay.md). Same state as [ChatScreen]. */
@Composable
fun ChatCard(
    state: ChatUiState,
    onIntent: (ChatIntent) -> Unit,
    onOpenModel: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    mood: BibiMood = BibiMood.IDLE,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BibiFace(mood)
                Text(
                    stringResource(R.string.chat_bibi),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                // FR-076: one tap in the card turns Safe mode on or off.
                TextButton(onClick = { onIntent(ChatIntent.ToggleSafeMode) }) {
                    Text(stringResource(if (state.safeMode) R.string.chat_safe_mode_on else R.string.chat_safe_mode_off))
                }
                TextButton(onClick = onClose) { Text(stringResource(R.string.chat_close)) }
            }
            MessageList(state, Modifier.weight(1f), onIntent, onReported = onClose)
            state.banner?.let { Banner(it, onOpenModel) }
            state.pendingAction?.let { ConfirmAction(it, onIntent) }
            state.notice?.let { Notice(it) }
            if (state.voiceReady) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TalkButton(state, onIntent, Modifier.weight(1f))
                    TextButton(onClick = { onIntent(ChatIntent.ToggleSpeaker) }) {
                        Text(stringResource(if (state.speakReplies) R.string.voice_read_aloud_on else R.string.voice_read_aloud_off))
                    }
                }
            }
            InputRow(state, onIntent)
        }
    }
}

@Preview(heightDp = 420)
@Composable
private fun ChatCardPreview() {
    BibiTheme { ChatCard(ChatUiState(messages = previewMessages(), banner = null), {}, {}, {}) }
}

@Preview(heightDp = 420)
@Composable
private fun ChatCardGeneratingPreview() {
    BibiTheme {
        ChatCard(ChatUiState(messages = previewMessages().take(3), streamingText = "Once upon a time,", banner = null), {}, {}, {})
    }
}
