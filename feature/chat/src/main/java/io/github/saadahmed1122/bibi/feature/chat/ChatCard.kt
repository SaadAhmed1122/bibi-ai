package io.github.saadahmed1122.bibi.feature.chat

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.saadahmed1122.bibi.core.designsystem.BibiTheme

@Composable
fun ChatCardRoute(viewModel: ChatViewModel, onOpenModel: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ChatCard(state, viewModel::onIntent, onOpenModel, onClose, modifier)
}

/** The compact chat shown over other apps when floating Bibi is tapped (SPEC-overlay.md). Same state as [ChatScreen]. */
@Composable
fun ChatCard(
    state: ChatUiState,
    onIntent: (ChatIntent) -> Unit,
    onOpenModel: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.chat_bibi),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                TextButton(onClick = onClose) { Text(stringResource(R.string.chat_close)) }
            }
            MessageList(state, Modifier.weight(1f))
            state.banner?.let { Banner(it, onOpenModel) }
            state.pendingAction?.let { ConfirmAction(it, onIntent) }
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
