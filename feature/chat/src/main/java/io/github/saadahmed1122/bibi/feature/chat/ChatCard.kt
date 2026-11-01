package io.github.saadahmed1122.bibi.feature.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.saadahmed1122.bibi.core.designsystem.BibiMood
import io.github.saadahmed1122.bibi.core.designsystem.BibiTheme
import io.github.saadahmed1122.bibi.core.designsystem.Elevation
import io.github.saadahmed1122.bibi.core.designsystem.Space
import io.github.saadahmed1122.bibi.core.designsystem.R as DsR

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

/** The compact chat shown over other apps on a long-press of floating Bibi or "open chat" (SPEC-overlay.md, #118). Same state as [ChatScreen]. */
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
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.background,
        shadowElevation = Elevation.card,
    ) {
        Column(Modifier.padding(horizontal = Space.m)) {
            ChatHeader(state, mood, onIntent) {
                // FR-076: one tap in the card turns Safe mode on or off.
                TextButton(onClick = { onIntent(ChatIntent.ToggleSafeMode) }) {
                    Text(stringResource(if (state.safeMode) R.string.chat_safe_mode_on else R.string.chat_safe_mode_off))
                }
                IconButton(onClick = onClose) {
                    Icon(painterResource(DsR.drawable.ic_close), contentDescription = stringResource(R.string.chat_close))
                }
            }
            ChatBody(state, onIntent, onOpenModel, Modifier.weight(1f), onReported = onClose)
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

@Preview(heightDp = 420, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatCardDarkPreview() {
    BibiTheme { ChatCard(ChatUiState(messages = previewMessages(), banner = null, voiceReady = true), {}, {}, {}) }
}

@Preview(heightDp = 520, fontScale = 2f)
@Composable
private fun ChatCardLargeFontPreview() {
    BibiTheme { ChatCard(ChatUiState(messages = previewMessages(), banner = null, safeMode = true), {}, {}, {}) }
}
