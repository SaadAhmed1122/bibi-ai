package io.github.saadahmed1122.bibi.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.saadahmed1122.bibi.core.designsystem.BibiTheme
import io.github.saadahmed1122.bibi.domain.model.Message

@Composable
fun ChatRoute(viewModel: ChatViewModel, onOpenModel: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ChatScreen(state = state, onIntent = viewModel::onIntent, onOpenModel = onOpenModel, onBack = onBack, modifier = modifier)
}

@Composable
fun ChatScreen(
    state: ChatUiState,
    onIntent: (ChatIntent) -> Unit,
    onOpenModel: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(Modifier.padding(padding).imePadding().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.chat_back)) }
                Text(
                    stringResource(R.string.chat_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onIntent(ChatIntent.Clear) }, enabled = state.canClear) {
                    Text(stringResource(R.string.chat_clear))
                }
            }
            MessageList(state, Modifier.weight(1f))
            state.banner?.let { Banner(it, onOpenModel) }
            state.pendingAction?.let { ConfirmAction(it, onIntent) }
            InputRow(state, onIntent)
        }
    }
}

/** The confirmation card of a Sensitive action (SPEC-tools.md): the exact target, then Confirm or Cancel. */
@Composable
internal fun ConfirmAction(summary: String, onIntent: (ChatIntent) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(summary, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onIntent(ChatIntent.ConfirmAction) }) { Text(stringResource(R.string.chat_action_confirm)) }
                OutlinedButton(onClick = { onIntent(ChatIntent.CancelAction) }) { Text(stringResource(R.string.chat_action_cancel)) }
            }
        }
    }
}

@Composable
internal fun MessageList(state: ChatUiState, modifier: Modifier = Modifier) {
    if (state.messages.isEmpty() && !state.generating) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.chat_empty), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    val listState = rememberLazyListState()
    val count = state.messages.size + if (state.generating) 1 else 0
    // Follow the newest message, including each streamed token.
    LaunchedEffect(count, state.streamingText) { listState.scrollToItem(count - 1) }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.messages, key = { it.id }) { Bubble(it.role, it.text, it.status) }
        if (state.streamingText != null) {
            item(key = "streaming") {
                Bubble(
                    role = Message.Role.BIBI,
                    text = state.streamingText.ifEmpty { stringResource(R.string.chat_thinking) },
                    status = Message.Status.DONE,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

@Composable
private fun Bubble(role: Message.Role, text: String, status: Message.Status, modifier: Modifier = Modifier) {
    val mine = role == Message.Role.USER
    val colors = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .background(if (mine) colors.primaryContainer else colors.surfaceVariant, RoundedCornerShape(16.dp))
                .padding(12.dp)
                .semantics(mergeDescendants = true) {},
        ) {
            Text(
                stringResource(if (mine) R.string.chat_you else R.string.chat_bibi),
                style = MaterialTheme.typography.labelMedium,
            )
            if (text.isNotEmpty()) Text(text, style = MaterialTheme.typography.bodyLarge)
            when (status) {
                Message.Status.STOPPED -> Text(stringResource(R.string.chat_stopped), style = MaterialTheme.typography.labelSmall)
                Message.Status.ERROR -> Text(
                    stringResource(R.string.chat_error),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.error,
                )
                Message.Status.DONE -> Unit
            }
        }
    }
}

@Composable
internal fun Banner(banner: ChatBanner, onOpenModel: () -> Unit) {
    val text = when (banner) {
        ChatBanner.ImportModel -> R.string.chat_banner_import
        ChatBanner.Loading -> R.string.chat_banner_loading
        ChatBanner.Restarting -> R.string.chat_banner_restarting
        ChatBanner.Error -> R.string.chat_banner_error
    }
    Surface(
        color = if (banner == ChatBanner.Error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
            if (banner == ChatBanner.ImportModel) {
                TextButton(onClick = onOpenModel) { Text(stringResource(R.string.model_choose)) }
            }
        }
    }
}

@Composable
internal fun InputRow(state: ChatUiState, onIntent: (ChatIntent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = state.input,
            onValueChange = { onIntent(ChatIntent.Edit(it)) },
            label = { Text(stringResource(R.string.chat_input_label)) },
            supportingText = if (state.input.length > MAX_INPUT_CHARS - 200) {
                { Text(stringResource(R.string.chat_input_count, state.input.length, MAX_INPUT_CHARS)) }
            } else {
                null
            },
            maxLines = 5,
            // The keyboard's own Send key: in landscape the vivo's floating keyboard covers the Send button.
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onIntent(ChatIntent.Send) }),
            modifier = Modifier.weight(1f),
        )
        if (state.generating) {
            OutlinedButton(onClick = { onIntent(ChatIntent.Stop) }) { Text(stringResource(R.string.chat_stop)) }
        } else {
            Button(onClick = { onIntent(ChatIntent.Send) }, enabled = state.canSend) {
                Text(stringResource(R.string.chat_send))
            }
        }
    }
}

internal fun previewMessages(lastStatus: Message.Status = Message.Status.DONE) = listOf(
    Message(1, Message.Role.USER, "Hi Bibi! How are you?", 0, Message.Status.DONE),
    Message(2, Message.Role.BIBI, "Hi! I'm doing well. What's on your mind today?", 0, Message.Status.DONE),
    Message(3, Message.Role.USER, "Tell me a long story about a cat.", 0, Message.Status.DONE),
    Message(4, Message.Role.BIBI, "Once upon a time, a small orange cat", 0, lastStatus),
)

@Composable
private fun ChatPreview(state: ChatUiState) {
    BibiTheme { ChatScreen(state = state, onIntent = {}, onOpenModel = {}, onBack = {}) }
}

@Preview
@Composable
private fun ChatEmptyPreview() = ChatPreview(ChatUiState(banner = null))

@Preview
@Composable
private fun ChatConversationPreview() = ChatPreview(ChatUiState(messages = previewMessages(), input = "Thanks!", banner = null))

@Preview
@Composable
private fun ChatGeneratingPreview() = ChatPreview(
    ChatUiState(messages = previewMessages().take(3), streamingText = "Once upon a time,", banner = null),
)

@Preview
@Composable
private fun ChatStoppedPreview() = ChatPreview(ChatUiState(messages = previewMessages(Message.Status.STOPPED), banner = null))

@Preview
@Composable
private fun ChatErrorPreview() = ChatPreview(ChatUiState(messages = previewMessages(Message.Status.ERROR), banner = null))

@Preview
@Composable
private fun ChatModelMissingPreview() = ChatPreview(ChatUiState(banner = ChatBanner.ImportModel))

@Preview
@Composable
private fun ChatConfirmActionPreview() = ChatPreview(
    ChatUiState(
        messages = previewMessages(),
        banner = null,
        pendingAction = "Open the dialer with 0123456789 prefilled? Nothing is called until you press Call in the dialer.",
    ),
)

@Preview
@Composable
private fun ChatRestartingPreview() = ChatPreview(ChatUiState(messages = previewMessages(Message.Status.STOPPED), banner = ChatBanner.Restarting))
