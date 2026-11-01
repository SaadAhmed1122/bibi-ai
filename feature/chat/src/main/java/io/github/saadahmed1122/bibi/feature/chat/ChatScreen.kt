package io.github.saadahmed1122.bibi.feature.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.saadahmed1122.bibi.core.designsystem.Banner
import io.github.saadahmed1122.bibi.core.designsystem.BannerTone
import io.github.saadahmed1122.bibi.core.designsystem.BibiAvatar
import io.github.saadahmed1122.bibi.core.designsystem.BibiMood
import io.github.saadahmed1122.bibi.core.designsystem.BibiTheme
import io.github.saadahmed1122.bibi.core.designsystem.BubbleShapes
import io.github.saadahmed1122.bibi.core.designsystem.EmptyState
import io.github.saadahmed1122.bibi.core.designsystem.LocalBibiAccents
import io.github.saadahmed1122.bibi.core.designsystem.PrimaryButton
import io.github.saadahmed1122.bibi.core.designsystem.SecondaryButton
import io.github.saadahmed1122.bibi.core.designsystem.Space
import io.github.saadahmed1122.bibi.domain.model.Message
import io.github.saadahmed1122.bibi.core.designsystem.R as DsR

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


/** The in-app chat, for typing (a tab, so [onBack] is only the system Back). Same state as [ChatCard]. */
@Composable
fun ChatScreen(
    state: ChatUiState,
    onIntent: (ChatIntent) -> Unit,
    onOpenModel: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit,
    modifier: Modifier = Modifier,
    mood: BibiMood = BibiMood.IDLE,
) {
    Scaffold(modifier = modifier.fillMaxSize(), containerColor = MaterialTheme.colorScheme.background) { padding ->
        // Consume the Scaffold's insets so imePadding doesn't add the navigation bar a second time.
        Box(Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = Space.readable).padding(horizontal = Space.l)) {
                ChatHeader(state, mood, onIntent) {
                    TextButton(onClick = { onIntent(ChatIntent.Clear) }, enabled = state.canClear) {
                        Text(stringResource(R.string.chat_clear))
                    }
                }
                ChatBody(state, onIntent, onOpenModel, Modifier.weight(1f))
            }
        }
    }
}

/** Messages, banners, the confirmation card and the composer: shared by the chat screen and the card. */
@Composable
internal fun ChatBody(state: ChatUiState, onIntent: (ChatIntent) -> Unit, onOpenModel: () -> Unit, modifier: Modifier = Modifier, onReported: () -> Unit = {}) {
    Column(modifier) {
        MessageList(state, Modifier.weight(1f), onIntent, onReported)
        Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
            state.banner?.let { Banner(it, onOpenModel) }
            state.pendingAction?.let { ConfirmAction(it, onIntent) }
            state.notice?.let { Notice(it) }
            Composer(state, onIntent)
        }
    }
}

/** Bibi's face and what it's doing, the Read aloud toggle when voice is ready, then [actions]. */
@Composable
internal fun ChatHeader(state: ChatUiState, mood: BibiMood, onIntent: (ChatIntent) -> Unit, actions: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = Space.s), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
        BibiAvatar(mood, size = Space.avatarSmall + Space.s)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.chat_bibi), style = MaterialTheme.typography.titleLarge, maxLines = 1)
            Text(
                stringResource(if (state.safeMode) R.string.chat_safe_mode_label else R.string.chat_status),
                style = MaterialTheme.typography.labelMedium,
                color = if (state.safeMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (state.voiceReady) {
            val label = stringResource(if (state.speakReplies) R.string.voice_read_aloud_on else R.string.voice_read_aloud_off)
            IconToggleButton(
                checked = state.speakReplies,
                onCheckedChange = { onIntent(ChatIntent.ToggleSpeaker) },
                modifier = Modifier.semantics { contentDescription = label },
            ) {
                Icon(
                    painterResource(DsR.drawable.ic_volume),
                    contentDescription = null,
                    tint = if (state.speakReplies) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
        actions()
    }
}

/**
 * One pill: the text field, then the round button that fits the moment: Stop while Bibi writes,
 * hold-to-talk while the box is empty and voice is ready, otherwise Send.
 */
@Composable
internal fun Composer(state: ChatUiState, onIntent: (ChatIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surfaceContainerLowest, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth().padding(bottom = Space.s)) {
        Row(Modifier.padding(start = Space.xs, end = Space.xs + Space.xs / 2, top = Space.xs, bottom = Space.xs), verticalAlignment = Alignment.Bottom) {
            TextField(
                value = state.input,
                onValueChange = { onIntent(ChatIntent.Edit(it)) },
                placeholder = { Text(stringResource(if (state.listening) R.string.voice_listening else R.string.chat_input_label)) },
                supportingText = if (state.input.length > MAX_INPUT_CHARS - 200) {
                    { Text(stringResource(R.string.chat_input_count, state.input.length, MAX_INPUT_CHARS)) }
                } else {
                    null
                },
                maxLines = 5,
                textStyle = MaterialTheme.typography.bodyLarge,
                // The keyboard's own Send key: in landscape the vivo's floating keyboard covers the Send button.
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onIntent(ChatIntent.Send) }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f),
            )
            when {
                state.generating -> RoundButton(DsR.drawable.ic_stop, stringResource(R.string.chat_stop), onClick = { onIntent(ChatIntent.Stop) })
                state.input.isBlank() && state.voiceReady -> TalkButton(state, onIntent)
                else -> RoundButton(DsR.drawable.ic_send, stringResource(R.string.chat_send), enabled = state.canSend, onClick = { onIntent(ChatIntent.Send) })
            }
        }
    }
}

@Composable
private fun RoundButton(icon: Int, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    FilledIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(Space.touch)) {
        Icon(painterResource(icon), contentDescription = label)
    }
}

/** Push-to-talk: hold to record, release to send what Bibi heard (SPEC-voice.md). */
@Composable
internal fun TalkButton(state: ChatUiState, onIntent: (ChatIntent) -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(if (state.listening) R.string.voice_listening else R.string.voice_hold_to_talk)
    val colors = MaterialTheme.colorScheme
    val accents = LocalBibiAccents.current
    Surface(
        color = if (state.listening) accents.listening else colors.primary,
        contentColor = colors.onPrimary,
        shape = CircleShape,
        modifier = modifier
            .size(Space.touch)
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
        Box(contentAlignment = Alignment.Center) { Icon(painterResource(DsR.drawable.ic_mic), contentDescription = null) }
    }
}

/** A short message that goes away by itself (ChatUiState.notice). */
@Composable
internal fun Notice(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.s).semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** The confirmation card of a Sensitive action (SPEC-tools.md): the exact target, then Confirm or Cancel. */
@Composable
internal fun ConfirmAction(summary: String, onIntent: (ChatIntent) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            Text(summary, style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                PrimaryButton(stringResource(R.string.chat_action_confirm), onClick = { onIntent(ChatIntent.ConfirmAction) })
                SecondaryButton(stringResource(R.string.chat_action_cancel), onClick = { onIntent(ChatIntent.CancelAction) })
            }
        }
    }
}

/** [onReported] runs after the share sheet opens: the floating card closes so the sheet isn't hidden under it. */
@Composable
internal fun MessageList(
    state: ChatUiState,
    modifier: Modifier = Modifier,
    onIntent: ((ChatIntent) -> Unit)? = null,
    onReported: () -> Unit = {},
) {
    if (state.messages.isEmpty() && !state.generating) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            EmptyState(stringResource(R.string.chat_empty))
        }
        return
    }
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val count = state.messages.size + if (state.generating) 1 else 0
    // Follow the newest message, including each streamed token.
    LaunchedEffect(count, state.streamingText) { listState.scrollToItem(count - 1) }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(Space.s),
        contentPadding = PaddingValues(vertical = Space.m),
    ) {
        items(state.messages, key = { it.id }) { message ->
            Bubble(
                message.role,
                message.text,
                message.status,
                onRemember = onIntent?.let { send -> { send(ChatIntent.RememberMessage(message.text)) } },
                onReport = if (message.role == Message.Role.BIBI) ({
                    context.startActivity(reportIntent(context, message.text))
                    onReported()
                }) else null,
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

/**
 * One message. Yours on the right in violet, Bibi's on the left on white; no name tags, the side
 * and colour say who (TalkBack hears "You:" or "Bibi:" first). Long-press for Remember / Report.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(
    role: Message.Role,
    text: String,
    status: Message.Status,
    modifier: Modifier = Modifier,
    onRemember: (() -> Unit)? = null,
    onReport: (() -> Unit)? = null,
) {
    val mine = role == Message.Role.USER
    val colors = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    val rememberLabel = stringResource(R.string.chat_remember)
    val hasMenu = (onRemember != null || onReport != null) && text.isNotBlank()
    val who = stringResource(if (mine) R.string.chat_you else R.string.chat_bibi)
    val shape = if (mine) BubbleShapes.mine else BubbleShapes.bibi
    Box(modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier
                .widthIn(max = BubbleShapes.maxWidth)
                .clip(shape)
                .then(
                    if (hasMenu) {
                        Modifier.combinedClickable(onClick = {}, onLongClick = { menu = true }, onLongClickLabel = rememberLabel)
                    } else {
                        Modifier
                    },
                )
                .background(if (mine) colors.secondary else colors.surfaceContainerLowest)
                .padding(horizontal = Space.l, vertical = Space.m)
                .semantics(mergeDescendants = true) { contentDescription = "$who: $text" },
        ) {
            if (text.isNotEmpty()) {
                Text(text, style = MaterialTheme.typography.bodyLarge, color = if (mine) colors.onSecondary else colors.onSurface)
            }
            when (status) {
                Message.Status.STOPPED -> Text(stringResource(R.string.chat_stopped), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Message.Status.ERROR -> Text(
                    stringResource(R.string.chat_error),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.error,
                )
                Message.Status.DONE -> Unit
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                onRemember?.let { DropdownMenuItem(text = { Text(rememberLabel) }, onClick = { menu = false; it() }) }
                onReport?.let { DropdownMenuItem(text = { Text(stringResource(R.string.chat_report)) }, onClick = { menu = false; it() }) }
            }
        }
    }
}

/**
 * "Report this reply" (SPEC-privacy.md): the share sheet with the reply and a short template; the
 * app sends nothing itself. NEW_TASK because the floating card has no activity behind it.
 */
private fun reportIntent(context: Context, reply: String): Intent {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.chat_report_subject))
        .putExtra(Intent.EXTRA_TEXT, context.getString(R.string.chat_report_template, reply))
    return Intent.createChooser(send, context.getString(R.string.chat_report)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

@Composable
internal fun Banner(banner: ChatBanner, onOpenModel: () -> Unit) {
    val text = when (banner) {
        ChatBanner.ImportModel -> R.string.chat_banner_import
        ChatBanner.Loading -> R.string.chat_banner_loading
        ChatBanner.Restarting -> R.string.chat_banner_restarting
        ChatBanner.Error -> R.string.chat_banner_error
    }
    Banner(
        stringResource(text),
        tone = if (banner == ChatBanner.Error) BannerTone.Error else BannerTone.Info,
        action = if (banner == ChatBanner.ImportModel) ({ SecondaryButton(stringResource(R.string.model_choose), onClick = onOpenModel) }) else null,
    )
}

internal fun previewMessages(lastStatus: Message.Status = Message.Status.DONE) = listOf(
    Message(1, Message.Role.USER, "Hi Bibi! How are you?", 0, Message.Status.DONE),
    Message(2, Message.Role.BIBI, "Hi! I'm doing well. What's on your mind today?", 0, Message.Status.DONE),
    Message(3, Message.Role.USER, "Tell me a long story about a cat.", 0, Message.Status.DONE),
    Message(4, Message.Role.BIBI, "Once upon a time, a small orange cat", 0, lastStatus),
)

@Composable
private fun ChatPreview(state: ChatUiState, dark: Boolean = false) {
    BibiTheme(darkTheme = dark) { ChatScreen(state = state, onIntent = {}, onOpenModel = {}, onBack = {}) }
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

@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatDarkVoicePreview() = ChatPreview(ChatUiState(messages = previewMessages(), banner = null, voiceReady = true, speakReplies = true), dark = true)

@Preview(fontScale = 2f)
@Composable
private fun ChatLargeFontPreview() = ChatPreview(ChatUiState(messages = previewMessages(), banner = null, voiceReady = true, safeMode = true))
