package io.github.saadahmed1122.bibi.feature.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.saadahmed1122.bibi.core.designsystem.BibiMood
import io.github.saadahmed1122.bibi.core.designsystem.BibiTheme
import io.github.saadahmed1122.bibi.domain.model.Message

@Composable
fun ChatRoute(viewModel: ChatViewModel, onOpenModel: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mood by viewModel.mood.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var explainMic by remember { mutableStateOf(false) }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    // The microphone is asked for the first time the mic is pressed, after one line of why (SPEC-voice.md).
    val onIntent: (ChatIntent) -> Unit = { intent ->
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        when {
            intent == ChatIntent.MicDown && !granted -> explainMic = true
            else -> viewModel.onIntent(intent)
        }
    }
    ChatScreen(state = state, onIntent = onIntent, onOpenModel = onOpenModel, onBack = onBack, modifier = modifier, mood = mood)
    if (explainMic) {
        AlertDialog(
            onDismissRequest = { explainMic = false },
            text = { Text(stringResource(R.string.voice_mic_why)) },
            confirmButton = {
                TextButton(onClick = { explainMic = false; askMic.launch(Manifest.permission.RECORD_AUDIO) }) {
                    Text(stringResource(R.string.voice_mic_continue))
                }
            },
            dismissButton = { TextButton(onClick = { explainMic = false }) { Text(stringResource(R.string.voice_mic_not_now)) } },
        )
    }
}

/** Small Bibi in a chat header, showing [mood] (SPEC-avatar-states.md). */
@Composable
internal fun BibiFace(mood: BibiMood, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(mood.frame),
        contentDescription = stringResource(mood.description),
        modifier = modifier.size(40.dp),
    )
}

@Composable
fun ChatScreen(
    state: ChatUiState,
    onIntent: (ChatIntent) -> Unit,
    onOpenModel: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    mood: BibiMood = BibiMood.IDLE,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        // Consume the Scaffold's insets so imePadding doesn't add the navigation bar a second time.
        Column(Modifier.padding(padding).consumeWindowInsets(padding).imePadding().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.chat_back)) }
                BibiFace(mood)
                Text(
                    stringResource(R.string.chat_title),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onIntent(ChatIntent.Clear) }, enabled = state.canClear) {
                    Text(stringResource(R.string.chat_clear))
                }
            }
            MessageList(state, Modifier.weight(1f), onIntent)
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

/** Push-to-talk: hold to record, release to send what Bibi heard (SPEC-voice.md). */
@Composable
internal fun TalkButton(state: ChatUiState, onIntent: (ChatIntent) -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(if (state.listening) R.string.voice_listening else R.string.voice_hold_to_talk)
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (state.listening) colors.primary else colors.secondaryContainer,
        contentColor = if (state.listening) colors.onPrimary else colors.onSecondaryContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .padding(top = 8.dp)
            .alpha(if (state.canTalk || state.listening) 1f else 0.4f)
            .semantics { contentDescription = label }
            .pointerInput(state.canTalk) {
                detectTapGestures(onPress = {
                    if (!state.canTalk) return@detectTapGestures
                    onIntent(ChatIntent.MicDown)
                    tryAwaitRelease()
                    onIntent(ChatIntent.MicUp)
                })
            },
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(14.dp), textAlign = TextAlign.Center)
    }
}

/** A short message that goes away by itself (ChatUiState.notice). */
@Composable
internal fun Notice(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
    )
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

/** [onIntent], when given, adds "Remember this" on a long press of a message. */
@Composable
internal fun MessageList(state: ChatUiState, modifier: Modifier = Modifier, onIntent: ((ChatIntent) -> Unit)? = null) {
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
        items(state.messages, key = { it.id }) { message ->
            Bubble(
                message.role,
                message.text,
                message.status,
                onRemember = onIntent?.let { send -> { send(ChatIntent.RememberMessage(message.text)) } },
            )
        }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(
    role: Message.Role,
    text: String,
    status: Message.Status,
    modifier: Modifier = Modifier,
    onRemember: (() -> Unit)? = null,
) {
    val mine = role == Message.Role.USER
    val colors = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    val rememberLabel = stringResource(R.string.chat_remember)
    Box(modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(16.dp))
                .then(
                    if (onRemember != null && text.isNotBlank()) {
                        Modifier.combinedClickable(onClick = {}, onLongClick = { menu = true }, onLongClickLabel = rememberLabel)
                    } else {
                        Modifier
                    },
                )
                .background(if (mine) colors.primaryContainer else colors.surfaceVariant)
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
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(rememberLabel) }, onClick = { menu = false; onRemember?.invoke() })
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
